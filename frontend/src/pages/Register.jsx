import { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import api from '../api/client';

export default function Register() {
  const [form, setForm] = useState({ username: '', password: '', nickname: '' });
  const [error, setError] = useState('');
  const navigate = useNavigate();

  const submit = async (e) => {
    e.preventDefault(); setError('');
    try {
      await api.post('/user/register', form);
      navigate('/login');
    } catch (err) { setError(err?.message || 'Register failed'); }
  };

  return (
    <div className="card" style={{ maxWidth: 400, margin: '60px auto' }}>
      <h2>注册</h2>
      {error && <p style={{ color: '#ef4444' }}>{error}</p>}
      <form onSubmit={submit}>
        <input placeholder="用户名" value={form.username} onChange={e => setForm({...form, username: e.target.value})} />
        <input placeholder="密码" type="password" value={form.password} onChange={e => setForm({...form, password: e.target.value})} />
        <input placeholder="昵称（可选）" value={form.nickname} onChange={e => setForm({...form, nickname: e.target.value})} />
        <button className="btn" style={{ width: '100%', marginTop: 8 }}>注册</button>
      </form>
      <p style={{ textAlign: 'center', marginTop: 12 }}>
        已有账号？<Link to="/login">登录</Link>
      </p>
    </div>
  );
}
