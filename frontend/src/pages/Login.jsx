import { useEffect, useMemo, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { MessageCircle } from 'lucide-react';
import api from '../api/client';

const isLocalWechatRedirectHost = (hostname) =>
  hostname === 'localhost' || hostname === '127.0.0.1' || hostname === '::1';

export default function Login() {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [rememberMe, setRememberMe] = useState(false);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();
  const isLocalhost = useMemo(
    () => isLocalWechatRedirectHost(window.location.hostname),
    []
  );

  useEffect(() => {
    const saved = localStorage.getItem('rememberedUsername');
    if (saved) {
      setUsername(saved);
      setRememberMe(true);
    }
  }, []);

  const persistLogin = (loginData) => {
    if (rememberMe) {
      localStorage.setItem('rememberedUsername', username);
    } else {
      localStorage.removeItem('rememberedUsername');
    }

    localStorage.setItem('token', loginData.token);
    if (loginData?.user?.id) {
      localStorage.setItem('userId', String(loginData.user.id));
    }
  };

  const handleSubmit = async (event) => {
    event.preventDefault();
    setError('');
    setLoading(true);

    if (!username || !password) {
      setError('请输入用户名和密码');
      setLoading(false);
      return;
    }

    try {
      const res = await api.post('/user/login', { username, password });
      persistLogin(res.data);
      navigate('/');
    } catch (err) {
      setError(err?.message || '登录失败，请检查用户名或密码');
    } finally {
      setLoading(false);
    }
  };

  const handleWeChatLogin = async () => {
    setError('');

    if (isLocalhost) {
      setError('本地 localhost 不能直接完成微信登录。后续只需要把 .env 里的 WECHAT_REDIRECT_BASE_URL 改成公网域名即可联调。');
      return;
    }

    try {
      const res = await api.get('/user/wx/authorize-url');
      window.location.href = res.data;
    } catch (err) {
      setError(err?.message || '微信登录暂时不可用，请稍后重试');
    }
  };

  return (
    <div
      style={{
        minHeight: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        padding: '24px',
        background:
          'radial-gradient(circle at top left, rgba(108, 63, 245, 0.12), transparent 32%), linear-gradient(180deg, #f8fafc 0%, #eef2ff 100%)'
      }}
    >
      <div
        style={{
          width: '100%',
          maxWidth: '420px',
          backgroundColor: '#ffffff',
          border: '1px solid #e5e7eb',
          borderRadius: '18px',
          padding: '32px',
          boxShadow: '0 20px 45px rgba(15, 23, 42, 0.08)'
        }}
      >
        <div style={{ marginBottom: '28px', textAlign: 'center' }}>
          <h1 style={{ margin: '0 0 8px', fontSize: '30px', color: '#111827' }}>欢迎回来</h1>
          <p style={{ margin: 0, fontSize: '14px', color: '#6b7280' }}>
            登录 SilverTongue，继续你的口语练习。
          </p>
        </div>

        {isLocalhost && (
          <div
            style={{
              marginBottom: '16px',
              padding: '10px 12px',
              backgroundColor: '#fffbeb',
              color: '#92400e',
              border: '1px solid #fde68a',
              borderRadius: '10px',
              fontSize: '13px',
              lineHeight: 1.5
            }}
          >
            当前地址是 localhost。微信登录的回调基地址已经预留为配置项，后续只要把
            <code style={{ margin: '0 4px' }}>WECHAT_REDIRECT_BASE_URL</code>
            改成公网域名即可。
          </div>
        )}

        <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
          <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
            <label style={{ fontSize: '13px', fontWeight: 600, color: '#374151' }}>用户名</label>
            <input
              type="text"
              value={username}
              onChange={(event) => setUsername(event.target.value)}
              placeholder="请输入用户名"
              required
              style={{
                height: '46px',
                padding: '0 14px',
                border: '1px solid #d1d5db',
                borderRadius: '10px',
                fontSize: '14px',
                outline: 'none'
              }}
            />
          </div>

          <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
            <label style={{ fontSize: '13px', fontWeight: 600, color: '#374151' }}>密码</label>
            <input
              type="password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              placeholder="请输入密码"
              required
              style={{
                height: '46px',
                padding: '0 14px',
                border: '1px solid #d1d5db',
                borderRadius: '10px',
                fontSize: '14px',
                outline: 'none'
              }}
            />
          </div>

          <label
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: '8px',
              fontSize: '13px',
              color: '#4b5563',
              cursor: 'pointer'
            }}
          >
            <input
              type="checkbox"
              checked={rememberMe}
              onChange={(event) => setRememberMe(event.target.checked)}
            />
            记住账号
          </label>

          {error && (
            <div
              style={{
                padding: '10px 12px',
                backgroundColor: '#fef2f2',
                color: '#dc2626',
                border: '1px solid #fecaca',
                borderRadius: '10px',
                fontSize: '13px'
              }}
            >
              {error}
            </div>
          )}

          <button
            type="submit"
            disabled={loading}
            style={{
              height: '46px',
              border: 'none',
              borderRadius: '10px',
              backgroundColor: '#6c3ff5',
              color: '#ffffff',
              fontSize: '15px',
              fontWeight: 700,
              cursor: 'pointer'
            }}
          >
            {loading ? '登录中...' : '登录'}
          </button>
        </form>

        <div style={{ marginTop: '16px' }}>
          <button
            onClick={handleWeChatLogin}
            type="button"
            style={{
              width: '100%',
              height: '46px',
              backgroundColor: '#ffffff',
              border: '1px solid #d1d5db',
              borderRadius: '10px',
              color: '#374151',
              fontSize: '14px',
              fontWeight: 600,
              cursor: 'pointer',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              gap: '8px'
            }}
          >
            <MessageCircle size={18} color="#07C160" />
            微信扫码快捷登录
          </button>
        </div>

        <div style={{ marginTop: '22px', textAlign: 'center', fontSize: '14px', color: '#6b7280' }}>
          还没有账号？{' '}
          <Link to="/register" style={{ color: '#6c3ff5', fontWeight: 700, textDecoration: 'none' }}>
            立即注册
          </Link>
        </div>
      </div>
    </div>
  );
}
