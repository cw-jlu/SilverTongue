import { useState, useEffect, useRef } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import api from '../api/client';

// Remote Audio Component to cleanly handle media streams in React
function RemoteAudio({ stream }) {
  const audioRef = useRef();
  useEffect(() => {
    if (audioRef.current && stream) {
      audioRef.current.srcObject = stream;
    }
  }, [stream]);
  return <audio ref={audioRef} autoPlay playsInline style={{ display: 'none' }} />;
}

export default function MeetingRoom() {
  const { id: roomId } = useParams();
  const navigate = useNavigate();

  const [room, setRoom] = useState(null);
  const [participants, setParticipants] = useState([]);
  const [userId, setUserId] = useState(null);
  const [currentUserInfo, setCurrentUserInfo] = useState(null);
  const [friends, setFriends] = useState([]);
  
  // Modals state
  const [showInvite, setShowInvite] = useState(false);
  const [showAddAi, setShowAddAi] = useState(false);
  const [showEditAi, setShowEditAi] = useState(false);
  
  // Form input state
  const [aiName, setAiName] = useState('Bella');
  const [aiSetting, setAiSetting] = useState('You are a friendly IELTS speaking examiner.');
  const [editingParticipant, setEditingParticipant] = useState(null);

  // WebRTC & Connection State
  const [connectionStatus, setConnectionStatus] = useState('disconnected'); // disconnected, connecting, connected
  const [localStream, setLocalStream] = useState(null);
  const [remoteStreams, setRemoteStreams] = useState({}); // targetUserId -> MediaStream
  const [speakingUsers, setSpeakingUsers] = useState({}); // targetUserId -> boolean

  // Refs for tracking mutable connections without re-renders
  const wsRef = useRef(null);
  const pcsRef = useRef({}); // targetUserId -> RTCPeerConnection
  const localStreamRef = useRef(null);

  // New Chat, TTS & STT States
  const [messages, setMessages] = useState([]);
  const [inputText, setInputText] = useState('');
  const [autoPlay, setAutoPlay] = useState(true);
  const [ttsVoice, setTtsVoice] = useState('female_us');
  const [ttsLoading, setTtsLoading] = useState(false);
  const [currentPlayId, setCurrentPlayId] = useState(null);
  const [recordState, setRecordState] = useState('idle'); // idle, recording, processing, error
  const [aiRoles, setAiRoles] = useState([]);
  const [aiRolesLoading, setAiRolesLoading] = useState(false);
  const [aiRolesError, setAiRolesError] = useState('');
  const [aiTabActive, setAiTabActive] = useState(0); // 0: preset library, 1: custom

  const autoPlayRef = useRef(autoPlay);
  useEffect(() => {
    autoPlayRef.current = autoPlay;
  }, [autoPlay]);

  const msgContainerRef = useRef(null);
  useEffect(() => {
    if (msgContainerRef.current) {
      msgContainerRef.current.scrollTop = msgContainerRef.current.scrollHeight;
    }
  }, [messages]);

  const audioRef = useRef(null);
  const mediaRecorderRef = useRef(null);
  const audioChunksRef = useRef([]);

  // TTS Speech Player
  const playTts = async (text, msgId = null) => {
    if (ttsLoading) return;
    if (audioRef.current) {
      audioRef.current.pause();
      audioRef.current = null;
    }
    setTtsLoading(true);
    setCurrentPlayId(msgId);
    try {
      const res = await api.post('/ai/tts/speak', { text, voice: ttsVoice }, { responseType: 'blob' });
      const blob = res instanceof Blob ? res : new Blob([res], { type: 'audio/mpeg' });
      const url = URL.createObjectURL(blob);
      const audio = new Audio(url);
      audioRef.current = audio;
      audio.onended = () => {
        setTtsLoading(false);
        setCurrentPlayId(null);
      };
      audio.onerror = () => {
        setTtsLoading(false);
        setCurrentPlayId(null);
      };
      await audio.play();
    } catch (e) {
      console.error('TTS synthesis failed, falling back to browser API', e);
      setTtsLoading(false);
      setCurrentPlayId(null);
      try {
        const utter = new SpeechSynthesisUtterance(text);
        utter.lang = ttsVoice.includes('uk') ? 'en-GB' : 'en-US';
        window.speechSynthesis.speak(utter);
      } catch (err) {
        console.error('Browser TTS fallback failed', err);
      }
    }
  };

  const playTtsRef = useRef(null);
  useEffect(() => {
    playTtsRef.current = playTts;
  }, [ttsVoice, ttsLoading, currentPlayId]);

  // Audio recording handlers
  const toggleRecording = async () => {
    if (recordState === 'recording') {
      stopRecording();
    } else {
      await startRecording();
    }
  };

  const startRecording = async () => {
    try {
      const stream = await navigator.mediaDevices.getUserMedia({
        audio: { sampleRate: 16000, channelCount: 1, echoCancellation: true, noiseSuppression: true }
      });
      audioChunksRef.current = [];
      const mediaRecorder = new MediaRecorder(stream, { mimeType: 'audio/webm;codecs=opus' });
      mediaRecorderRef.current = mediaRecorder;
      mediaRecorder.ondataavailable = e => {
        if (e.data.size > 0) audioChunksRef.current.push(e.data);
      };
      mediaRecorder.onstop = handleRecordStop;
      mediaRecorder.start(100);
      setRecordState('recording');
    } catch (e) {
      console.error('Failed to start recording:', e);
      setRecordState('error');
      setTimeout(() => setRecordState('idle'), 3000);
    }
  };

  const stopRecording = () => {
    if (mediaRecorderRef.current && recordState === 'recording') {
      mediaRecorderRef.current.stop();
      mediaRecorderRef.current.stream.getTracks().forEach(t => t.stop());
      setRecordState('processing');
    }
  };

  const handleRecordStop = async () => {
    try {
      const audioBlob = new Blob(audioChunksRef.current, { type: 'audio/webm' });
      const reader = new FileReader();
      reader.readAsDataURL(audioBlob);
      reader.onloadend = async () => {
        const base64data = reader.result.split(',')[1];
        try {
          const res = await api.post('/ai/stt/transcribe', { audioBase64: base64data });
          setRecordState('idle');
          if (res?.text) {
            sendChatMessage(res.text);
          } else {
            alert('未能识别出语音内容，请重新尝试。');
          }
        } catch (err) {
          console.error('STT transcribing request error:', err);
          setRecordState('error');
          setTimeout(() => setRecordState('idle'), 3000);
        }
      };
    } catch (e) {
      console.error('Error handling recorded buffer:', e);
      setRecordState('error');
      setTimeout(() => setRecordState('idle'), 3000);
    }
  };

  // 1. Fetch Room Detail
  const loadRoomDetail = async () => {
    try {
      const res = await api.get(`/room/${roomId}`);
      if (res) {
        setRoom(res);
        setParticipants(res.participants || []);
      }
    } catch (error) {
      console.error('Failed to load room details:', error);
    }
  };

  // 2. Fetch Friends List
  const loadFriends = async () => {
    try {
      const res = await api.get('/friend');
      if (Array.isArray(res)) {
        setFriends(res);
      }
    } catch (error) {
      console.error('Failed to load friends:', error);
    }
  };

  // Fetch Preset AI Roles
  const loadAiRoles = async () => {
    setAiRolesLoading(true);
    setAiRolesError('');
    try {
      const res = await api.get('/room/ai-roles');
      if (Array.isArray(res) && res.length > 0) {
        setAiRoles(res);
      } else {
        setAiRolesError('预设角色加载失败，请确认 AI 服务已启动');
      }
    } catch (error) {
      console.error('Failed to fetch AI roles:', error);
      setAiRolesError('无法连接 AI 服务，请稍后重试');
    } finally {
      setAiRolesLoading(false);
    }
  };

  // 3. Invite Friend
  const inviteFriend = async (friendId) => {
    try {
      await api.post(`/room/${roomId}/invite/${friendId}`);
      alert('已成功发送邀请！');
      setShowInvite(false);
    } catch (error) {
      alert(error?.message || '邀请失败');
    }
  };

  // 4. Add AI Agent
  const addAiAgent = async () => {
    if (!aiName.trim()) return;
    try {
      await api.post(`/room/${roomId}/ai`, { aiName, aiSetting });
      setAiName('Bella');
      setAiSetting('You are a friendly IELTS speaking examiner.');
      setShowAddAi(false);
      loadRoomDetail();
    } catch (error) {
      alert(error?.message || '添加 AI 失败');
    }
  };

  // 5. Open Edit AI Modal
  const openEditAi = (p) => {
    setEditingParticipant(p);
    setAiName(p.aiRoleName || 'AI Assistant');
    setAiSetting(p.aiRoleSetting || '');
    setShowEditAi(true);
  };

  // 6. Update AI Agent
  const updateAiAgent = async () => {
    if (!editingParticipant) return;
    try {
      await api.put(`/room/${roomId}/ai/${editingParticipant.id}`, {
        aiName,
        aiSetting
      });
      setShowEditAi(false);
      setEditingParticipant(null);
      loadRoomDetail();
    } catch (error) {
      alert(error?.message || '更新 AI 失败');
    }
  };

  // 7. Remove Participant (Human or AI)
  const removeParticipant = async (participantId) => {
    if (!window.confirm('确定要移出该成员吗？')) return;
    try {
      await api.delete(`/room/${roomId}/participant/${participantId}`);
      loadRoomDetail();
    } catch (error) {
      alert(error?.message || '操作失败');
    }
  };

  // 8. Leave Room
  const leaveRoom = async () => {
    try {
      await api.post(`/room/${roomId}/leave`);
    } catch (error) {
      console.error('Error leaving room on backend:', error);
    }
    cleanupConnections();
    navigate('/meeting');
  };

  // Send message helper
  const sendChatMessage = (text) => {
    if (!text || !text.trim()) return;
    const msgText = text.trim();
    const localMsg = {
      id: Date.now(),
      from: String(userId),
      senderName: currentUserInfo?.nickname || '我',
      content: msgText
    };
    localMsg.senderName = currentUserInfo?.nickname || 'Me';
    setMessages(prev => [...prev, localMsg]);

    if (wsRef.current && wsRef.current.readyState === WebSocket.OPEN) {
      wsRef.current.send(JSON.stringify({
        type: 'chat',
        roomId: Number(roomId),
        content: msgText
      }));
    }
    setInputText('');
  };

  // 9. Clean up WebRTC and WebSockets
  const cleanupConnections = () => {
    if (wsRef.current) {
      wsRef.current.close();
      wsRef.current = null;
    }

    Object.values(pcsRef.current).forEach(pc => {
      pc.close();
    });
    pcsRef.current = {};

    if (localStreamRef.current) {
      localStreamRef.current.getTracks().forEach(track => track.stop());
      localStreamRef.current = null;
      setLocalStream(null);
    }

    setRemoteStreams({});
    setConnectionStatus('disconnected');

    if (audioRef.current) {
      audioRef.current.pause();
      audioRef.current = null;
    }
  };

  // 10. WebRTC & WebSocket Setup
  const initializeMediaAndSignaling = async (currentUid) => {
    setConnectionStatus('connecting');
    try {
      // Step A: Request microphone access
      const stream = await navigator.mediaDevices.getUserMedia({
        audio: {
          echoCancellation: true,
          noiseSuppression: true,
          autoGainControl: true
        }
      });
      localStreamRef.current = stream;
      setLocalStream(stream);

      // Step B: Connect to WebSocket signaling server
      const wsProtocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
      const wsUserId = encodeURIComponent(currentUid);
      const wsUrl = `${wsProtocol}//${window.location.host}/ws/signaling?userId=${wsUserId}`;
      const ws = new WebSocket(wsUrl);
      wsRef.current = ws;

      ws.onopen = () => {
        setConnectionStatus('connected');
        ws.send(JSON.stringify({ type: 'join', roomId: Number(roomId) }));
      };

      ws.onmessage = async (event) => {
        try {
          const message = JSON.parse(event.data);
          const { type, from, data } = message;

          if (type === 'user-joined') {
            loadRoomDetail();
            if (from && from !== String(currentUid) && Number(from) > 0) {
              await initiateWebRTCConnection(from, true);
            }
          } else if (type === 'user-left') {
            loadRoomDetail();
            closePeerConnection(from);
          } else if (type === 'offer') {
            await handleOffer(from, data);
          } else if (type === 'answer') {
            await handleAnswer(from, data);
          } else if (type === 'candidate') {
            await handleIceCandidate(from, data);
          } else if (type === 'chat') {
            const newMsg = {
              id: message.id || Date.now(),
              from: message.from,
              senderName: message.senderName || '用户',
              content: message.content || ''
            };
            newMsg.senderName = message.senderName || 'User';
            setMessages(prev => {
              if (prev.some(m => m.id === newMsg.id || (m.content === newMsg.content && m.senderName === newMsg.senderName && (Date.now() - m.id < 2000)))) {
                return prev;
              }
              return [...prev, newMsg];
            });

            if (autoPlayRef.current && Number(message.from) < 0 && message.content) {
              if (playTtsRef.current) {
                playTtsRef.current(message.content, newMsg.id);
              }
            }
          }
        } catch (err) {
          console.error('Failed to parse signaling message:', err);
        }
      };

      ws.onclose = () => {
        setConnectionStatus('disconnected');
      };

      ws.onerror = (err) => {
        console.error('Signaling WebSocket error:', err);
        setConnectionStatus('disconnected');
      };

    } catch (error) {
      console.error('Media acquisition or signaling setup failed:', error);
      alert('获取麦克风失败，请确保您已授权浏览器麦克风权限！');
      setConnectionStatus('disconnected');
    }
  };

  // 11. Initiate WebRTC connection to a target peer
  const initiateWebRTCConnection = async (targetUid, isPolite) => {
    if (pcsRef.current[targetUid]) {
      pcsRef.current[targetUid].close();
    }

    const pc = new RTCPeerConnection({
      iceServers: [{ urls: 'stun:stun.l.google.com:19302' }]
    });

    pcsRef.current[targetUid] = pc;

    if (localStreamRef.current) {
      localStreamRef.current.getTracks().forEach(track => {
        pc.addTrack(track, localStreamRef.current);
      });
    }

    pc.onicecandidate = (event) => {
      if (event.candidate && wsRef.current && wsRef.current.readyState === WebSocket.OPEN) {
        wsRef.current.send(JSON.stringify({
          type: 'candidate',
          roomId: Number(roomId),
          target: targetUid,
          data: event.candidate
        }));
      }
    };

    pc.ontrack = (event) => {
      if (event.streams && event.streams[0]) {
        setRemoteStreams(prev => ({
          ...prev,
          [targetUid]: event.streams[0]
        }));
      }
    };

    pc.onconnectionstatechange = () => {
      if (pc.connectionState === 'disconnected' || pc.connectionState === 'failed' || pc.connectionState === 'closed') {
        closePeerConnection(targetUid);
      }
    };

    if (isPolite) {
      try {
        const offer = await pc.createOffer();
        await pc.setLocalDescription(offer);
        if (wsRef.current && wsRef.current.readyState === WebSocket.OPEN) {
          wsRef.current.send(JSON.stringify({
            type: 'offer',
            roomId: Number(roomId),
            target: targetUid,
            data: offer
          }));
        }
      } catch (err) {
        console.error('Error creating offer for peer:', targetUid, err);
      }
    }

    return pc;
  };

  // 12. Handle WebRTC Offer
  const handleOffer = async (fromUid, sdp) => {
    const pc = await initiateWebRTCConnection(fromUid, false);
    try {
      await pc.setRemoteDescription(new RTCSessionDescription(sdp));
      const answer = await pc.createAnswer();
      await pc.setLocalDescription(answer);

      if (wsRef.current && wsRef.current.readyState === WebSocket.OPEN) {
        wsRef.current.send(JSON.stringify({
          type: 'answer',
          roomId: Number(roomId),
          target: fromUid,
          data: answer
        }));
      }
    } catch (err) {
      console.error('Error handling offer from peer:', fromUid, err);
    }
  };

  // 13. Handle WebRTC Answer
  const handleAnswer = async (fromUid, sdp) => {
    const pc = pcsRef.current[fromUid];
    if (pc) {
      try {
        await pc.setRemoteDescription(new RTCSessionDescription(sdp));
      } catch (err) {
        console.error('Error setting remote description from answer from peer:', fromUid, err);
      }
    }
  };

  // 14. Handle ICE Candidate
  const handleIceCandidate = async (fromUid, candidate) => {
    const pc = pcsRef.current[fromUid];
    if (pc) {
      try {
        await pc.addIceCandidate(new RTCIceCandidate(candidate));
      } catch (err) {
        console.error('Error adding ICE candidate from peer:', fromUid, err);
      }
    }
  };

  // 15. Close Peer Connection
  const closePeerConnection = (targetUid) => {
    const pc = pcsRef.current[targetUid];
    if (pc) {
      pc.close();
      delete pcsRef.current[targetUid];
    }
    setRemoteStreams(prev => {
      const updated = { ...prev };
      delete updated[targetUid];
      return updated;
    });
  };

  // 16. Life cycle hooks
  useEffect(() => {
    let currentUid = null;

    const setup = async () => {
      await loadRoomDetail();
      await loadFriends();

      try {
        const res = await api.get('/user/me');
        if (res) {
          currentUid = res.id;
          setUserId(res.id);
          setCurrentUserInfo(res);
          initializeMediaAndSignaling(res.id);
        }
      } catch (err) {
        console.error('Failed to authenticate or fetch user:', err);
      }
    };

    setup();

    return () => {
      cleanupConnections();
    };
  }, [roomId]);

  const isHost = room && room.creatorId === userId;

  return (
    <div style={styles.container}>
      {/* Hidden elements to play remote streams */}
      {Object.entries(remoteStreams).map(([uid, stream]) => (
        <RemoteAudio key={uid} stream={stream} />
      ))}

      {/* Header */}
      <div style={styles.header}>
        <div style={styles.headerLeft}>
          <button style={styles.backBtn} onClick={leaveRoom}>
            🚪 退出房间
          </button>
          <div style={styles.roomMeta}>
            <h2 style={styles.roomTitle}>{room?.roomName || '语音房'}</h2>
            <span style={styles.statusBadge}>
              <span style={{
                ...styles.statusDot,
                backgroundColor: connectionStatus === 'connected' ? '#10b981' : '#f59e0b'
              }} />
              {connectionStatus === 'connected' ? '信令已连接' : '正在连接信令...'}
            </span>
          </div>
        </div>

        <div style={styles.headerRight}>
          <button style={styles.secondaryBtn} onClick={() => { loadFriends(); setShowInvite(true); }}>
            👥 邀请好友
          </button>
          {isHost && (
            <button style={styles.primaryBtn} onClick={() => { setAiTabActive(0); loadAiRoles(); setShowAddAi(true); }}>
              🤖 添加 AI
            </button>
          )}
        </div>
      </div>

      {/* Split layout: top horizontal scrolling participants, middle messages, bottom controls */}
      <div style={styles.participantsHeader}>
        {/* Current User Card */}
        <div style={styles.miniUserCard}>
          <div style={{ ...styles.miniAvatar, backgroundColor: '#3b82f6' }}>
            {currentUserInfo?.nickname ? currentUserInfo.nickname[0].toUpperCase() : 'ME'}
          </div>
          <div style={styles.miniMeta}>
            <span style={styles.miniName}>{currentUserInfo?.nickname || '我'} (我)</span>
            <span style={styles.miniRole}>房主</span>
          </div>
        </div>

        {/* Other Participants */}
        {participants
          .filter(p => p.userId !== userId)
          .map((p) => {
            const isAi = p.userId < 0;
            const hasAudio = !isAi && remoteStreams[p.userId];

            return (
              <div key={p.id} style={styles.miniUserCard}>
                <div style={{
                  ...styles.miniAvatar,
                  backgroundColor: isAi ? '#8b5cf6' : '#10b981',
                  border: hasAudio ? '2px solid #10b981' : 'none'
                }}>
                  {isAi ? '🤖' : (p.nickname ? p.nickname[0].toUpperCase() : 'U')}
                </div>
                <div style={styles.miniMeta}>
                  <span style={styles.miniName}>{isAi ? p.aiRoleName : (p.nickname || `用户 ${p.userId}`)}</span>
                  <span style={styles.miniRole}>{isAi ? 'AI 伴练' : (p.role === 1 ? '房主' : '成员')}</span>
                </div>
                
                {isHost && (
                  <div style={styles.miniActionOverlay}>
                    {isAi && (
                      <button style={styles.miniBtnIcon} onClick={() => openEditAi(p)} title="设置">⚙️</button>
                    )}
                    <button style={styles.miniBtnIconDelete} onClick={() => removeParticipant(p.id)} title="移出">✕</button>
                  </div>
                )}
              </div>
            );
          })}
      </div>

      {/* Messages list */}
      <div style={styles.chatMessagesArea} ref={msgContainerRef}>
        {messages.length === 0 ? (
          <div style={styles.emptyChatText}>暂无对话记录。在下方输入或录音开始交流吧！</div>
        ) : (
          messages.map((msg) => {
            const isMe = String(msg.from) === String(userId);
            const isAi = Number(msg.from) < 0;
            return (
              <div key={msg.id} style={{
                ...styles.messageRow,
                justifyContent: isMe ? 'flex-end' : 'flex-start'
              }}>
                <div style={{
                  ...styles.messageBubble,
                  backgroundColor: isMe ? '#1e293b' : '#1e1b4b',
                  alignItems: isMe ? 'flex-end' : 'flex-start'
                }}>
                  <div style={styles.messageSenderName}>{msg.senderName}</div>
                  <div style={styles.messageContent}>{msg.content}</div>
                  <div style={styles.messageActions}>
                    <button style={{
                      ...styles.speakBtn,
                      color: currentPlayId === msg.id && ttsLoading ? '#10b981' : '#94a3b8'
                    }} onClick={() => playTts(msg.content, msg.id)}>
                      {currentPlayId === msg.id && ttsLoading ? '🔊 播放中...' : '🔈 播放语音'}
                    </button>
                  </div>
                </div>
              </div>
            );
          })
        )}
      </div>

      {/* Bottom control bar */}
      <div style={styles.bottomBar}>
        <div style={styles.bottomLeftControls}>
          <div style={styles.voiceSelectWrapper}>
            <label style={styles.miniLabel}>TTS 声音:</label>
            <select
              style={styles.select}
              value={ttsVoice}
              onChange={e => setTtsVoice(e.target.value)}
            >
              <option value="female_us">美音女声 (Aria)</option>
              <option value="male_us">美音男声 (Guy)</option>
              <option value="female_uk">英音女声 (Sonia)</option>
              <option value="male_uk">英音男声 (Ryan)</option>
            </select>
          </div>

          <button
            style={{
              ...styles.toggleAutoPlayBtn,
              background: autoPlay ? '#1e3a8a' : '#1e293b',
              borderColor: autoPlay ? '#3b82f6' : '#334155'
            }}
            onClick={() => setAutoPlay(!autoPlay)}
          >
            {autoPlay ? '🔊 自动播放已开启' : '🔇 自动播放已关闭'}
          </button>
        </div>

        <div style={styles.bottomInputArea}>
          <button
            style={{
              ...styles.recordBtn,
              background: recordState === 'recording' ? '#ef4444' : (recordState === 'processing' ? '#f59e0b' : '#3b82f6')
            }}
            onClick={toggleRecording}
            disabled={recordState === 'processing'}
          >
            {recordState === 'recording' ? '⏹️ 停止录音' : (recordState === 'processing' ? '🔄 识别中...' : '🎤 语音输入')}
          </button>

          <input
            style={styles.bottomInput}
            value={inputText}
            onChange={e => setInputText(e.target.value)}
            onKeyDown={e => { if (e.key === 'Enter') sendChatMessage(inputText); }}
            placeholder={recordState === 'recording' ? '正在录音中...' : '输入消息开始交流...'}
            disabled={recordState === 'recording'}
          />

          <button style={styles.sendBtn} onClick={() => sendChatMessage(inputText)} disabled={!inputText.trim()}>
            发送
          </button>
        </div>
      </div>

      {/* Invite Modal */}
      {showInvite && (
        <div style={styles.modalOverlay} onClick={() => setShowInvite(false)}>
          <div style={styles.modalCard} onClick={e => e.stopPropagation()}>
            <div style={styles.modalHeader}>
              <h3>邀请好友</h3>
              <button style={styles.closeBtn} onClick={() => setShowInvite(false)}>✕</button>
            </div>
            <div style={styles.modalBody}>
              {friends.length === 0 ? (
                <div style={styles.emptyText}>暂无好友可邀请。快去社交大厅添加好友吧！</div>
              ) : (
                <div style={styles.list}>
                  {friends.map(f => (
                    <div key={f.friendId} style={styles.listItem}>
                      <span style={styles.listItemName}>{f.nickname || `用户 ${f.friendId}`}</span>
                      <button style={styles.inviteActionBtn} onClick={() => inviteFriend(f.friendId)}>
                        邀请加入
                      </button>
                    </div>
                  ))}
                </div>
              )}
            </div>
          </div>
        </div>
      )}

      {/* Add AI Modal */}
      {showAddAi && (
        <div style={styles.modalOverlay} onClick={() => setShowAddAi(false)}>
          <div style={styles.modalCard} onClick={e => e.stopPropagation()}>
            <div style={styles.modalHeader}>
              <h3>添加 AI 伴练</h3>
              <button style={styles.closeBtn} onClick={() => setShowAddAi(false)}>✕</button>
            </div>
            <div style={styles.modalBody}>
              <div style={styles.tabHeader}>
                <button
                  style={{
                    ...styles.tabBtn,
                    borderBottom: aiTabActive === 0 ? '2px solid #4f46e5' : '2px solid transparent',
                    color: aiTabActive === 0 ? '#4f46e5' : '#94a3b8'
                  }}
                  onClick={() => setAiTabActive(0)}
                >
                  预设角色库
                </button>
                <button
                  style={{
                    ...styles.tabBtn,
                    borderBottom: aiTabActive === 1 ? '2px solid #4f46e5' : '2px solid transparent',
                    color: aiTabActive === 1 ? '#4f46e5' : '#94a3b8'
                  }}
                  onClick={() => setAiTabActive(1)}
                >
                  自定义 AI
                </button>
              </div>

              {aiTabActive === 0 ? (
                <div style={styles.rolesGrid}>
                  {aiRolesLoading ? (
                    <div style={styles.rolesStatus}>加载中...</div>
                  ) : aiRolesError ? (
                    <div style={styles.rolesError}>{aiRolesError}</div>
                  ) : aiRoles.length === 0 ? (
                    <div style={styles.rolesStatus}>暂无预设角色</div>
                  ) : (
                    aiRoles.map(role => (
                      <div key={role.id} style={styles.roleCard} onClick={() => {
                        setAiName(role.name);
                        setAiSetting(role.setting);
                        setAiTabActive(1);
                      }}>
                        <div style={styles.roleCardName}>{role.name}</div>
                        <div style={styles.roleCardDesc}>{role.description}</div>
                      </div>
                    ))
                  )}
                </div>
              ) : (
                <>
                  <div style={styles.formGroup}>
                    <label style={styles.label}>AI 角色名字</label>
                    <input
                      style={styles.input}
                      value={aiName}
                      onChange={e => setAiName(e.target.value)}
                      placeholder="例如: Bella"
                    />
                  </div>
                  <div style={styles.formGroup}>
                    <label style={styles.label}>AI 角色提示词设定</label>
                    <textarea
                      style={styles.textarea}
                      value={aiSetting}
                      onChange={e => setAiSetting(e.target.value)}
                      rows="4"
                      placeholder="例如: 你是一个友好的 IELTS 英语考官，负责向用户提问并纠错。"
                    />
                  </div>
                  <button style={styles.submitBtn} onClick={addAiAgent}>
                    确定添加
                  </button>
                </>
              )}
            </div>
          </div>
        </div>
      )}

      {/* Edit AI Modal */}
      {showEditAi && (
        <div style={styles.modalOverlay} onClick={() => setShowEditAi(false)}>
          <div style={styles.modalCard} onClick={e => e.stopPropagation()}>
            <div style={styles.modalHeader}>
              <h3>修改 AI 设定</h3>
              <button style={styles.closeBtn} onClick={() => setShowEditAi(false)}>✕</button>
            </div>
            <div style={styles.modalBody}>
              <div style={styles.formGroup}>
                <label style={styles.label}>AI 角色名字</label>
                <input
                  style={styles.input}
                  value={aiName}
                  onChange={e => setAiName(e.target.value)}
                />
              </div>
              <div style={styles.formGroup}>
                <label style={styles.label}>AI 角色提示词设定</label>
                <textarea
                  style={styles.textarea}
                  value={aiSetting}
                  onChange={e => setAiSetting(e.target.value)}
                  rows="4"
                />
              </div>
              <button style={styles.submitBtn} onClick={updateAiAgent}>
                保存设定
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

const styles = {
  container: {
    display: 'flex',
    flexDirection: 'column',
    height: '90vh',
    background: '#090d16',
    borderRadius: '16px',
    border: '1px solid #1e293b',
    color: '#f8fafc',
    overflow: 'hidden',
    fontFamily: '"Outfit", sans-serif',
  },
  header: {
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'center',
    padding: '16px 24px',
    background: '#0d1527',
    borderBottom: '1px solid #1e293b',
  },
  headerLeft: {
    display: 'flex',
    alignItems: 'center',
    gap: '20px',
  },
  backBtn: {
    padding: '8px 16px',
    background: '#ef4444',
    color: '#fff',
    border: 'none',
    borderRadius: '8px',
    fontWeight: 'bold',
    cursor: 'pointer',
    transition: 'background 0.2s',
  },
  roomMeta: {
    display: 'flex',
    flexDirection: 'column',
  },
  roomTitle: {
    margin: 0,
    fontSize: '18px',
    fontWeight: '600',
  },
  statusBadge: {
    display: 'flex',
    alignItems: 'center',
    fontSize: '12px',
    color: '#94a3b8',
    marginTop: '4px',
    gap: '6px',
  },
  statusDot: {
    width: '8px',
    height: '8px',
    borderRadius: '50%',
    display: 'inline-block',
  },
  headerRight: {
    display: 'flex',
    gap: '12px',
  },
  primaryBtn: {
    padding: '10px 20px',
    background: 'linear-gradient(135deg, #4f46e5, #7c3aed)',
    color: '#fff',
    border: 'none',
    borderRadius: '8px',
    fontWeight: '600',
    cursor: 'pointer',
  },
  secondaryBtn: {
    padding: '10px 20px',
    background: '#1e293b',
    color: '#f8fafc',
    border: '1px solid #334155',
    borderRadius: '8px',
    fontWeight: '600',
    cursor: 'pointer',
  },
  participantsHeader: {
    display: 'flex',
    gap: '16px',
    padding: '12px 24px',
    background: '#0a101f',
    borderBottom: '1px solid #1e293b',
    overflowX: 'auto',
  },
  miniUserCard: {
    display: 'flex',
    alignItems: 'center',
    gap: '10px',
    padding: '8px 12px',
    background: '#131e35',
    borderRadius: '8px',
    border: '1px solid #1e293b',
    minWidth: '150px',
    position: 'relative',
  },
  miniAvatar: {
    width: '32px',
    height: '32px',
    borderRadius: '50%',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    fontSize: '14px',
    color: '#fff',
    fontWeight: 'bold',
  },
  miniMeta: {
    display: 'flex',
    flexDirection: 'column',
  },
  miniName: {
    fontSize: '13px',
    fontWeight: '600',
    color: '#f8fafc',
  },
  miniRole: {
    fontSize: '10px',
    color: '#94a3b8',
  },
  miniActionOverlay: {
    position: 'absolute',
    right: '4px',
    top: '4px',
    display: 'flex',
    gap: '4px',
  },
  miniBtnIcon: {
    background: '#1e293b',
    border: 'none',
    color: '#3b82f6',
    cursor: 'pointer',
    fontSize: '10px',
    padding: '2px',
    borderRadius: '4px',
  },
  miniBtnIconDelete: {
    background: '#1e293b',
    border: 'none',
    color: '#ef4444',
    cursor: 'pointer',
    fontSize: '10px',
    padding: '2px',
    borderRadius: '4px',
  },
  chatMessagesArea: {
    flex: 1,
    padding: '24px',
    overflowY: 'auto',
    display: 'flex',
    flexDirection: 'column',
    gap: '16px',
    background: '#090d16',
  },
  emptyChatText: {
    margin: 'auto',
    color: '#64748b',
    textAlign: 'center',
    fontSize: '14px',
  },
  messageRow: {
    display: 'flex',
    width: '100%',
  },
  messageBubble: {
    display: 'flex',
    flexDirection: 'column',
    maxWidth: '70%',
    padding: '12px 16px',
    borderRadius: '12px',
    color: '#f8fafc',
    gap: '4px',
    boxShadow: '0 2px 8px rgba(0,0,0,0.15)',
  },
  messageSenderName: {
    fontSize: '11px',
    fontWeight: 'bold',
    color: '#94a3b8',
  },
  messageContent: {
    fontSize: '14px',
    lineHeight: '1.5',
    whiteSpace: 'pre-wrap',
  },
  messageActions: {
    display: 'flex',
    marginTop: '6px',
  },
  speakBtn: {
    background: 'none',
    border: 'none',
    padding: 0,
    fontSize: '11px',
    cursor: 'pointer',
    display: 'flex',
    alignItems: 'center',
    gap: '4px',
  },
  bottomBar: {
    display: 'flex',
    flexDirection: 'column',
    gap: '12px',
    padding: '16px 24px',
    background: '#0d1527',
    borderTop: '1px solid #1e293b',
  },
  bottomLeftControls: {
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  voiceSelectWrapper: {
    display: 'flex',
    alignItems: 'center',
    gap: '8px',
  },
  miniLabel: {
    fontSize: '12px',
    color: '#94a3b8',
  },
  select: {
    background: '#131e35',
    border: '1px solid #1e293b',
    color: '#fff',
    padding: '4px 8px',
    borderRadius: '6px',
    fontSize: '12px',
    outline: 'none',
  },
  toggleAutoPlayBtn: {
    padding: '6px 12px',
    color: '#fff',
    border: '1px solid',
    borderRadius: '6px',
    cursor: 'pointer',
    fontSize: '12px',
    fontWeight: '600',
  },
  bottomInputArea: {
    display: 'flex',
    gap: '12px',
  },
  recordBtn: {
    padding: '10px 20px',
    color: '#fff',
    border: 'none',
    borderRadius: '8px',
    fontWeight: '600',
    cursor: 'pointer',
    transition: 'background 0.2s',
  },
  bottomInput: {
    flex: 1,
    background: '#131e35',
    border: '1px solid #1e293b',
    color: '#fff',
    padding: '10px 16px',
    borderRadius: '8px',
    outline: 'none',
  },
  sendBtn: {
    padding: '10px 20px',
    background: 'linear-gradient(135deg, #4f46e5, #7c3aed)',
    color: '#fff',
    border: 'none',
    borderRadius: '8px',
    fontWeight: '600',
    cursor: 'pointer',
  },
  // Modal styles
  modalOverlay: {
    position: 'fixed',
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
    background: 'rgba(0,0,0,0.7)',
    display: 'flex',
    justifyContent: 'center',
    alignItems: 'center',
    zIndex: 1000,
  },
  modalCard: {
    background: '#0d1527',
    border: '1px solid #1e293b',
    borderRadius: '12px',
    width: '480px',
    maxWidth: '90%',
    padding: '24px',
    color: '#f8fafc',
  },
  modalHeader: {
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'center',
    borderBottom: '1px solid #1e293b',
    paddingBottom: '12px',
    marginBottom: '16px',
  },
  closeBtn: {
    background: 'none',
    border: 'none',
    color: '#94a3b8',
    fontSize: '20px',
    cursor: 'pointer',
  },
  modalBody: {
    display: 'flex',
    flexDirection: 'column',
    gap: '16px',
  },
  tabHeader: {
    display: 'flex',
    borderBottom: '1px solid #1e293b',
    marginBottom: '8px',
  },
  tabBtn: {
    flex: 1,
    background: 'none',
    border: 'none',
    borderBottom: '2px solid transparent',
    padding: '10px',
    cursor: 'pointer',
    fontSize: '14px',
    fontWeight: '600',
  },
  rolesGrid: {
    display: 'grid',
    gridTemplateColumns: 'repeat(2, 1fr)',
    gap: '12px',
    maxHeight: '280px',
    overflowY: 'auto',
  },
  roleCard: {
    background: '#131e35',
    border: '1px solid #1e293b',
    borderRadius: '8px',
    padding: '12px',
    cursor: 'pointer',
    transition: 'all 0.2s',
  },
  roleCardName: {
    fontWeight: 'bold',
    fontSize: '14px',
    color: '#8b5cf6',
    marginBottom: '6px',
  },
  roleCardDesc: {
    fontSize: '12px',
    color: '#94a3b8',
    lineHeight: '1.4',
  },
  rolesStatus: {
    gridColumn: '1 / -1',
    textAlign: 'center',
    color: '#94a3b8',
    padding: '40px 0',
    fontSize: '14px',
  },
  rolesError: {
    gridColumn: '1 / -1',
    textAlign: 'center',
    color: '#f87171',
    padding: '40px 0',
    fontSize: '14px',
  },
  formGroup: {
    display: 'flex',
    flexDirection: 'column',
    gap: '6px',
  },
  label: {
    fontSize: '14px',
    color: '#94a3b8',
  },
  input: {
    background: '#131e35',
    border: '1px solid #1e293b',
    color: '#fff',
    padding: '10px',
    borderRadius: '8px',
  },
  textarea: {
    background: '#131e35',
    border: '1px solid #1e293b',
    color: '#fff',
    padding: '10px',
    borderRadius: '8px',
    resize: 'none',
  },
  submitBtn: {
    background: 'linear-gradient(135deg, #4f46e5, #7c3aed)',
    color: '#fff',
    padding: '12px',
    borderRadius: '8px',
    border: 'none',
    fontWeight: 'bold',
    cursor: 'pointer',
    marginTop: '10px',
  },
  emptyText: {
    textAlign: 'center',
    color: '#64748b',
    padding: '20px 0',
  },
  list: {
    display: 'flex',
    flexDirection: 'column',
    gap: '10px',
  },
  listItem: {
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'center',
    background: '#131e35',
    padding: '12px',
    borderRadius: '8px',
    border: '1px solid #1e293b',
  },
  listItemName: {
    fontWeight: '500',
  },
  inviteActionBtn: {
    background: '#10b981',
    color: '#fff',
    border: 'none',
    padding: '6px 12px',
    borderRadius: '6px',
    cursor: 'pointer',
    fontWeight: '600',
  }
};
