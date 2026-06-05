import { useState, useEffect } from 'react';
import api from '../api/client';

export default function Dashboard() {
  const [profile, setProfile] = useState(null);
  const [calendar, setCalendar] = useState([]);
  const [cards, setCards] = useState([]);
  const [rank, setRank] = useState([]);
  const [signMsg, setSignMsg] = useState('');

  useEffect(() => {
    api.get('/user/me').then(r => setProfile(r.data));
    const now = new Date();
    api.get(`/signin/calendar?year=${now.getFullYear()}&month=${now.getMonth() + 1}`).then(r => setCalendar(r.data || []));
    api.get('/card/due').then(r => setCards(r.data || []));
    api.get('/rank/points?top=10').then(r => setRank(r.data || []));
  }, []);

  const signIn = async () => {
    try {
      const r = await api.post('/signin');
      setSignMsg(`签到成功！+${r.data.pointsRewarded} 积分`);
      setProfile(p => ({ ...p, points: r.data.totalPoints }));
    } catch (err) { setSignMsg(err?.message || '签到失败'); }
  };

  if (!profile) return <p>Loading...</p>;

  return (
    <div>
      <h2>👋 欢迎, {profile.nickname}</h2>
      <div className="grid cols-2">
        <div className="card">
          <h3>📅 每日签到</h3>
          <p>积分: <strong>{profile.points}</strong> | 累计: <strong>{profile.signInCount}</strong> 天</p>
          <button className="btn" onClick={signIn}>签到</button>
          {signMsg && <p style={{ marginTop: 8, color: '#059669' }}>{signMsg}</p>}
          <div style={{ marginTop: 12, display: 'flex', flexWrap: 'wrap', gap: 4 }}>
            {calendar.map(d => (
              <span key={d.day} className="tag" style={{ background: d.signed ? '#4f46e5' : '#e5e7eb', color: d.signed ? '#fff' : '#9ca3af' }}>
                {d.day}
              </span>
            ))}
          </div>
        </div>

        <div className="card">
          <h3>🧠 SRS 待复习</h3>
          {cards.length === 0 ? <p>暂无待复习卡片 🎉</p> : (
            <ul>{cards.map(c => <li key={c.id}>{c.word} — <span className="tag">{c.phrase || '无例句'}</span></li>)}</ul>
          )}
        </div>
      </div>

      <div className="card">
        <h3>🏆 积分排行榜</h3>
        <table style={{ width: '100%', borderCollapse: 'collapse' }}>
          <thead><tr style={{ borderBottom: '1px solid #e5e7eb' }}><th style={{ padding: 8, textAlign: 'left' }}>#</th><th>用户</th><th>积分</th></tr></thead>
          <tbody>
            {rank.map(r => (
              <tr key={r.userId} style={{ borderBottom: '1px solid #f3f4f6' }}>
                <td style={{ padding: 8 }}>{r.rank}</td>
                <td>{r.nickname}</td>
                <td>{r.points}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
