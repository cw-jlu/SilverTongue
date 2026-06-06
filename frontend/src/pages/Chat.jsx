import { useState } from 'react';
import api from '../api/client';

export default function Chat() {
  const [session, setSession] = useState(null);
  const [messages, setMessages] = useState([]);
  const [audioFile, setAudioFile] = useState(null);
  const [targetText, setTargetText] = useState('');
  const [score, setScore] = useState(null);
  
  // 新增角色/场景相关状态
  const [roleType, setRoleType] = useState('日常闲聊');
  const [customRole, setCustomRole] = useState('');

  const PRESET_ROLES = ['日常闲聊', '雅思考官', '外企 HR 面试', '商务会议', '旅游向导', '餐厅点餐', '自定义'];

  const createSession = async () => {
    const finalTopic = roleType === '自定义' ? customRole : roleType;
    const r = await api.post('/session/create', { 
      type: 'ai_chat', 
      mode: 'free_talk',
      topic: finalTopic
    });
    setSession(r.data);
  };

  const submitAudio = async () => {
    if (!audioFile || !session) return;
    const fd = new FormData();
    fd.append('audio', audioFile);
    if (targetText) fd.append('targetText', targetText);
    const r = await api.post(`/session/${session.id}/recording`, fd);
    setMessages([...messages, { id: Date.now(), score: r.data.score }]);
    setScore(r.data.score);
    setAudioFile(null);
  };

  return (
    <div>
      <h2>🤖 AI 口语对练</h2>
      {!session ? (
        <div className="card">
          <h3>设定 AI 角色 / 练习场景</h3>
          <select 
            value={roleType} 
            onChange={(e) => setRoleType(e.target.value)}
            style={{ padding: '8px', marginBottom: '16px', display: 'block', width: '100%', maxWidth: '300px' }}
          >
            {PRESET_ROLES.map(role => (
              <option key={role} value={role}>{role}</option>
            ))}
          </select>
          
          {roleType === '自定义' && (
            <textarea
              placeholder="请输入自定义角色设定（例如：你是一个严厉的大学教授...）"
              value={customRole}
              onChange={(e) => setCustomRole(e.target.value)}
              rows={3}
              style={{ width: '100%', maxWidth: '400px', marginBottom: '16px', padding: '8px' }}
            />
          )}
          
          <button className="btn" onClick={createSession}>开始新对话</button>
        </div>
      ) : (
        <div className="card">
          <p>
            会话 ID: {session.id} | 模式: {session.mode} <br/>
            角色/场景: <strong style={{ color: '#4f46e5' }}>{session.topic}</strong> <br/>
            状态: {session.status === 0 ? '进行中' : '已完成'}
          </p>
          <hr style={{ margin: '16px 0' }} />
          <h3>🎙 提交录音</h3>
          <input placeholder="目标文本（跟读模式）" value={targetText} onChange={e => setTargetText(e.target.value)} style={{ marginRight: '8px' }} />
          <input type="file" accept="audio/*" onChange={e => setAudioFile(e.target.files[0])} />
          <button className="btn btn-sm" onClick={submitAudio} style={{ marginTop: 8 }}>提交评分</button>
          
          {score && <p style={{ marginTop: 8, fontSize: 18, color: '#4f46e5' }}>综合评分: <strong>{score}</strong> / 100</p>}
          
          <button className="btn btn-sm btn-red" onClick={async () => { await api.post(`/session/${session.id}/complete`); setSession({...session, status: 1}); }} style={{ marginTop: 16, display: 'block' }}>
            结束会话
          </button>
        </div>
      )}
    </div>
  );
}
