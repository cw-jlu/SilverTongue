import { useState, useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { Mic, Send, Volume2, Sparkles, AlertCircle, HelpCircle, StopCircle, RefreshCw, ChevronRight, User, Settings } from 'lucide-react';
import api from '../api/client';

export default function Chat() {
  const [session, setSession] = useState(null);
  const [messages, setMessages] = useState([]);
  const [roleType, setRoleType] = useState('日常闲聊');
  const [customRole, setCustomRole] = useState('');
  const [level, setLevel] = useState('B2');
  const [contextFile, setContextFile] = useState(null);
  const [uploadingContext, setUploadingContext] = useState(false);

  // 聊天输入与控制状态
  const [inputText, setInputText] = useState('');
  const [chatMode, setChatMode] = useState('text'); // 'text' or 'voice'
  const [isRecording, setIsRecording] = useState(false);
  const [playbackSpeed, setPlaybackSpeed] = useState(1.0);
  const [scaffoldingHints, setScaffoldingHints] = useState([]);
  const [isSending, setIsSending] = useState(false);

  const messagesEndRef = useRef(null);
  const mediaRecorderRef = useRef(null);
  const audioChunksRef = useRef([]);

  const PRESET_ROLES = ['日常闲聊', '雅思考官', '外企 HR 面试', '商务会议', '旅游向导', '餐厅点餐', '自定义'];
  const CEFR_LEVELS = ['A2 (初级)', 'B1 (中级)', 'B2 (中高级)', 'C1 (高级)'];

  // 自动滚动到最新消息
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  // 加载会话历史消息
  const loadMessages = async (sid) => {
    try {
      const res = await api.get(`/session/${sid}/messages`);
      if (res.data) {
        setMessages(res.data);
      }
    } catch (e) {
      console.error("Failed to load messages", e);
    }
  };

  // 定时轮询或者在创建会话时读取
  useEffect(() => {
    if (session) {
      loadMessages(session.id);
    }
  }, [session]);

  // 脚手架自动联想（防抖）
  useEffect(() => {
    if (!session || !inputText.trim() || chatMode !== 'text') {
      setScaffoldingHints([]);
      return;
    }

    const timer = setTimeout(async () => {
      try {
        const res = await api.get(`/session/${session.id}/scaffolding?q=${encodeURIComponent(inputText)}`);
        if (res.data) {
          setScaffoldingHints(res.data);
        }
      } catch (e) {
        console.error("Scaffolding load failed", e);
      }
    }, 400);

    return () => clearTimeout(timer);
  }, [inputText, session, chatMode]);

  // 创建新会话
  const createSession = async () => {
    try {
      setUploadingContext(true);
      const finalTopic = roleType === '自定义' ? customRole : roleType;
      
      let contextFileUrl = null;
      if (contextFile) {
        const fd = new FormData();
        fd.append('file', contextFile);
        const uploadRes = await api.post('/session/upload_context', fd);
        contextFileUrl = uploadRes.data;
      }

      // 获取当前 CEFR 等级缩写 (例如 'B2')
      const targetLevel = level.split(' ')[0];

      const r = await api.post('/session/create', { 
        type: 'ai_chat', 
        mode: 'free_talk',
        topic: finalTopic,
        userLevel: targetLevel,
        contextFileUrl: contextFileUrl
      });
      setSession(r.data);
      setMessages([]);
    } catch (e) {
      console.error("Failed to create session:", e);
      alert("创建会话失败，请重试");
    } finally {
      setUploadingContext(false);
    }
  };

  // 发送文本消息
  const sendTextMessage = async () => {
    if (!inputText.trim() || !session || isSending) return;
    setIsSending(true);
    const text = inputText;
    setInputText('');
    setScaffoldingHints([]);

    // 先在本地插入用户的消息
    const userMsgTemp = {
      id: Date.now(),
      sender: 'user',
      content: text,
      createTime: new Date().toISOString()
    };
    setMessages(prev => [...prev, userMsgTemp]);

    try {
      const res = await api.post(`/session/${session.id}/chat`, {
        type: 'text',
        content: text
      });

      if (res.data) {
        setMessages(prev => [...prev, res.data]);
        // 自动播放 AI 音频 (若有)
        if (res.data.audioBase64) {
          playAudioFromBase64(res.data.audioBase64);
        }
      }
    } catch (e) {
      console.error("Send text failed", e);
      alert("消息发送失败，请重试");
    } finally {
      setIsSending(false);
    }
  };

  // 开始录音
  const startRecording = async () => {
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      mediaRecorderRef.current = new MediaRecorder(stream);
      audioChunksRef.current = [];

      mediaRecorderRef.current.ondataavailable = (e) => {
        if (e.data.size > 0) {
          audioChunksRef.current.push(e.data);
        }
      };

      mediaRecorderRef.current.onstop = async () => {
        const audioBlob = new Blob(audioChunksRef.current, { type: 'audio/wav' });
        sendVoiceMessage(audioBlob);
      };

      mediaRecorderRef.current.start();
      setIsRecording(true);
    } catch (err) {
      console.error("Failed to start recording:", err);
      alert("麦克风权限获取失败，请检查浏览器权限设置！");
    }
  };

  // 停止录音并触发上传
  const stopRecording = () => {
    if (mediaRecorderRef.current && isRecording) {
      mediaRecorderRef.current.stop();
      setIsRecording(false);
      // 停止麦克风流以释放设备
      mediaRecorderRef.current.stream.getTracks().forEach(track => track.stop());
    }
  };

  // 发送语音消息
  const sendVoiceMessage = async (blob) => {
    if (!session || isSending) return;
    setIsSending(true);

    // 临时占位，等待 STT 识别结果
    const userMsgTemp = {
      id: Date.now() + 1,
      sender: 'user',
      content: '[语音转写中...]',
      createTime: new Date().toISOString()
    };
    setMessages(prev => [...prev, userMsgTemp]);

    const reader = new FileReader();
    reader.readAsDataURL(blob);
    reader.onloadend = async () => {
      const base64data = reader.result.split(',')[1];
      try {
        const res = await api.post(`/session/${session.id}/chat`, {
          type: 'audio',
          audioBase64: base64data
        });

        if (res.data) {
          // 移除占位符并替换为最终带有真实 userTranscript 文本以及 AI 回复的消息
          setMessages(prev => {
            const filtered = prev.filter(m => m.content !== '[语音转写中...]');
            const userSpeech = {
              id: Date.now(),
              sender: 'user',
              content: res.data.userTranscript || '[识别失败]',
              createTime: new Date().toISOString()
            };
            return [...filtered, userSpeech, res.data];
          });

          // 自动播放 AI 音频
          if (res.data.audioBase64) {
            playAudioFromBase64(res.data.audioBase64);
          }
        }
      } catch (e) {
        console.error("Send voice failed", e);
        setMessages(prev => prev.filter(m => m.content !== '[语音转写中...]'));
        alert("语音发送失败，请重试");
      } finally {
        setIsSending(false);
      }
    };
  };

  // 播音逻辑
  const playAudioFromBase64 = (base64) => {
    const audioSrc = `data:audio/wav;base64,${base64}`;
    const audio = new Audio(audioSrc);
    audio.playbackRate = playbackSpeed;
    audio.play().catch(e => console.warn("Auto-play blocked by browser. User interaction required.", e));
  };

  const playAudioFromUrl = (url) => {
    // 带有 /st-recordings 代理前缀
    const audio = new Audio(url);
    audio.playbackRate = playbackSpeed;
    audio.play().catch(e => console.error("Play url audio failed", e));
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
      {/* 顶栏控制面板 */}
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
              {session ? `等级: ${session.userLevel || 'B2'} | 实时纠错与精美语速调控已就绪` : '设定您专属的智能口语外教'}
            </p>
          </div>
        </div>

        {session && (
          <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
            {/* 语速控制器 */}
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px', fontSize: '13px', color: '#4b5563' }}>
              <Volume2 size={16} />
              <span>语速:</span>
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
                <option value="0.8">0.8x (慢速)</option>
                <option value="1.0">1.0x (正常)</option>
                <option value="1.2">1.2x (稍快)</option>
                <option value="1.5">1.5x (快速)</option>
              </select>
            </div>

            <button
              onClick={async () => {
                if(confirm("确定要结束本轮对话吗？系统将自动保存您的练习历史。")) {
                  await api.post(`/session/${session.id}/complete`);
                  setSession(null);
                  setMessages([]);
                }
              }}
              style={{
                padding: '8px 16px',
                backgroundColor: '#fef2f2',
                color: '#ef4444',
                border: '1px solid #fee2e2',
                borderRadius: '8px',
                fontSize: '13px',
                fontWeight: 600,
                cursor: 'pointer',
                transition: 'all 0.2s'
              }}
            >
              结束对话
            </button>
          </div>
        )}
      </div>

      {/* 主体交互区域 */}
      <div style={{ display: 'flex', flex: 1, overflow: 'hidden' }}>
        {/* 未创建会话：侧边设定表单 */}
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
              <Sparkles color="#6C3FF5" /> 配置您的英语对话场景
            </h2>

            <div style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
              {/* 预设场景 */}
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
                  {PRESET_ROLES.map(role => (
                    <option key={role} value={role}>{role}</option>
                  ))}
                </select>
              </div>

              {/* 自定义角色描述 */}
              {roleType === '自定义' && (
                <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
                  <label style={{ fontSize: '14px', fontWeight: 600, color: '#374151' }}>自定义 AI 设定</label>
                  <textarea
                    placeholder="输入更具体的指令。例如：你是一个星巴克店员，语气热情友好，等待我来点咖啡。"
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

              {/* 词汇与难度 CEFR 等级 */}
              <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
                <label style={{ fontSize: '14px', fontWeight: 600, color: '#374151' }}>难度等级设定 (CEFR)</label>
                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: '8px' }}>
                  {CEFR_LEVELS.map(lvl => {
                    const code = lvl.split(' ')[0];
                    const active = level.startsWith(code);
                    return (
                      <button
                        key={lvl}
                        onClick={() => setLevel(lvl)}
                        style={{
                          height: '40px',
                          backgroundColor: active ? '#6C3FF5' : '#ffffff',
                          color: active ? '#ffffff' : '#374151',
                          border: `1px solid ${active ? '#6C3FF5' : '#d1d5db'}`,
                          borderRadius: '8px',
                          fontSize: '13px',
                          fontWeight: 500,
                          cursor: 'pointer',
                          transition: 'all 0.2s'
                        }}
                      >
                        {lvl}
                      </button>
                    );
                  })}
                </div>
              </div>

              {/* 背景材料上传 */}
              <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
                <label style={{ fontSize: '14px', fontWeight: 600, color: '#374151' }}>场景背景材料（选填，支持 PDF, DOCX, TXT）</label>
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
                    onChange={e => setContextFile(e.target.files[0])}
                    style={{
                      position: 'absolute',
                      inset: 0,
                      opacity: 0,
                      cursor: 'pointer'
                    }}
                  />
                  <p style={{ margin: 0, fontSize: '13px', color: '#4b5563' }}>
                    {contextFile ? `已选择: ${contextFile.name}` : '点击或拖拽文件到这里上传'}
                  </p>
                  <p style={{ margin: '4px 0 0', fontSize: '11px', color: '#9ca3af' }}>AI 将自动提取材料内容作为对练的讨论背景</p>
                </div>
              </div>

              {/* 启动按钮 */}
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
                  transition: 'background-color 0.2s',
                  marginTop: '12px'
                }}
              >
                {uploadingContext ? '建立会话中...' : '开始口语练习'}
              </button>
            </div>
          </div>
        ) : (
          /* 已创建会话：左右排版聊天室 */
          <>
            {/* 左边信息栏与提示面板 */}
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
                  <div>难度等级: <strong style={{ color: '#111827' }}>{session.userLevel || 'B2'}</strong></div>
                  <div>模式: <strong style={{ color: '#111827' }}>自由对练</strong></div>
                </div>
              </div>

              <hr style={{ border: 0, borderTop: '1px solid #f3f4f6', margin: 0 }} />

              <div style={{ flex: 1, display: 'flex', flexDirection: 'column' }}>
                <h4 style={{ margin: '0 0 10px', fontSize: '13px', textTransform: 'uppercase', color: '#9ca3af', fontWeight: 600 }}>智能表达助手</h4>
                <p style={{ fontSize: '12px', color: '#6b7280', margin: '0 0 12px' }}>如果您在打字过程中遇到瓶颈，输入前半句，我们将为您实时联想地道的英语句式。</p>
                
                {/* 句式引导展示 */}
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
                    scaffoldingHints.map((hint, idx) => (
                      <div
                        key={idx}
                        onClick={() => {
                          setInputText(prev => prev + ' ' + hint);
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
                          transition: 'all 0.2s',
                          lineHeight: '1.4'
                        }}
                        onMouseEnter={(e) => e.target.style.backgroundColor = '#ede9fe'}
                        onMouseLeave={(e) => e.target.style.backgroundColor = '#f5f3ff'}
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
                      输入英文以触发实时句式引导提示
                    </div>
                  )}
                </div>
              </div>
            </div>

            {/* 右边消息区与输入区 */}
            <div style={{ display: 'flex', flexDirection: 'column', flex: 1, backgroundColor: '#f9fafb' }}>
              {/* 消息滚动区 */}
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
                    会话已开启。请在下方点击麦克风发送语音，或直接打字开始对话吧！
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
                        {/* 气泡本体 */}
                        <div style={{ display: 'flex', gap: '10px', maxWidth: '75%', flexDirection: isUser ? 'row-reverse' : 'row' }}>
                          {/* 头像 */}
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
                              boxShadow: '0 1px 3px rgba(0,0,0,0.05)',
                              position: 'relative'
                            }}>
                              {msg.content}
                              
                              {/* 音频播放小组件 */}
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

                            {/* 纠错和推荐 (只有 AI 的消息有) */}
                            {!isUser && msg.refinedContent && (
                              <div style={{
                                marginTop: '6px',
                                padding: '10px 12px',
                                backgroundColor: '#f0fdf4',
                                border: '1px solid #bbf7d0',
                                borderRadius: '8px',
                                fontSize: '12px',
                                color: '#166534',
                                display: 'flex',
                                flexDirection: 'column',
                                gap: '4px',
                                boxShadow: '0 1px 2px rgba(0,0,0,0.02)'
                              }}>
                                <span style={{ fontWeight: 'bold', display: 'flex', alignItems: 'center', gap: '4px' }}>
                                  💡 推荐更地道的表达:
                                </span>
                                <span>{msg.refinedContent}</span>
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
                                display: 'flex',
                                flexDirection: 'column',
                                gap: '4px',
                                boxShadow: '0 1px 2px rgba(0,0,0,0.02)'
                              }}>
                                <span style={{ fontWeight: 'bold', display: 'flex', alignItems: 'center', gap: '4px' }}>
                                  ⚠️ 语法与中式表达提醒:
                                </span>
                                <span style={{ whiteSpace: 'pre-wrap' }}>{msg.chinglishFeedback}</span>
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

              {/* 输入底栏 */}
              <div style={{
                padding: '16px 24px',
                backgroundColor: '#ffffff',
                borderTop: '1px solid #e5e7eb'
              }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
                  {/* 输入模式切换 */}
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
                      justifyContent: 'center',
                      transition: 'all 0.2s'
                    }}
                    title={chatMode === 'text' ? "切换到语音输入" : "切换到文字打字"}
                  >
                    {chatMode === 'text' ? <Mic size={18} /> : <Settings size={18} />}
                  </button>

                  {chatMode === 'text' ? (
                    /* 文本输入框模式 */
                    <div style={{ display: 'flex', flex: 1, gap: '10px', position: 'relative' }}>
                      <input
                        type="text"
                        placeholder="打字开始交谈..."
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
                          justifyContent: 'center',
                          transition: 'all 0.2s'
                        }}
                      >
                        <Send size={16} />
                      </button>
                    </div>
                  ) : (
                    /* 语音输入按钮模式 */
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
                            fontWeight: 600,
                            boxShadow: '0 4px 12px rgba(239, 68, 68, 0.2)',
                            animation: 'pulse 1.5s infinite'
                          }}
                        >
                          <StopCircle size={18} />
                          <span>正在录音 — 点击结束并发送</span>
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
                            fontWeight: 600,
                            boxShadow: isSending ? 'none' : '0 4px 12px rgba(108, 63, 245, 0.2)',
                            transition: 'all 0.2s'
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

      {/* 注入录音脉动的 CSS 动画 */}
      <style>{`
        @keyframes pulse {
          0% { transform: scale(1); }
          50% { transform: scale(1.03); box-shadow: 0 4px 20px rgba(239, 68, 68, 0.4); }
          100% { transform: scale(1); }
        }
      `}</style>
    </div>
  );
}
