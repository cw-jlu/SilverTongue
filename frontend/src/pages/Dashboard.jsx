import { useEffect, useState } from 'react';
import { Award, BookOpen, Calendar, CheckCircle2, ChevronLeft, ChevronRight, Clock, Flame, TrendingUp, User } from 'lucide-react';
import api from '../api/client';

function heatColor(seconds) {
  if (seconds <= 0) return '#f3f4f6';
  if (seconds < 900) return '#ddd6fe';
  if (seconds < 1800) return '#c084fc';
  if (seconds < 3600) return '#a855f7';
  return '#6c3ff5';
}

function fmtDuration(seconds) {
  if (!seconds || seconds <= 0) return '0 min';
  const h = Math.floor(seconds / 3600);
  const m = Math.floor((seconds % 3600) / 60);
  if (h > 0) return `${h}h ${m}m`;
  return `${m}m`;
}

function fmtHours(hours) {
  if (hours < 1) return `${Math.round(hours * 60)} min`;
  return `${hours.toFixed(1)} h`;
}

function monthWeeks(heatmap, year, month) {
  const firstDay = new Date(year, month - 1, 1).getDay();
  const rows = [];
  const current = [];

  for (let i = 0; i < firstDay; i += 1) {
    current.push(null);
  }

  heatmap.forEach((day) => {
    current.push(day);
    if (current.length === 7) {
      rows.push([...current]);
      current.length = 0;
    }
  });

  if (current.length > 0) {
    while (current.length < 7) {
      current.push(null);
    }
    rows.push([...current]);
  }

  return rows;
}

export default function Dashboard() {
  const [profile, setProfile] = useState(null);
  const [calendar, setCalendar] = useState([]);
  const [cards, setCards] = useState([]);
  const [rank, setRank] = useState([]);
  const [progress, setProgress] = useState(null);
  const [heatmap, setHeatmap] = useState([]);
  const [signMsg, setSignMsg] = useState('');
  const [signing, setSigning] = useState(false);
  const [hmYear, setHmYear] = useState(new Date().getFullYear());
  const [hmMonth, setHmMonth] = useState(new Date().getMonth() + 1);

  const loadOverview = async (year = hmYear, month = hmMonth) => {
    const [user, calendarRes, cardsRes, rankRes, progressRes, heatmapRes] = await Promise.all([
      api.get('/user/me'),
      api.get(`/signin/calendar?year=${year}&month=${month}`),
      api.get('/card/due'),
      api.get('/rank/points?top=10'),
      api.get('/dashboard/progress'),
      api.get(`/dashboard/heatmap?year=${year}&month=${month}`),
    ]);

    setProfile(user);
    setCalendar(calendarRes || []);
    setCards(cardsRes || []);
    setRank(rankRes || []);
    setProgress(progressRes);
    setHeatmap(heatmapRes || []);
  };

  useEffect(() => {
    loadOverview().catch((error) => console.error('Failed to load dashboard', error));
  }, []);

  const loadHeatmap = async (year, month) => {
    setHmYear(year);
    setHmMonth(month);
    try {
      const res = await api.get(`/dashboard/heatmap?year=${year}&month=${month}`);
      setHeatmap(res || []);
    } catch (error) {
      console.error('Failed to load heatmap', error);
    }
  };

  const signIn = async () => {
    if (signing) {
      return;
    }

    setSigning(true);
    try {
      const result = await api.post('/signin');
      setSignMsg(`签到成功，获得 ${result.pointsRewarded} 积分`);
      setProfile((prev) => (
        prev
          ? { ...prev, points: result.totalPoints, signInCount: (prev.signInCount || 0) + 1 }
          : prev
      ));

      const [calendarRes, progressRes] = await Promise.all([
        api.get(`/signin/calendar?year=${hmYear}&month=${hmMonth}`),
        api.get('/dashboard/progress'),
      ]);

      setCalendar(calendarRes || []);
      setProgress(progressRes);
    } catch (error) {
      setSignMsg(error?.message || '签到失败或今天已签到');
    } finally {
      setSigning(false);
    }
  };

  if (!profile) {
    return (
      <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '60vh', color: '#6c3ff5' }}>
        <p style={{ fontSize: '16px', fontWeight: 600 }}>正在加载你的学习仪表盘...</p>
      </div>
    );
  }

  const weeks = monthWeeks(heatmap, hmYear, hmMonth);
  const monthlyTotal = heatmap.reduce((sum, day) => sum + (day?.totalSeconds || 0), 0);

  return (
    <div style={{ maxWidth: '1200px', margin: '0 auto', padding: '24px 16px', fontFamily: "-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif" }}>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '28px', backgroundColor: '#ffffff', padding: '20px 24px', borderRadius: '16px', border: '1px solid #e5e7eb', boxShadow: '0 4px 6px -1px rgba(0,0,0,0.02)' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
          <div style={{ width: '48px', height: '48px', borderRadius: '50%', backgroundColor: '#f5f3ff', display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#6c3ff5' }}>
            <User size={24} />
          </div>
          <div>
            <h2 style={{ margin: '0 0 4px', fontSize: '22px', fontWeight: 'bold', color: '#111827' }}>
              你好，{profile.nickname || profile.username}
            </h2>
            <p style={{ margin: 0, fontSize: '13px', color: '#6b7280' }}>今天也继续把英语练习推进一点点。</p>
          </div>
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: '8px', padding: '8px 16px', backgroundColor: '#f5f3ff', borderRadius: '20px', fontSize: '14px', color: '#6c3ff5', fontWeight: 600 }}>
          <Flame size={16} />
          <span>累计签到 {profile.signInCount || 0} 天</span>
        </div>
      </div>

      {progress && (
        <div style={{ backgroundColor: '#ffffff', borderRadius: '16px', padding: '24px', border: '1px solid #e5e7eb', boxShadow: '0 4px 6px -1px rgba(0,0,0,0.02)', marginBottom: '24px' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px' }}>
            <h3 style={{ margin: 0, fontSize: '16px', fontWeight: 'bold', color: '#111827', display: 'flex', alignItems: 'center', gap: '8px' }}>
              <Clock size={18} color="#6C3FF5" />
              <span>1000 小时训练计划进度</span>
            </h3>
            <span style={{ fontSize: '15px', fontWeight: 'bold', color: '#10b981' }}>{progress.percentTo1000.toFixed(1)}%</span>
          </div>

          <div style={{ display: 'flex', alignItems: 'baseline', gap: '8px', marginBottom: '12px' }}>
            <span style={{ fontSize: '32px', fontWeight: 800, color: '#6c3ff5' }}>{fmtHours(progress.totalHours)}</span>
            <span style={{ color: '#9ca3af', fontSize: '14px' }}>/ 1000 h</span>
          </div>

          <div style={{ height: '14px', backgroundColor: '#f3f4f6', borderRadius: '7px', overflow: 'hidden', marginBottom: '18px' }}>
            <div style={{ height: '100%', width: `${Math.min(progress.percentTo1000, 100)}%`, background: 'linear-gradient(90deg, #6c3ff5 0%, #a855f7 100%)', borderRadius: '7px' }} />
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: '16px', fontSize: '14px' }}>
            <div>
              <div style={{ color: '#9ca3af' }}>听力输入</div>
              <strong style={{ color: '#0ea5e9', fontSize: '16px' }}>{fmtHours(progress.inputHours)}</strong>
            </div>
            <div>
              <div style={{ color: '#9ca3af' }}>口语输出</div>
              <strong style={{ color: '#10b981', fontSize: '16px' }}>{fmtHours(progress.outputHours)}</strong>
            </div>
            <div>
              <div style={{ color: '#9ca3af' }}>剩余目标</div>
              <strong style={{ color: '#ef4444', fontSize: '16px' }}>{fmtHours(progress.remainingHours)}</strong>
            </div>
          </div>

          <div style={{ marginTop: '16px', paddingTop: '16px', borderTop: '1px solid #f3f4f6', fontSize: '13px', color: '#6b7280', display: 'flex', gap: '24px', flexWrap: 'wrap' }}>
            <div>SRS 待复习: <strong style={{ color: '#111827' }}>{progress.dueCardsCount}</strong> / {progress.totalCardsCount}</div>
            <div>本月签到: <strong style={{ color: '#111827' }}>{progress.signInCount}</strong> 次</div>
          </div>
        </div>
      )}

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(360px, 1fr))', gap: '24px', marginBottom: '24px' }}>
        <div style={{ backgroundColor: '#ffffff', borderRadius: '16px', padding: '24px', border: '1px solid #e5e7eb', boxShadow: '0 4px 6px -1px rgba(0,0,0,0.02)' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px' }}>
            <h3 style={{ margin: 0, fontSize: '16px', fontWeight: 'bold', color: '#111827', display: 'flex', alignItems: 'center', gap: '8px' }}>
              <TrendingUp size={18} color="#6C3FF5" />
              <span>每日练习热力图</span>
            </h3>
            <div style={{ display: 'flex', gap: '6px', alignItems: 'center' }}>
              <button onClick={() => loadHeatmap(hmMonth === 1 ? hmYear - 1 : hmYear, hmMonth === 1 ? 12 : hmMonth - 1)} style={{ border: '1px solid #d1d5db', background: '#ffffff', borderRadius: '6px', padding: '2px 6px', cursor: 'pointer' }}>
                <ChevronLeft size={14} />
              </button>
              <span style={{ fontSize: '13px', fontWeight: 600 }}>{hmYear}-{String(hmMonth).padStart(2, '0')}</span>
              <button onClick={() => loadHeatmap(hmMonth === 12 ? hmYear + 1 : hmYear, hmMonth === 12 ? 1 : hmMonth + 1)} style={{ border: '1px solid #d1d5db', background: '#ffffff', borderRadius: '6px', padding: '2px 6px', cursor: 'pointer' }}>
                <ChevronRight size={14} />
              </button>
            </div>
          </div>

          <div style={{ display: 'flex', gap: '4px', marginBottom: '8px' }}>
            {['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'].map((day) => (
              <span key={day} style={{ width: '32px', textAlign: 'center', fontSize: '11px', color: '#9ca3af', fontWeight: 600 }}>{day}</span>
            ))}
          </div>

          <div style={{ display: 'flex', flexDirection: 'column', gap: '4px' }}>
            {weeks.map((week, weekIndex) => (
              <div key={weekIndex} style={{ display: 'flex', gap: '4px' }}>
                {week.map((day, dayIndex) => (
                  <div
                    key={dayIndex}
                    style={{
                      width: '32px',
                      height: '32px',
                      borderRadius: '6px',
                      background: day ? heatColor(day.totalSeconds) : '#f9fafb',
                      border: '1px solid #f3f4f6',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      fontSize: '11px',
                      fontWeight: 600,
                      color: day && day.totalSeconds > 0 ? '#ffffff' : '#d1d5db'
                    }}
                    title={day ? `${day.day}: ${fmtDuration(day.totalSeconds)}` : ''}
                  >
                    {day ? day.day : ''}
                  </div>
                ))}
              </div>
            ))}
          </div>

          <div style={{ display: 'flex', alignItems: 'center', gap: '6px', marginTop: '16px', fontSize: '11px', color: '#9ca3af' }}>
            <span>Less</span>
            {[0, 1, 900, 1800, 3600].map((seconds) => (
              <div key={seconds} style={{ width: '12px', height: '12px', borderRadius: '3px', background: heatColor(seconds), border: '1px solid #f3f4f6' }} />
            ))}
            <span>More</span>
            <span style={{ marginLeft: 'auto' }}>
              本月合计: <strong style={{ color: '#6c3ff5' }}>{fmtDuration(monthlyTotal)}</strong>
            </span>
          </div>
        </div>

        <div style={{ backgroundColor: '#ffffff', borderRadius: '16px', padding: '24px', border: '1px solid #e5e7eb', boxShadow: '0 4px 6px -1px rgba(0,0,0,0.02)', display: 'flex', flexDirection: 'column', justifyContent: 'space-between' }}>
          <div>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px' }}>
              <h3 style={{ margin: 0, fontSize: '16px', fontWeight: 'bold', color: '#111827', display: 'flex', alignItems: 'center', gap: '8px' }}>
                <Calendar size={18} color="#6C3FF5" />
                <span>每日签到</span>
              </h3>
              <span style={{ fontSize: '13px', color: '#4b5563' }}>当前积分: <strong style={{ color: '#6c3ff5' }}>{profile.points}</strong></span>
            </div>

            <button onClick={signIn} disabled={signing} style={{ width: '100%', height: '42px', backgroundColor: '#6c3ff5', color: '#ffffff', border: 'none', borderRadius: '8px', fontWeight: 600, fontSize: '14px', cursor: 'pointer', marginBottom: '12px' }}>
              {signing ? '签到中...' : '每日签到'}
            </button>

            {signMsg && (
              <div style={{ padding: '8px 12px', backgroundColor: '#ecfdf5', border: '1px solid #a7f3d0', borderRadius: '6px', color: '#065f46', fontSize: '12px', marginBottom: '12px' }}>
                {signMsg}
              </div>
            )}
          </div>

          <div style={{ display: 'flex', flexWrap: 'wrap', gap: '6px', maxHeight: '120px', overflowY: 'auto' }}>
            {calendar.map((day) => (
              <span key={day.day} style={{ display: 'inline-block', padding: '4px 10px', borderRadius: '12px', fontSize: '11px', fontWeight: 600, backgroundColor: day.signed ? '#6c3ff5' : '#f3f4f6', color: day.signed ? '#ffffff' : '#9ca3af' }}>
                {day.day}
              </span>
            ))}
          </div>
        </div>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(360px, 1fr))', gap: '24px' }}>
        <div style={{ backgroundColor: '#ffffff', borderRadius: '16px', padding: '24px', border: '1px solid #e5e7eb', boxShadow: '0 4px 6px -1px rgba(0,0,0,0.02)' }}>
          <h3 style={{ margin: '0 0 16px', fontSize: '16px', fontWeight: 'bold', color: '#111827', display: 'flex', alignItems: 'center', gap: '8px' }}>
            <BookOpen size={18} color="#6C3FF5" />
            <span>SRS 待复习卡片</span>
          </h3>

          {cards.length === 0 ? (
            <div style={{ textAlign: 'center', padding: '32px', color: '#9ca3af', border: '1px dashed #e5e7eb', borderRadius: '8px' }}>
              <CheckCircle2 size={24} style={{ color: '#10b981', marginBottom: '8px' }} />
              <p style={{ margin: 0, fontSize: '13px' }}>当前没有待复习卡片。</p>
            </div>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '8px', maxHeight: '240px', overflowY: 'auto' }}>
              {cards.map((card) => (
                <div key={card.id} style={{ padding: '10px 14px', borderRadius: '8px', backgroundColor: '#f9fafb', border: '1px solid #f3f4f6', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <strong style={{ color: '#111827', fontSize: '14px' }}>{card.word}</strong>
                  <span style={{ fontSize: '11px', padding: '2px 8px', backgroundColor: '#e0e7ff', color: '#4f46e5', borderRadius: '12px' }}>
                    {card.phrase || '待复习'}
                  </span>
                </div>
              ))}
            </div>
          )}
        </div>

        <div style={{ backgroundColor: '#ffffff', borderRadius: '16px', padding: '24px', border: '1px solid #e5e7eb', boxShadow: '0 4px 6px -1px rgba(0,0,0,0.02)' }}>
          <h3 style={{ margin: '0 0 16px', fontSize: '16px', fontWeight: 'bold', color: '#111827', display: 'flex', alignItems: 'center', gap: '8px' }}>
            <Award size={18} color="#6C3FF5" />
            <span>社区积分排行榜</span>
          </h3>

          <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '13px' }}>
            <thead>
              <tr style={{ borderBottom: '2px solid #f3f4f6', color: '#9ca3af', fontWeight: 600 }}>
                <th style={{ padding: '8px', textAlign: 'left', width: '40px' }}>排名</th>
                <th style={{ padding: '8px', textAlign: 'left' }}>用户</th>
                <th style={{ padding: '8px', textAlign: 'right' }}>积分</th>
              </tr>
            </thead>
            <tbody>
              {rank.map((item, index) => (
                <tr key={item.userId} style={{ borderBottom: '1px solid #f3f4f6', backgroundColor: item.username === profile.username ? '#f5f3ff' : 'transparent', fontWeight: item.username === profile.username ? 600 : 500 }}>
                  <td style={{ padding: '10px 8px' }}>{index + 1}</td>
                  <td style={{ padding: '10px 8px', color: '#111827' }}>
                    {item.nickname || item.username}
                    {item.username === profile.username && <span style={{ marginLeft: '6px', fontSize: '10px', padding: '1px 5px', backgroundColor: '#6c3ff5', color: '#fff', borderRadius: '4px' }}>我</span>}
                  </td>
                  <td style={{ padding: '10px 8px', textAlign: 'right', color: '#6c3ff5', fontWeight: 'bold' }}>{item.points}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}
