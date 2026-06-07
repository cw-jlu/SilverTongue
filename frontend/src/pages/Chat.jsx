import { useEffect, useRef, useState } from 'react';
import { Mic, Send, Sparkles, Settings, StopCircle, Volume2 } from 'lucide-react';
import api from '../api/client';

const PRESET_ROLES = ['日常闲聊', '雅思考官', '外企 HR 面试', '商务会议', '旅游向导', '餐厅点餐', '自定义'];
const CEFR_LEVELS = ['A2 (初级)', 'B1 (中级)', 'B2 (中高级)', 'C1 (高级)'];

export default function Chat() {
  const [session, setSession] = useState(null);
  const [messages, setMessages] = useState([]);
  const [roleType, setRoleType] = useState('日常闲聊');
  const [customRole, setCustomRole] = useState('');
  const [level, setLevel] = useState('B2 (中高级)');
  const [contextFile, setContextFile] = useState(null);
  const [uploadingContext, setUploadingContext] = useState(false);
  const [inputText, setInputText] = useState('');
  const [chatMode, setChatMode] = useState('text');
  const [isRecording, setIsRecording] = useState(false);
  const [playbackSpeed, setPlaybackSpeed] = useState(1.0);
  const [scaffoldingHints, setScaffoldingHints] = useState([]);
  const [isSending, setIsSending] = useState(false);

  const messagesEndRef = useRef(null);
  const mediaRecorderRef = useRef(null);
  const audioChunksRef = useRef([]);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  useEffect(() => {
    if (!session?.id) {
      return;
    }
    loadMessages(session.id);
  }, [session]);

  useEffect(() => {
    if (!session?.id || chatMode !== 'text' || !inputText.trim()) {
      setScaffoldingHints([]);
      return;
    }

    const timer = setTimeout(async () => {
      try {
        const res = await api.get(`/session/${session.id}/scaffolding?q=${encodeURIComponent(inputText)}`);
        setScaffoldingHints(Array.isArray(res) ? res : []);
      } catch (error) {
        console.error('Scaffolding load failed', error);
      }
    }, 400);

    return () => clearTimeout(timer);
  }, [chatMode, inputText, session]);

  const loadMessages = async (sid) => {
    try {
      const res = await api.get(`/session/${sid}/messages`);
      setMessages(Array.isArray(res) ? res : []);
    } catch (error) {
      console.error('Failed to load messages', error);
    }
  };

  const playAudioFromBase64 = (base64) => {
    const audio = new Audio(`data:audio/wav;base64,${base64}`);
    audio.playbackRate = playbackSpeed;
    audio.play().catch((error) => console.warn('Auto-play blocked by browser.', error));
  };

  const playAudioFromUrl = (url) => {
    const audio = new Audio(url);
    audio.playbackRate = playbackSpeed;
    audio.play().catch((error) => console.error('Play url audio failed', error));
  };

  const createSession = async () => {
    try {
      setUploadingContext(true);
      const topic = roleType === '自定义' ? customRole.trim() : roleType;
      const targetLevel = level.split(' ')[0];

      let contextFileUrl = null;
      if (contextFile) {
        const fd = new FormData();
        fd.append('file', contextFile);
        contextFileUrl = await api.post('/session/upload_context', fd);
      }

      const created = await api.post('/session/create', {
        type: 'ai_chat',
        mode: 'free_talk',
        topic,
        userLevel: targetLevel,
        contextFileUrl,
      });

      setSession(created);
      setMessages([]);
      setInputText('');
      setScaffoldingHints([]);
    } catch (error) {
      console.error('Failed to create session', error);
      alert('创建会话失败，请重试');
    } finally {
      setUploadingContext(false);
    }
  };

  const sendTextMessage = async () => {
    if (!session?.id || !inputText.trim() || isSending) {
      return;
    }

    const text = inputText.trim();
    setIsSending(true);
    setInputText('');
    setScaffoldingHints([]);

    const userMessage = {
      id: Date.now(),
      sender: 'user',
      content: text,
      createTime: new Date().toISOString(),
    };
    setMessages((prev) => [...prev, userMessage]);

    try {
      const res = await api.post(`/session/${session.id}/chat`, {
        type: 'text',
        content: text,
      });

      if (res) {
        setMessages((prev) => [...prev, res]);
        if (res.audioBase64) {
          playAudioFromBase64(res.audioBase64);
        }
      }
    } catch (error) {
      console.error('Send text failed', error);
      alert('消息发送失败，请重试');
      setMessages((prev) => prev.filter((message) => message.id !== userMessage.id));
      setInputText(text);
    } finally {
      setIsSending(false);
    }
  };

  const sendVoiceMessage = async (blob) => {
    if (!session?.id || isSending) {
      return;
    }

    setIsSending(true);
    const placeholderId = Date.now();
    setMessages((prev) => [
      ...prev,
      {
        id: placeholderId,
        sender: 'user',
        content: '[语音转写中...]',
        createTime: new Date().toISOString(),
      },
    ]);

    const reader = new FileReader();
    reader.readAsDataURL(blob);
    reader.onloadend = async () => {
      const base64data = reader.result.split(',')[1];
      try {
        const res = await api.post(`/session/${session.id}/chat`, {
          type: 'audio',
          audioBase64: base64data,
        });

        if (res) {
          setMessages((prev) => {
            const filtered = prev.filter((message) => message.id !== placeholderId);
            const userSpeech = {
              id: Date.now(),
              sender: 'user',
              content: res.userTranscript || '[speech recognition failed]',
              createTime: new Date().toISOString(),
            };
            return [...filtered, userSpeech, res];
          });

          if (res.audioBase64) {
            playAudioFromBase64(res.audioBase64);
          }
        }
      } catch (error) {
        console.error('Send voice failed', error);
        setMessages((prev) => prev.filter((message) => message.id !== placeholderId));
        alert('语音发送失败，请重试');
      } finally {
        setIsSending(false);
      }
    };
  };

  const startRecording = async () => {
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      const recorder = new MediaRecorder(stream);
      mediaRecorderRef.current = recorder;
      audioChunksRef.current = [];

      recorder.ondataavailable = (event) => {
        if (event.data.size > 0) {
          audioChunksRef.current.push(event.data);
        }
      };

      recorder.onstop = async () => {
        const audioBlob = new Blob(audioChunksRef.current, { type: 'audio/wav' });
        await sendVoiceMessage(audioBlob);
      };

      recorder.start();
      setIsRecording(true);
    } catch (error) {
      console.error('Failed to start recording', error);
      alert('麦克风权限获取失败，请检查浏览器权限设置');
    }
  };

  const stopRecording = () => {
    if (!mediaRecorderRef.current || !isRecording) {
      return;
    }

    mediaRecorderRef.current.stop();
    mediaRecorderRef.current.stream.getTracks().forEach((track) => track.stop());
    setIsRecording(false);
  };

  const completeSession = async () => {
    if (!session?.id) {
      return;
    }

    if (!window.confirm('确定要结束本轮对话吗？系统会自动保存练习历史。')) {
      return;
    }

    await api.post(`/session/${session.id}/complete`);
    setSession(null);
    setMessages([]);
    setScaffoldingHints([]);
    setInputText('');
  };

  return (
    <div style={{
      display: 'flex',
      flexDirection: 'column',
      height: 'calc(100vh - 80px)',
      backgroundColor: '#f3f4f6',
      borderRadius: '16px',
      overflow: 'hidden',
      boxShadow: '0 4px 20px rgba(0,0,0,0.05)'
    }}>
      <div style={{
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'center',
        padding: '16px 24px',
        backgroundColor: '#ffffff',
        borderBottom: '1px solid #e5e7eb',
        zIndex: 10
      }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
          <div style={{
            width: '40px',
            height: '40px',
            borderRadius: '50%',
            backgroundColor: '#e8e8ff',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            color: '#6C3FF5'
          }}>
            <Sparkles size={20} />
          </div>
          <div>
            <h3 style={{ margin: 0, fontSize: '16px', fontWeight: 'bold', color: '#111827' }}>
              {session ? `口语对练：${session.topic}` : '配置 AI 对练角色'}
            </h3>
            <p style={{ margin: 0, fontSize: '12px', color: '#6b7280' }}>
              {session ? `等级：${session.userLevel || 'B2'} | 实时纠错与语速调节已启用` : '设定你的专属智能口语外教'}
            </p>
          </div>
        </div>

        {session && (
          <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px', fontSize: '13px', color: '#4b5563' }}>
              <Volume2 size={16} />
              <span>语速</span>
              <select
                value={playbackSpeed}
                onChange={(e) => setPlaybackSpeed(parseFloat(e.target.value))}
                style={{
                  padding: '4px 8px',
                  borderRadius: '6px',
                  border: '1px solid #d1d5db',
                  outline: 'none',
                  fontSize: '13px',
                  cursor: 'pointer'
                }}
              >
                <option value="0.8">0.8x</option>
                <option value="1.0">1.0x</option>
                <option value="1.2">1.2x</option>
                <option value="1.5">1.5x</option>
              </select>
            </div>

            <button
              onClick={completeSession}
              style={{
                padding: '8px 16px',
                backgroundColor: '#fef2f2',
                color: '#ef4444',
                border: '1px solid #fee2e2',
                borderRadius: '8px',
                fontSize: '13px',
                fontWeight: 600,
                cursor: 'pointer'
              }}
            >
              结束对话
            </button>
          </div>
        )}
      </div>

      <div style={{ display: 'flex', flex: 1, overflow: 'hidden' }}>
        {!session ? (
          <div style={{
            display: 'flex',
            flexDirection: 'column',
            width: '100%',
            maxWidth: '560px',
            margin: 'auto',
            backgroundColor: '#ffffff',
            padding: '32px',
            borderRadius: '16px',
            boxShadow: '0 10px 25px rgba(0,0,0,0.05)'
          }}>
            <h2 style={{ fontSize: '22px', fontWeight: 'bold', margin: '0 0 24px', color: '#111827', display: 'flex', alignItems: 'center', gap: '8px' }}>
              <Sparkles color="#6C3FF5" /> 配置你的英语对练场景
            </h2>

            <div style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
              <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
                <label style={{ fontSize: '14px', fontWeight: 600, color: '#374151' }}>练习角色/场景</label>
                <select
                  value={roleType}
                  onChange={(e) => setRoleType(e.target.value)}
                  style={{
                    height: '42px',
                    padding: '0 12px',
                    borderRadius: '8px',
                    border: '1px solid #d1d5db',
                    fontSize: '14px',
                    outline: 'none'
                  }}
                >
                  {PRESET_ROLES.map((role) => (
                    <option key={role} value={role}>{role}</option>
                  ))}
                </select>
              </div>

              {roleType === '自定义' && (
                <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
                  <label style={{ fontSize: '14px', fontWeight: 600, color: '#374151' }}>自定义 AI 设定</label>
                  <textarea
                    placeholder="例如：你是一位热情的咖啡店店员，正在和我确认订单。"
                    value={customRole}
                    onChange={(e) => setCustomRole(e.target.value)}
                    rows={3}
                    style={{
                      padding: '12px',
                      borderRadius: '8px',
                      border: '1px solid #d1d5db',
                      fontSize: '14px',
                      outline: 'none',
                      resize: 'none'
                    }}
                  />
                </div>
              )}

              <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
                <label style={{ fontSize: '14px', fontWeight: 600, color: '#374151' }}>难度等级 (CEFR)</label>
                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: '8px' }}>
                  {CEFR_LEVELS.map((item) => {
                    const code = item.split(' ')[0];
                    const active = level.startsWith(code);
                    return (
                      <button
                        key={item}
                        onClick={() => setLevel(item)}
                        style={{
                          height: '40px',
                          backgroundColor: active ? '#6C3FF5' : '#ffffff',
                          color: active ? '#ffffff' : '#374151',
                          border: `1px solid ${active ? '#6C3FF5' : '#d1d5db'}`,
                          borderRadius: '8px',
                          fontSize: '13px',
                          fontWeight: 500,
                          cursor: 'pointer'
                        }}
                      >
                        {item}
                      </button>
                    );
                  })}
                </div>
              </div>

              <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
                <label style={{ fontSize: '14px', fontWeight: 600, color: '#374151' }}>背景材料（可选，支持 PDF/DOCX/TXT）</label>
                <div style={{
                  border: '2px dashed #d1d5db',
                  borderRadius: '8px',
                  padding: '16px',
                  textAlign: 'center',
                  backgroundColor: '#f9fafb',
                  cursor: 'pointer',
                  position: 'relative'
                }}>
                  <input
                    type="file"
                    accept=".pdf,.docx,.doc,.txt"
                    onChange={(e) => setContextFile(e.target.files[0] || null)}
                    style={{
                      position: 'absolute',
                      inset: 0,
                      opacity: 0,
                      cursor: 'pointer'
                    }}
                  />
                  <p style={{ margin: 0, fontSize: '13px', color: '#4b5563' }}>
                    {contextFile ? `已选择：${contextFile.name}` : '点击选择背景材料'}
                  </p>
                  <p style={{ margin: '4px 0 0', fontSize: '11px', color: '#9ca3af' }}>
                    AI 会把材料内容作为对练背景上下文
                  </p>
                </div>
              </div>

              <button
                onClick={createSession}
                disabled={uploadingContext}
                style={{
                  height: '48px',
                  backgroundColor: '#6C3FF5',
                  color: '#ffffff',
                  border: 'none',
                  borderRadius: '8px',
                  fontWeight: 600,
                  fontSize: '16px',
                  cursor: 'pointer',
                  marginTop: '12px'
                }}
              >
                {uploadingContext ? '建立会话中...' : '开始口语练习'}
              </button>
            </div>
          </div>
        ) : (
          <>
            <div style={{
              width: '260px',
              backgroundColor: '#ffffff',
              borderRight: '1px solid #e5e7eb',
              display: 'flex',
              flexDirection: 'column',
              padding: '20px',
              gap: '20px'
            }}>
              <div>
                <h4 style={{ margin: '0 0 10px', fontSize: '13px', textTransform: 'uppercase', color: '#9ca3af', fontWeight: 600 }}>当前会话信息</h4>
                <div style={{ display: 'flex', flexDirection: 'column', gap: '8px', fontSize: '13px', color: '#4b5563' }}>
                  <div>场景: <strong style={{ color: '#111827' }}>{session.topic}</strong></div>
                  <div>等级: <strong style={{ color: '#111827' }}>{session.userLevel || 'B2'}</strong></div>
                  <div>模式: <strong style={{ color: '#111827' }}>自由对练</strong></div>
                </div>
              </div>

              <hr style={{ border: 0, borderTop: '1px solid #f3f4f6', margin: 0 }} />

              <div style={{ flex: 1, display: 'flex', flexDirection: 'column' }}>
                <h4 style={{ margin: '0 0 10px', fontSize: '13px', textTransform: 'uppercase', color: '#9ca3af', fontWeight: 600 }}>表达建议</h4>
                <p style={{ fontSize: '12px', color: '#6b7280', margin: '0 0 12px' }}>输入英文时，会实时给出可点击的表达补全建议。</p>

                <div style={{
                  flex: 1,
                  display: 'flex',
                  flexDirection: 'column',
                  gap: '8px',
                  overflowY: 'auto',
                  maxHeight: '260px',
                  paddingRight: '4px'
                }}>
                  {scaffoldingHints.length > 0 ? (
                    scaffoldingHints.map((hint, index) => (
                      <div
                        key={index}
                        onClick={() => {
                          setInputText((prev) => `${prev} ${hint}`.trim());
                          setScaffoldingHints([]);
                        }}
                        style={{
                          padding: '10px',
                          backgroundColor: '#f5f3ff',
                          border: '1px solid #ddd6fe',
                          borderRadius: '8px',
                          fontSize: '12px',
                          color: '#5b21b6',
                          cursor: 'pointer',
                          lineHeight: '1.4'
                        }}
                      >
                        {hint}
                      </div>
                    ))
                  ) : (
                    <div style={{
                      padding: '16px',
                      textAlign: 'center',
                      color: '#9ca3af',
                      fontSize: '12px',
                      border: '1px dashed #e5e7eb',
                      borderRadius: '8px'
                    }}>
                      输入英文以触发表达建议
                    </div>
                  )}
                </div>
              </div>
            </div>

            <div style={{ display: 'flex', flexDirection: 'column', flex: 1, backgroundColor: '#f9fafb' }}>
              <div style={{
                flex: 1,
                padding: '24px',
                overflowY: 'auto',
                display: 'flex',
                flexDirection: 'column',
                gap: '20px'
              }}>
                {messages.length === 0 ? (
                  <div style={{
                    margin: 'auto',
                    textAlign: 'center',
                    maxWidth: '320px',
                    color: '#9ca3af',
                    fontSize: '14px'
                  }}>
                    <div style={{ fontSize: '32px', marginBottom: '12px' }}>💬</div>
                    会话已开启。发送语音或文字，开始和 AI 对练吧。
                  </div>
                ) : (
                  messages.map((msg, index) => {
                    const isUser = msg.sender === 'user';
                    return (
                      <div key={msg.id || index} style={{
                        display: 'flex',
                        flexDirection: 'column',
                        alignItems: isUser ? 'flex-end' : 'flex-start'
                      }}>
                        <div style={{ display: 'flex', gap: '10px', maxWidth: '75%', flexDirection: isUser ? 'row-reverse' : 'row' }}>
                          <div style={{
                            width: '36px',
                            height: '36px',
                            borderRadius: '50%',
                            backgroundColor: isUser ? '#6C3FF5' : '#e5e7eb',
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'center',
                            color: isUser ? '#ffffff' : '#4b5563',
                            fontWeight: 'bold',
                            fontSize: '13px'
                          }}>
                            {isUser ? 'ME' : 'AI'}
                          </div>

                          <div style={{ display: 'flex', flexDirection: 'column', gap: '4px' }}>
                            <div style={{
                              padding: '12px 16px',
                              borderRadius: isUser ? '16px 4px 16px 16px' : '4px 16px 16px 16px',
                              backgroundColor: isUser ? '#6C3FF5' : '#ffffff',
                              color: isUser ? '#ffffff' : '#1b1b1b',
                              fontSize: '14px',
                              lineHeight: '1.5',
                              boxShadow: '0 1px 3px rgba(0,0,0,0.05)'
                            }}>
                              {msg.content}
                              {msg.audioUrl && (
                                <button
                                  onClick={() => playAudioFromUrl(msg.audioUrl)}
                                  style={{
                                    border: 'none',
                                    background: 'none',
                                    cursor: 'pointer',
                                    padding: '0 0 0 8px',
                                    color: isUser ? '#ffffff' : '#6C3FF5',
                                    verticalAlign: 'middle'
                                  }}
                                  title="播放录音"
                                >
                                  <Volume2 size={15} style={{ display: 'inline' }} />
                                </button>
                              )}
                            </div>

                            {!isUser && msg.refinedContent && (
                              <div style={{
                                marginTop: '6px',
                                padding: '10px 12px',
                                backgroundColor: '#f0fdf4',
                                border: '1px solid #bbf7d0',
                                borderRadius: '8px',
                                fontSize: '12px',
                                color: '#166534'
                              }}>
                                <strong>更地道的表达：</strong> {msg.refinedContent}
                              </div>
                            )}

                            {!isUser && msg.chinglishFeedback && (
                              <div style={{
                                marginTop: '6px',
                                padding: '10px 12px',
                                backgroundColor: '#fff7ed',
                                border: '1px solid #ffedd5',
                                borderRadius: '8px',
                                fontSize: '12px',
                                color: '#c2410c',
                                whiteSpace: 'pre-wrap'
                              }}>
                                <strong>语法与表达提醒：</strong> {msg.chinglishFeedback}
                              </div>
                            )}
                          </div>
                        </div>
                      </div>
                    );
                  })
                )}
                <div ref={messagesEndRef} />
              </div>

              <div style={{
                padding: '16px 24px',
                backgroundColor: '#ffffff',
                borderTop: '1px solid #e5e7eb'
              }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
                  <button
                    onClick={() => setChatMode(chatMode === 'text' ? 'voice' : 'text')}
                    style={{
                      width: '40px',
                      height: '40px',
                      borderRadius: '50%',
                      border: '1px solid #e5e7eb',
                      backgroundColor: chatMode === 'voice' ? '#6C3FF5' : '#ffffff',
                      color: chatMode === 'voice' ? '#ffffff' : '#6b7280',
                      cursor: 'pointer',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center'
                    }}
                    title={chatMode === 'text' ? '切换到语音输入' : '切换到文字输入'}
                  >
                    {chatMode === 'text' ? <Mic size={18} /> : <Settings size={18} />}
                  </button>

                  {chatMode === 'text' ? (
                    <div style={{ display: 'flex', flex: 1, gap: '10px' }}>
                      <input
                        type="text"
                        placeholder="输入内容开始对话..."
                        value={inputText}
                        onChange={(e) => setInputText(e.target.value)}
                        onKeyDown={(e) => {
                          if (e.key === 'Enter') {
                            sendTextMessage();
                          }
                        }}
                        disabled={isSending}
                        style={{
                          height: '42px',
                          flex: 1,
                          padding: '0 16px',
                          border: '1px solid #d1d5db',
                          borderRadius: '21px',
                          outline: 'none',
                          fontSize: '14px',
                          margin: 0
                        }}
                      />
                      <button
                        onClick={sendTextMessage}
                        disabled={!inputText.trim() || isSending}
                        style={{
                          width: '42px',
                          height: '42px',
                          borderRadius: '50%',
                          backgroundColor: inputText.trim() && !isSending ? '#6C3FF5' : '#f3f4f6',
                          color: inputText.trim() && !isSending ? '#ffffff' : '#9ca3af',
                          border: 'none',
                          cursor: 'pointer',
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'center'
                        }}
                      >
                        <Send size={16} />
                      </button>
                    </div>
                  ) : (
                    <div style={{ display: 'flex', flex: 1, justifyContent: 'center' }}>
                      {isRecording ? (
                        <button
                          onClick={stopRecording}
                          style={{
                            height: '42px',
                            padding: '0 24px',
                            borderRadius: '21px',
                            backgroundColor: '#ef4444',
                            color: '#ffffff',
                            border: 'none',
                            cursor: 'pointer',
                            display: 'flex',
                            alignItems: 'center',
                            gap: '8px',
                            fontSize: '14px',
                            fontWeight: 600
                          }}
                        >
                          <StopCircle size={18} />
                          <span>录音中，点击结束并发送</span>
                        </button>
                      ) : (
                        <button
                          onClick={startRecording}
                          disabled={isSending}
                          style={{
                            height: '42px',
                            padding: '0 24px',
                            borderRadius: '21px',
                            backgroundColor: isSending ? '#f3f4f6' : '#6C3FF5',
                            color: isSending ? '#9ca3af' : '#ffffff',
                            border: 'none',
                            cursor: 'pointer',
                            display: 'flex',
                            alignItems: 'center',
                            gap: '8px',
                            fontSize: '14px',
                            fontWeight: 600
                          }}
                        >
                          <Mic size={18} />
                          <span>点击开始录音</span>
                        </button>
                      )}
                    </div>
                  )}
                </div>
              </div>
            </div>
          </>
        )}
      </div>
    </div>
  );
}
