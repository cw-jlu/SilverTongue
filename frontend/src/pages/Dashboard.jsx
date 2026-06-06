import { useState, useEffect } from 'react';
import api from '../api/client';

/** 根据练习秒数返回热力图色阶 */
function heatColor(seconds) {
  if (seconds <= 0) return '#ebedf0';
  if (seconds < 900) return '#9be9a8';       // < 15 min
  if (seconds < 1800) return '#40c463';      // 15-30 min
  if (seconds < 3600) return '#30a14e';      // 30-60 min
  return '#216e39';                           // ≥ 60 min
}

/** 格式化秒数为可读字符串 */
function fmtDuration(seconds) {
  if (!seconds || seconds <= 0) return '0 min';
  const h = Math.floor(seconds / 3600);
  const m = Math.floor((seconds % 3600) / 60);
  if (h > 0) return `${h}h ${m}m`;
  return `${m} min`;
}

/** 格式化小时数 */
function fmtHours(h) {
  if (h < 1) return `${Math.round(h * 60)} min`;
  return `${h.toFixed(1)} h`;
}

export default function Dashboard() {
  const [profile, setProfile] = useState(null);
  const [calendar, setCalendar] = useState([]);
  const [cards, setCards] = useState([]);
  const [rank, setRank] = useState([]);
  const [signMsg, setSignMsg] = useState('');

  // 模块四新增：仪表盘数据
  const [progress, setProgress] = useState(null);
  const [heatmap, setHeatmap] = useState([]);
  const [hmYear, setHmYear] = useState(new Date().getFullYear());
  const [hmMonth, setHmMonth] = useState(new Date().getMonth() + 1);

  useEffect(() => {
    api.get('/user/me').then(r => setProfile(r.data));
    const now = new Date();
    api.get(`/signin/calendar?year=${now.getFullYear()}&month=${now.getMonth() + 1}`).then(r => setCalendar(r.data || []));
    api.get('/card/due').then(r => setCards(r.data || []));
    api.get('/rank/points?top=10').then(r => setRank(r.data || []));
    // 模块四新增
    api.get('/dashboard/progress').then(r => setProgress(r.data));
    api.get(`/dashboard/heatmap?year=${now.getFullYear()}&month=${now.getMonth() + 1}`).then(r => setHeatmap(r.data || []));
  }, []);

  /** 切换热力图月份 */
  const loadHeatmap = (year, month) => {
    setHmYear(year);
    setHmMonth(month);
    api.get(`/dashboard/heatmap?year=${year}&month=${month}`).then(r => setHeatmap(r.data || []));
  };

  const signIn = async () => {
    try {
      const r = await api.post('/signin');
      setSignMsg(`签到成功！+${r.data.pointsRewarded} 积分`);
      setProfile(p => ({ ...p, points: r.data.totalPoints }));
    } catch (err) { setSignMsg(err?.message || '签到失败'); }
  };

  if (!profile) return <p>Loading...</p>;

  // 热力图按周分组（模仿 GitHub 贡献图）
  const firstDay = new Date(hmYear, hmMonth - 1, 1).getDay(); // 0=Sun
  const weeks = [];
  let dayIdx = 0;
  const totalDays = heatmap.length;
  // 填充首周空白
  const firstWeek = [];
  for (let i = 0; i < firstDay; i++) {
    firstWeek.push(null);
    dayIdx++;
  }
  for (let d = 1; d <= totalDays; d++) {
    firstWeek.push(heatmap[d - 1]);
    if (firstWeek.length === 7) {
      weeks.push(firstWeek.splice(0, 7));
    }
  }
  if (firstWeek.length > 0) {
    while (firstWeek.length < 7) firstWeek.push(null);
    weeks.push(firstWeek);
  }

  return (
    <div>
      <h2>👋 欢迎, {profile.nickname}</h2>

      {/* ===== 1000 小时进度条 ===== */}
      {progress && (
        <div className="card" style={{ marginBottom: 16 }}>
          <h3>🔥 1000 小时听力/口语训练</h3>
          <div style={{ display: 'flex', alignItems: 'baseline', gap: 12, marginBottom: 8 }}>
            <span style={{ fontSize: 28, fontWeight: 700, color: '#4f46e5' }}>
              {fmtHours(progress.totalHours)}
            </span>
            <span style={{ color: '#6b7280' }}>/ 1000 h</span>
            <span style={{ marginLeft: 'auto', fontSize: 14, fontWeight: 600, color: '#059669' }}>
              {progress.percentTo1000.toFixed(1)}%
            </span>
          </div>
          {/* 进度条 */}
          <div style={{ height: 12, background: '#e5e7eb', borderRadius: 6, overflow: 'hidden', marginBottom: 12 }}>
            <div style={{
              height: '100%', width: `${Math.min(progress.percentTo1000, 100)}%`,
              background: 'linear-gradient(90deg, #4f46e5, #7c3aed)', borderRadius: 6,
              transition: 'width 0.5s ease'
            }} />
          </div>
          {/* 输入 / 输出 分解 */}
          <div style={{ display: 'flex', gap: 24, fontSize: 14 }}>
            <div>
              <span style={{ color: '#6b7280' }}>🎧 输入 (听/读) </span>
              <strong style={{ color: '#0891b2' }}>{fmtHours(progress.inputHours)}</strong>
            </div>
            <div>
              <span style={{ color: '#6b7280' }}>🗣️ 输出 (说/写) </span>
              <strong style={{ color: '#059669' }}>{fmtHours(progress.outputHours)}</strong>
            </div>
            <div style={{ marginLeft: 'auto' }}>
              <span style={{ color: '#6b7280' }}>剩余 </span>
              <strong style={{ color: '#dc2626' }}>{fmtHours(progress.remainingHours)}</strong>
            </div>
          </div>
          {/* SRS 卡片概览 */}
          <div style={{ marginTop: 12, paddingTop: 12, borderTop: '1px solid #f3f4f6', fontSize: 13, color: '#6b7280' }}>
            📇 SRS 卡片: <strong>{progress.dueCardsCount}</strong> 待复习 / <strong>{progress.totalCardsCount}</strong> 总计
            &nbsp;|&nbsp; 📅 累计签到: <strong>{progress.signInCount}</strong> 天
          </div>
        </div>
      )}

      {/* ===== 热力图 + 签到 ===== */}
      <div className="grid cols-2">
        {/* 月度热力图 */}
        <div className="card">
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }}>
            <h3 style={{ margin: 0 }}>📊 练习热力图</h3>
            <div style={{ display: 'flex', gap: 4, alignItems: 'center' }}>
              <button className="btn-sm"
                style={{ padding: '2px 8px', background: '#e5e7eb', color: '#374151', border: 'none', borderRadius: 4, cursor: 'pointer' }}
                onClick={() => loadHeatmap(hmMonth === 1 ? hmYear - 1 : hmYear, hmMonth === 1 ? 12 : hmMonth - 1)}>
                ◀
              </button>
              <span style={{ fontSize: 13, fontWeight: 600 }}>{hmYear}-{String(hmMonth).padStart(2, '0')}</span>
              <button className="btn-sm"
                style={{ padding: '2px 8px', background: '#e5e7eb', color: '#374151', border: 'none', borderRadius: 4, cursor: 'pointer' }}
                onClick={() => loadHeatmap(hmMonth === 12 ? hmYear + 1 : hmYear, hmMonth === 12 ? 1 : hmMonth + 1)}>
                ▶
              </button>
            </div>
          </div>
          {/* 周标签 */}
          <div style={{ display: 'flex', gap: 3, marginBottom: 4 }}>
            {['日', '一', '二', '三', '四', '五', '六'].map(d => (
              <span key={d} style={{ width: 28, textAlign: 'center', fontSize: 10, color: '#9ca3af' }}>{d}</span>
            ))}
          </div>
          {/* 热力图网格 */}
          <div style={{ display: 'flex', flexDirection: 'column', gap: 3 }}>
            {weeks.map((week, wi) => (
              <div key={wi} style={{ display: 'flex', gap: 3 }}>
                {week.map((day, di) => (
                  <div key={di} style={{
                    width: 28, height: 28, borderRadius: 4,
                    background: day ? heatColor(day.totalSeconds) : '#f9fafb',
                    border: '1px solid #f3f4f6',
                    display: 'flex', alignItems: 'center', justifyContent: 'center',
                    fontSize: 9, color: day && day.totalSeconds > 0 ? '#fff' : '#d1d5db',
                    cursor: day ? 'pointer' : 'default',
                    position: 'relative'
                  }}
                  title={day ? `${day.day}日: ${fmtDuration(day.totalSeconds)} (🎧${fmtDuration(day.inputSeconds)} / 🗣️${fmtDuration(day.outputSeconds)})` : ''}
                  >
                    {day ? day.day : ''}
                  </div>
                ))}
              </div>
            ))}
          </div>
          {/* 图例 */}
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginTop: 8, fontSize: 11, color: '#9ca3af' }}>
            <span>少</span>
            {[0, 1, 900, 1800, 3600].map(s => (
              <div key={s} style={{ width: 14, height: 14, borderRadius: 3, background: heatColor(s), border: '1px solid #f3f4f6' }} />
            ))}
            <span>多</span>
            <span style={{ marginLeft: 'auto' }}>
              本月合计: <strong style={{ color: '#4f46e5' }}>{fmtDuration(heatmap.reduce((s, d) => s + d.totalSeconds, 0))}</strong>
            </span>
          </div>
        </div>

        {/* 每日签到 */}
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
      </div>

      {/* ===== SRS 待复习 + 积分排行榜 ===== */}
      <div className="grid cols-2" style={{ marginTop: 16 }}>
        <div className="card">
          <h3>🧠 SRS 待复习</h3>
          {cards.length === 0 ? <p>暂无待复习卡片 🎉</p> : (
            <ul style={{ paddingLeft: 20, margin: 0 }}>{cards.map(c => <li key={c.id} style={{ marginBottom: 4 }}>{c.word} — <span className="tag">{c.phrase || '无例句'}</span></li>)}</ul>
          )}
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
    </div>
  );
}
