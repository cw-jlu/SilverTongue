import { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import api from '../api/client';

export default function Login() {
  const [form, setForm] = useState({ username: '', password: '' });
  const [error, setError] = useState('');
  const navigate = useNavigate();

  const submit = async (e) => {
    e.preventDefault(); setError('');
    try {
      const res = await api.post('/user/login', form);
      localStorage.setItem('token', res.data.token);
      navigate('/');
    } catch (err) { setError(err?.message || 'Login failed'); }
  };

  return (
    <div className="card" style={{ maxWidth: 400, margin: '60px auto' }}>
      <h2>登录 SilverTongue</h2>
      {error && <p style={{ color: '#ef4444' }}>{error}</p>}
      <form onSubmit={submit}>
        <input placeholder="用户名" value={form.username} onChange={e => setForm({...form, username: e.target.value})} />
        <input placeholder="密码" type="password" value={form.password} onChange={e => setForm({...form, password: e.target.value})} />
        <button className="btn" style={{ width: '100%', marginTop: 8 }}>登录</button>
      </form>
      <p style={{ textAlign: 'center', marginTop: 12 }}>
        没有账号？<Link to="/register">注册</Link>
      </p>
    </div>
  );
}
