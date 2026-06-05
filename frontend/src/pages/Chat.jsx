import { useState } from 'react';
import api from '../api/client';

export default function Chat() {
  const [session, setSession] = useState(null);
  const [messages, setMessages] = useState([]);
  const [audioFile, setAudioFile] = useState(null);
  const [targetText, setTargetText] = useState('');
  const [score, setScore] = useState(null);

  const createSession = async () => {
    const r = await api.post('/session/create', { type: 'ai_chat', mode: 'free_talk' });
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
        <button className="btn" onClick={createSession}>开始新对话</button>
      ) : (
        <div className="card">
          <p>会话 ID: {session.id} | 模式: {session.mode} | 状态: {session.status === 0 ? '进行中' : '已完成'}</p>
          <h3>🎙 提交录音</h3>
          <input placeholder="目标文本（跟读模式）" value={targetText} onChange={e => setTargetText(e.target.value)} />
          <input type="file" accept="audio/*" onChange={e => setAudioFile(e.target.files[0])} />
          <button className="btn btn-sm" onClick={submitAudio} style={{ marginTop: 8 }}>提交评分</button>
          {score && <p style={{ marginTop: 8, fontSize: 18, color: '#4f46e5' }}>综合评分: <strong>{score}</strong> / 100</p>}
          <button className="btn btn-sm btn-red" onClick={async () => { await api.post(`/session/${session.id}/complete`); setSession({...session, status: 1}); }} style={{ marginLeft: 8, marginTop: 8 }}>
            结束会话
          </button>
        </div>
      )}
    </div>
  );
}
