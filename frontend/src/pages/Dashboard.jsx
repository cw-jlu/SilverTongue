import { useState, useEffect } from 'react';
import { Award, Calendar, BookOpen, Flame, Clock, CheckCircle2, TrendingUp, Sparkles, ChevronLeft, ChevronRight, User } from 'lucide-react';
import api from '../api/client';

/** 根据练习秒数返回热力图色阶 */
function heatColor(seconds) {
  if (seconds <= 0) return '#f3f4f6';
  if (seconds < 900) return '#ddd6fe';       // < 15 min (浅紫)
  if (seconds < 1800) return '#c084fc';      // 15-30 min
  if (seconds < 3600) return '#a855f7';      // 30-60 min
  return '#6c3ff5';                           // ≥ 60 min (深紫)
}

/** 格式化秒数为可读字符串 */
function fmtDuration(seconds) {
  if (!seconds || seconds <= 0) return '0 min';
  const h = Math.floor(seconds / 3600);
  const m = Math.floor((seconds % 3600) / 60);
  if (h > 0) return `${h}小时${m}分钟`;
  return `${m}分钟`;
}

/** 格式化小时数 */
function fmtHours(h) {
  if (h < 1) return `${Math.round(h * 60)} 分钟`;
  return `${h.toFixed(1)} 小时`;
}

export default function Dashboard() {
  const [profile, setProfile] = useState(null);
  const [calendar, setCalendar] = useState([]);
  const [cards, setCards] = useState([]);
  const [rank, setRank] = useState([]);
  const [signMsg, setSignMsg] = useState('');

  // 仪表盘数据
  const [progress, setProgress] = useState(null);
  const [heatmap, setHeatmap] = useState([]);
  const [hmYear, setHmYear] = useState(new Date().getFullYear());
  const [hmMonth, setHmMonth] = useState(new Date().getMonth() + 1);
  const [signing, setSigning] = useState(false);

  useEffect(() => {
    api.get('/user/me').then(r => setProfile(r.data));
    const now = new Date();
    api.get(`/signin/calendar?year=${now.getFullYear()}&month=${now.getMonth() + 1}`).then(r => setCalendar(r.data || []));
    api.get('/card/due').then(r => setCards(r.data || []));
    api.get('/rank/points?top=10').then(r => setRank(r.data || []));
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
    if (signing) return;
    setSigning(true);
    try {
      const r = await api.post('/signin');
      setSignMsg(`签到成功！+${r.data.pointsRewarded} 积分`);
      setProfile(p => ({ ...p, points: r.data.totalPoints, signInCount: (p.signInCount || 0) + 1 }));
      // 重新拉取日历和进度
      const now = new Date();
      const calRes = await api.get(`/signin/calendar?year=${now.getFullYear()}&month=${now.getMonth() + 1}`);
      setCalendar(calRes.data || []);
      const progRes = await api.get('/dashboard/progress');
      setProgress(progRes.data);
    } catch (err) {
      setSignMsg(err?.message || '签到失败或今天已签到');
    } finally {
      setSigning(false);
    }
  };

  if (!profile) return (
    <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '60vh', color: '#6C3FF5' }}>
      <p style={{ fontSize: '16px', fontWeight: 600 }}>正在获取您的学习仪表盘...</p>
    </div>
  );

  // 热力图按周分组
  const firstDay = new Date(hmYear, hmMonth - 1, 1).getDay();
  const weeks = [];
  const totalDays = heatmap.length;
  const firstWeek = [];
  for (let i = 0; i < firstDay; i++) {
    firstWeek.push(null);
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
    <div style={{
      maxWidth: '1200px',
      margin: '0 auto',
      padding: '24px 16px',
      fontFamily: "-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif"
    }}>
      {/* 头部欢迎 */}
      <div style={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        marginBottom: '28px',
        backgroundColor: '#ffffff',
        padding: '20px 24px',
        borderRadius: '16px',
        border: '1px solid #e5e7eb',
        boxShadow: '0 4px 6px -1px rgba(0,0,0,0.02)'
      }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
          <div style={{
            width: '48px',
            height: '48px',
            borderRadius: '50%',
            backgroundColor: '#f5f3ff',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            color: '#6c3ff5'
          }}>
            <User size={24} />
          </div>
          <div>
            <h2 style={{ margin: '0 0 4px', fontSize: '22px', fontWeight: 'bold', color: '#111827' }}>
              你好, {profile.nickname || profile.username}!
            </h2>
            <p style={{ margin: 0, fontSize: '13px', color: '#6b7280' }}>今天也是沉浸在英语练习中美好的一天。</p>
          </div>
        </div>
        <div style={{
          display: 'flex',
          alignItems: 'center',
          gap: '8px',
          padding: '8px 16px',
          backgroundColor: '#f5f3ff',
          borderRadius: '20px',
          fontSize: '14px',
          color: '#6c3ff5',
          fontWeight: 600
        }}>
          <Flame size={16} />
          <span>累计学习: {profile.signInCount || 0} 天</span>
        </div>
      </div>

      {/* ===== 1000 小时训练进度条 ===== */}
      {progress && (
        <div style={{
          backgroundColor: '#ffffff',
          borderRadius: '16px',
          padding: '24px',
          border: '1px solid #e5e7eb',
          boxShadow: '0 4px 6px -1px rgba(0,0,0,0.02)',
          marginBottom: '24px'
        }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px' }}>
            <h3 style={{ margin: 0, fontSize: '16px', fontWeight: 'bold', color: '#111827', display: 'flex', alignItems: 'center', gap: '8px' }}>
              <Clock size={18} color="#6C3FF5" />
              <span>🔥 1000 小时口语/听力大师计划进度</span>
            </h3>
            <span style={{ fontSize: '12px', color: '#9ca3af' }}>成为口语大师的必经之路</span>
          </div>

          <div style={{ display: 'flex', alignItems: 'baseline', gap: '8px', marginBottom: '12px' }}>
            <span style={{ fontSize: '32px', fontWeight: 800, color: '#6c3ff5', letterSpacing: '-0.5px' }}>
              {fmtHours(progress.totalHours)}
            </span>
            <span style={{ color: '#9ca3af', fontSize: '14px' }}>/ 1000 小时</span>
            <span style={{ marginLeft: 'auto', fontSize: '15px', fontWeight: 'bold', color: '#10b981' }}>
              {progress.percentTo1000.toFixed(1)}% 完成
            </span>
          </div>

          {/* 漂亮的渐变进度条 */}
          <div style={{ height: '14px', backgroundColor: '#f3f4f6', borderRadius: '7px', overflow: 'hidden', marginBottom: '18px' }}>
            <div style={{
              height: '100%',
              width: `${Math.min(progress.percentTo1000, 100)}%`,
              background: 'linear-gradient(90deg, #6c3ff5 0%, #a855f7 100%)',
              borderRadius: '7px',
              transition: 'width 0.6s cubic-bezier(0.4, 0, 0.2, 1)'
            }} />
          </div>

          {/* 数据指标分类 */}
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: '16px', fontSize: '14px' }}>
            <div style={{ display: 'flex', flexDirection: 'column', gap: '4px' }}>
              <span style={{ color: '#9ca3af' }}>🎧 听力输入累计</span>
              <strong style={{ color: '#0ea5e9', fontSize: '16px' }}>{fmtHours(progress.inputHours)}</strong>
            </div>
            <div style={{ display: 'flex', flexDirection: 'column', gap: '4px' }}>
              <span style={{ color: '#9ca3af' }}>🗣️ 口语输出累计</span>
              <strong style={{ color: '#10b981', fontSize: '16px' }}>{fmtHours(progress.outputHours)}</strong>
            </div>
            <div style={{ display: 'flex', flexDirection: 'column', gap: '4px' }}>
              <span style={{ color: '#9ca3af' }}>⏳ 距离大师级还差</span>
              <strong style={{ color: '#ef4444', fontSize: '16px' }}>{fmtHours(progress.remainingHours)}</strong>
            </div>
          </div>

          {/* 卡片概要 */}
          <div style={{
            marginTop: '16px',
            paddingTop: '16px',
            borderTop: '1px solid #f3f4f6',
            fontSize: '13px',
            color: '#6b7280',
            display: 'flex',
            gap: '24px'
          }}>
            <div>📇 SRS 复习进度: <strong style={{ color: '#111827' }}>{progress.dueCardsCount}</strong> 待复习 / <strong style={{ color: '#111827' }}>{progress.totalCardsCount}</strong> 总词库</div>
            <div>📅 本月签到: <strong style={{ color: '#111827' }}>{progress.signInCount}</strong> 次</div>
          </div>
        </div>
      )}

      {/* ===== 热力图 + 签到并列 ===== */}
      <div style={{
        display: 'grid',
        gridTemplateColumns: 'repeat(auto-fit, minmax(360px, 1fr))',
        gap: '24px',
        marginBottom: '24px'
      }}>
        {/* 月度热力图 */}
        <div style={{
          backgroundColor: '#ffffff',
          borderRadius: '16px',
          padding: '24px',
          border: '1px solid #e5e7eb',
          boxShadow: '0 4px 6px -1px rgba(0,0,0,0.02)'
        }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px' }}>
            <h3 style={{ margin: 0, fontSize: '16px', fontWeight: 'bold', color: '#111827', display: 'flex', alignItems: 'center', gap: '8px' }}>
              <TrendingUp size={18} color="#6C3FF5" />
              <span>📊 每日练习热力图</span>
            </h3>
            <div style={{ display: 'flex', gap: '6px', alignItems: 'center' }}>
              <button
                onClick={() => loadHeatmap(hmMonth === 1 ? hmYear - 1 : hmYear, hmMonth === 1 ? 12 : hmMonth - 1)}
                style={{
                  border: '1px solid #d1d5db',
                  background: '#ffffff',
                  borderRadius: '6px',
                  padding: '2px 6px',
                  cursor: 'pointer'
                }}
              >
                <ChevronLeft size={14} />
              </button>
              <span style={{ fontSize: '13px', fontWeight: 600 }}>{hmYear}-{String(hmMonth).padStart(2, '0')}</span>
              <button
                onClick={() => loadHeatmap(hmMonth === 12 ? hmYear + 1 : hmYear, hmMonth === 12 ? 1 : hmMonth + 1)}
                style={{
                  border: '1px solid #d1d5db',
                  background: '#ffffff',
                  borderRadius: '6px',
                  padding: '2px 6px',
                  cursor: 'pointer'
                }}
              >
                <ChevronRight size={14} />
              </button>
            </div>
          </div>

          {/* 周标签 */}
          <div style={{ display: 'flex', gap: '4px', marginBottom: '8px' }}>
            {['日', '一', '二', '三', '四', '五', '六'].map(d => (
              <span key={d} style={{ width: '32px', textAlign: 'center', fontSize: '11px', color: '#9ca3af', fontWeight: 600 }}>{d}</span>
            ))}
          </div>

          {/* 热力图网格 */}
          <div style={{ display: 'flex', flexDirection: 'column', gap: '4px' }}>
            {weeks.map((week, wi) => (
              <div key={wi} style={{ display: 'flex', gap: '4px' }}>
                {week.map((day, di) => (
                  <div
                    key={di}
                    className="heatmap-cell"
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
                      color: day && day.totalSeconds > 0 ? '#ffffff' : '#d1d5db',
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
          <div style={{ display: 'flex', alignItems: 'center', gap: '6px', marginTop: '16px', fontSize: '11px', color: '#9ca3af' }}>
            <span>少</span>
            {[0, 1, 900, 1800, 3600].map(s => (
              <div key={s} style={{ width: '12px', height: '12px', borderRadius: '3px', background: heatColor(s), border: '1px solid #f3f4f6' }} />
            ))}
            <span>多</span>
            <span style={{ marginLeft: 'auto' }}>
              本月合计: <strong style={{ color: '#6c3ff5' }}>{fmtDuration(heatmap.reduce((s, d) => s + d.totalSeconds, 0))}</strong>
            </span>
          </div>
        </div>

        {/* 每日签到 */}
        <div style={{
          backgroundColor: '#ffffff',
          borderRadius: '16px',
          padding: '24px',
          border: '1px solid #e5e7eb',
          boxShadow: '0 4px 6px -1px rgba(0,0,0,0.02)',
          display: 'flex',
          flexDirection: 'column',
          justifyContent: 'space-between'
        }}>
          <div>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px' }}>
              <h3 style={{ margin: 0, fontSize: '16px', fontWeight: 'bold', color: '#111827', display: 'flex', alignItems: 'center', gap: '8px' }}>
                <Calendar size={18} color="#6C3FF5" />
                <span>📅 每日签到打卡</span>
              </h3>
              <span style={{ fontSize: '13px', color: '#4b5563' }}>
                当前积分: <strong style={{ color: '#6c3ff5' }}>{profile.points}</strong>
              </span>
            </div>

            <button
              className="btn"
              onClick={signIn}
              disabled={signing}
              style={{
                width: '100%',
                height: '42px',
                backgroundColor: '#6c3ff5',
                color: '#ffffff',
                border: 'none',
                borderRadius: '8px',
                fontWeight: 600,
                fontSize: '14px',
                cursor: 'pointer',
                marginBottom: '12px'
              }}
            >
              {signing ? '打卡中...' : '每日打卡签到'}
            </button>

            {signMsg && (
              <div style={{
                padding: '8px 12px',
                backgroundColor: '#ecfdf5',
                border: '1px solid #a7f3d0',
                borderRadius: '6px',
                color: '#065f46',
                fontSize: '12px',
                marginBottom: '12px'
              }}>
                {signMsg}
              </div>
            )}
          </div>

          {/* 签到标签面板 */}
          <div style={{
            display: 'flex',
            flexWrap: 'wrap',
            gap: '6px',
            maxHeight: '120px',
            overflowY: 'auto'
          }}>
            {calendar.map(d => (
              <span
                key={d.day}
                style={{
                  display: 'inline-block',
                  padding: '4px 10px',
                  borderRadius: '12px',
                  fontSize: '11px',
                  fontWeight: 600,
                  backgroundColor: d.signed ? '#6c3ff5' : '#f3f4f6',
                  color: d.signed ? '#ffffff' : '#9ca3af',
                  transition: 'all 0.2s'
                }}
              >
                {d.day}日
              </span>
            ))}
          </div>
        </div>
      </div>

      {/* ===== SRS 待复习 + 排行榜并列 ===== */}
      <div style={{
        display: 'grid',
        gridTemplateColumns: 'repeat(auto-fit, minmax(360px, 1fr))',
        gap: '24px'
      }}>
        {/* SRS 词卡复习 */}
        <div style={{
          backgroundColor: '#ffffff',
          borderRadius: '16px',
          padding: '24px',
          border: '1px solid #e5e7eb',
          boxShadow: '0 4px 6px -1px rgba(0,0,0,0.02)'
        }}>
          <h3 style={{ margin: '0 0 16px', fontSize: '16px', fontWeight: 'bold', color: '#111827', display: 'flex', alignItems: 'center', gap: '8px' }}>
            <BookOpen size={18} color="#6C3FF5" />
            <span>🧠 智能记忆卡片复习</span>
          </h3>

          {cards.length === 0 ? (
            <div style={{
              textAlign: 'center',
              padding: '32px',
              color: '#9ca3af',
              border: '1px dashed #e5e7eb',
              borderRadius: '8px'
            }}>
              <CheckCircle2 size={24} style={{ color: '#10b981', marginBottom: '8px' }} />
              <p style={{ margin: 0, fontSize: '13px' }}>太棒了，当前没有待复习的词卡！</p>
            </div>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '8px', maxHeight: '240px', overflowY: 'auto' }}>
              {cards.map(c => (
                <div key={c.id} style={{
                  padding: '10px 14px',
                  borderRadius: '8px',
                  backgroundColor: '#f9fafb',
                  border: '1px solid #f3f4f6',
                  display: 'flex',
                  justifyContent: 'space-between',
                  alignItems: 'center'
                }}>
                  <strong style={{ color: '#111827', fontSize: '14px' }}>{c.word}</strong>
                  <span style={{
                    fontSize: '11px',
                    padding: '2px 8px',
                    backgroundColor: '#e0e7ff',
                    color: '#4f46e5',
                    borderRadius: '12px'
                  }}>
                    {c.phrase || '复习中'}
                  </span>
                </div>
              ))}
            </div>
          )}
        </div>

        {/* 积分排行榜 */}
        <div style={{
          backgroundColor: '#ffffff',
          borderRadius: '16px',
          padding: '24px',
          border: '1px solid #e5e7eb',
          boxShadow: '0 4px 6px -1px rgba(0,0,0,0.02)'
        }}>
          <h3 style={{ margin: '0 0 16px', fontSize: '16px', fontWeight: 'bold', color: '#111827', display: 'flex', alignItems: 'center', gap: '8px' }}>
            <Award size={18} color="#6C3FF5" />
            <span>🏆 社区积分排行榜</span>
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
              {rank.map((r, index) => {
                const isTop3 = index < 3;
                const topColors = ['#f59e0b', '#94a3b8', '#b45309']; // 金银铜
                return (
                  <tr key={r.userId} style={{
                    borderBottom: '1px solid #f3f4f6',
                    backgroundColor: r.username === profile.username ? '#f5f3ff' : 'transparent',
                    fontWeight: r.username === profile.username ? 600 : 500
                  }}>
                    <td style={{ padding: '10px 8px' }}>
                      {isTop3 ? (
                        <span style={{
                          display: 'inline-block',
                          width: '20px',
                          height: '20px',
                          borderRadius: '50%',
                          backgroundColor: topColors[index],
                          color: '#ffffff',
                          textAlign: 'center',
                          lineHeight: '20px',
                          fontSize: '11px',
                          fontWeight: 'bold'
                        }}>
                          {index + 1}
                        </span>
                      ) : (
                        <span>{index + 1}</span>
                      )}
                    </td>
                    <td style={{ padding: '10px 8px', color: '#111827' }}>
                      {r.nickname || r.username}
                      {r.username === profile.username && <span style={{ marginLeft: '6px', fontSize: '10px', padding: '1px 5px', backgroundColor: '#6c3ff5', color: '#fff', borderRadius: '4px' }}>我</span>}
                    </td>
                    <td style={{ padding: '10px 8px', textAlign: 'right', color: '#6c3ff5', fontWeight: 'bold' }}>{r.points}</td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}
