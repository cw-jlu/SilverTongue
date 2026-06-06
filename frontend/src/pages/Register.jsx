import { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { Sparkles, ArrowRight } from 'lucide-react';
import api from '../api/client';

export default function Register() {
  const [form, setForm] = useState({ username: '', password: '', nickname: '' });
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();

  const submit = async (e) => {
    e.preventDefault();
    setError('');
    setLoading(true);

    if (!form.username || !form.password) {
      setError('用户名和密码为必填项');
      setLoading(false);
      return;
    }

    try {
      await api.post('/user/register', form);
      alert('注册成功！请登录。');
      navigate('/login');
    } catch (err) {
      setError(err?.message || '注册失败，用户名可能已存在');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div style={{
      display: 'grid',
      gridTemplateColumns: 'repeat(auto-fit, minmax(360px, 1fr))',
      minHeight: '100vh',
      backgroundColor: '#f9fafb',
      fontFamily: "-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif"
    }}>
      {/* 渐变修饰侧边栏 (左侧/顶部) */}
      <div style={{
        background: 'linear-gradient(135deg, #0c0f1d 0%, #1a153b 100%)',
        display: 'flex',
        flexDirection: 'column',
        justifyContent: 'space-between',
        padding: '40px',
        color: '#fff',
        position: 'relative',
        overflow: 'hidden',
        minHeight: '450px'
      }}>
        {/* LOGO */}
        <div style={{ display: 'flex', alignItems: 'center', gap: '8px', zIndex: 10 }}>
          <div style={{
            width: '32px',
            height: '32px',
            borderRadius: '8px',
            backgroundColor: 'rgba(255,255,255,0.1)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center'
          }}>
            <Sparkles size={16} color="#FF9B6B" />
          </div>
          <span style={{ fontSize: '18px', fontWeight: 'bold', letterSpacing: '0.5px' }}>SilverTongue</span>
        </div>

        {/* 欢迎标语 */}
        <div style={{ zIndex: 5, maxWidth: '380px', margin: 'auto 0' }}>
          <h1 style={{ fontSize: '36px', fontWeight: '800', lineHeight: '1.2', margin: '0 0 16px' }}>
            开启属于你的<br />
            AI 英语私教之旅
          </h1>
          <p style={{ fontSize: '16px', color: 'rgba(255,255,255,0.7)', lineHeight: '1.6', margin: '0 0 24px' }}>
            SilverTongue 整合了智能对话引擎、中式英语纠错与影子跟读评分，为您打造全方位的浸润式口语练习空间。
          </p>
          <div style={{
            display: 'flex',
            alignItems: 'center',
            gap: '8px',
            fontSize: '14px',
            color: '#FF9B6B',
            fontWeight: 600
          }}>
            <span>免费注册即可体验全部核心功能</span>
            <ArrowRight size={16} />
          </div>
        </div>

        {/* 底部版权 */}
        <div style={{ fontSize: '12px', color: 'rgba(255,255,255,0.4)', zIndex: 10 }}>
          © 2026 SilverTongue. All rights reserved.
        </div>

        {/* 背景光晕装饰 */}
        <div style={{
          position: 'absolute',
          width: '300px',
          height: '300px',
          borderRadius: '50%',
          backgroundColor: 'rgba(108, 63, 245, 0.1)',
          filter: 'blur(60px)',
          bottom: '-50px',
          left: '-50px'
        }} />
      </div>

      {/* 注册表单 (右侧/底部) */}
      <div style={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        padding: '40px',
        backgroundColor: '#ffffff'
      }}>
        <div style={{ width: '100%', maxWidth: '380px' }}>
          {/* 标题 */}
          <div style={{ textAlign: 'center', marginBottom: '32px' }}>
            <h1 style={{ fontSize: '28px', fontWeight: 'bold', color: '#111827', margin: '0 0 8px' }}>新用户注册</h1>
            <p style={{ fontSize: '14px', color: '#6b7280', margin: 0 }}>创建您的 SilverTongue 练习账号</p>
          </div>

          {/* 表单 */}
          <form onSubmit={submit} style={{ display: 'flex', flexDirection: 'column', gap: '18px' }}>
            {/* 用户名 */}
            <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
              <label style={{ fontSize: '13px', fontWeight: 600, color: '#374151' }}>用户名 <span style={{ color: '#ef4444' }}>*</span></label>
              <input
                type="text"
                placeholder="作为登录凭证"
                value={form.username}
                onChange={e => setForm({...form, username: e.target.value})}
                required
                style={{
                  height: '46px',
                  padding: '0 14px',
                  border: '1px solid #e5e7eb',
                  borderRadius: '8px',
                  fontSize: '14px',
                  outline: 'none',
                  margin: 0
                }}
              />
            </div>

            {/* 密码 */}
            <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
              <label style={{ fontSize: '13px', fontWeight: 600, color: '#374151' }}>密码 <span style={{ color: '#ef4444' }}>*</span></label>
              <input
                type="password"
                placeholder="设置登录密码"
                value={form.password}
                onChange={e => setForm({...form, password: e.target.value})}
                required
                style={{
                  height: '46px',
                  padding: '0 14px',
                  border: '1px solid #e5e7eb',
                  borderRadius: '8px',
                  fontSize: '14px',
                  outline: 'none',
                  margin: 0
                }}
              />
            </div>

            {/* 昵称 */}
            <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
              <label style={{ fontSize: '13px', fontWeight: 600, color: '#374151' }}>昵称</label>
              <input
                type="text"
                placeholder="怎么称呼您（非必填）"
                value={form.nickname}
                onChange={e => setForm({...form, nickname: e.target.value})}
                style={{
                  height: '46px',
                  padding: '0 14px',
                  border: '1px solid #e5e7eb',
                  borderRadius: '8px',
                  fontSize: '14px',
                  outline: 'none',
                  margin: 0
                }}
              />
            </div>

            {/* 错误显示 */}
            {error && (
              <div style={{
                padding: '10px 12px',
                fontSize: '13px',
                color: '#ef4444',
                backgroundColor: '#fef2f2',
                border: '1px solid #fee2e2',
                borderRadius: '6px'
              }}>
                {error}
              </div>
            )}

            {/* 提交按钮 */}
            <button
              type="submit"
              disabled={loading}
              className="btn"
              style={{
                height: '46px',
                backgroundColor: '#6C3FF5',
                color: '#fff',
                border: 'none',
                borderRadius: '8px',
                fontWeight: 600,
                fontSize: '15px',
                cursor: 'pointer',
                transition: 'background-color 0.2s',
                marginTop: '6px',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center'
              }}
            >
              {loading ? "注册中..." : "完成注册"}
            </button>
          </form>

          {/* 登录链接 */}
          <div style={{ textAlign: 'center', marginTop: '24px', fontSize: '14px', color: '#6b7280' }}>
            已有账号?{" "}
            <Link to="/login" style={{ color: '#6C3FF5', fontWeight: 600, textDecoration: 'none' }}>
              立即登录
            </Link>
          </div>
        </div>
      </div>
    </div>
  );
}
