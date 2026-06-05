import { Link, useNavigate } from 'react-router-dom';

const navStyle = {
  display: 'flex', gap: 16, padding: '12px 20px',
  background: '#4f46e5', color: '#fff', alignItems: 'center'
};

export default function Navbar() {
  const token = localStorage.getItem('token');
  const navigate = useNavigate();

  const logout = () => { localStorage.removeItem('token'); navigate('/login'); };

  if (!token) return null;

  return (
    <nav style={navStyle}>
      <strong style={{ marginRight: 'auto' }}>🗣️ SilverTongue</strong>
      <Link to="/" style={linkStyle}>仪表盘</Link>
      <Link to="/shadowing" style={linkStyle}>影子跟读</Link>
      <Link to="/chat" style={linkStyle}>AI 对练</Link>
      <Link to="/square" style={linkStyle}>社区</Link>
      <Link to="/meeting" style={linkStyle}>语音房</Link>
      <button onClick={logout} style={btnStyle}>退出</button>
    </nav>
  );
}

const linkStyle = { color: '#fff', textDecoration: 'none', fontSize: 14 };
const btnStyle = { background: '#ef4444', color: '#fff', border: 'none', borderRadius: 4, padding: '4px 12px', cursor: 'pointer' };
