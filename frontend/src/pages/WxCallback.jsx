import { useEffect, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import api from '../api/client';

export default function WxCallback() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const code = searchParams.get('code');
    if (!code) {
      setError('缺少微信授权码（code），请重新登录');
      setLoading(false);
      return;
    }

    api.get('/user/wx/callback', { params: { code } })
      .then((res) => {
        localStorage.setItem('token', res.data.token);
        if (res.data?.user?.id) {
          localStorage.setItem('userId', res.data.user.id);
        }
        navigate('/', { replace: true });
      })
      .catch((err) => {
        setError(err?.message || '微信登录失败，请重试');
        setLoading(false);
      });
  }, [searchParams, navigate]);

  return (
    <div style={{
      display: 'flex',
      flexDirection: 'column',
      alignItems: 'center',
      justifyContent: 'center',
      minHeight: '60vh',
      fontFamily: "-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif"
    }}>
      {loading ? (
        <>
          <div style={{
            width: '40px',
            height: '40px',
            border: '4px solid #e5e7eb',
            borderTopColor: '#07C160',
            borderRadius: '50%',
            animation: 'spin 0.8s linear infinite'
          }} />
          <p style={{ marginTop: '16px', color: '#6b7280', fontSize: '14px' }}>
            微信登录中，请稍候...
          </p>
          <style>{`@keyframes spin { to { transform: rotate(360deg); } }`}</style>
        </>
      ) : (
        <>
          <p style={{ color: '#ef4444', fontSize: '14px', marginBottom: '16px' }}>{error}</p>
          <button
            onClick={() => navigate('/login', { replace: true })}
            style={{
              padding: '8px 24px',
              backgroundColor: '#6C3FF5',
              color: '#fff',
              border: 'none',
              borderRadius: '6px',
              cursor: 'pointer',
              fontSize: '14px'
            }}
          >
            返回登录
          </button>
        </>
      )}
    </div>
  );
}
