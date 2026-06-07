import { useEffect, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import api from '../api/client';

export default function WxCallback() {
  const [searchParams] = useSearchParams();
  const [error, setError] = useState('');
  const navigate = useNavigate();

  useEffect(() => {
    const code = searchParams.get('code');
    const wxError = searchParams.get('error');

    if (wxError) {
      setError(`微信授权失败: ${wxError}`);
      return;
    }

    if (!code) {
      setError('未获取到微信授权 code');
      return;
    }

    let cancelled = false;

    const completeWxLogin = async () => {
      try {
        const res = await api.get(`/user/wx/callback?code=${encodeURIComponent(code)}`);
        if (cancelled) {
          return;
        }

        localStorage.setItem('token', res.token);
        if (res?.user?.id) {
          localStorage.setItem('userId', String(res.user.id));
        }
        navigate('/');
      } catch (err) {
        if (!cancelled) {
          setError(err?.message || '微信登录失败，请稍后重试');
        }
      }
    };

    completeWxLogin();

    return () => {
      cancelled = true;
    };
  }, [navigate, searchParams]);

  return (
    <div style={{ maxWidth: 480, margin: '80px auto', padding: '24px', textAlign: 'center' }}>
      <h1 style={{ fontSize: '24px', marginBottom: '12px', color: '#111827' }}>微信登录</h1>
      {error ? (
        <p style={{ color: '#dc2626', fontSize: '14px' }}>{error}</p>
      ) : (
        <p style={{ color: '#4b5563', fontSize: '14px' }}>正在处理微信登录，请稍候...</p>
      )}
    </div>
  );
}
