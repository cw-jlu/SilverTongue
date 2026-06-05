import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import Navbar from './components/Navbar';
import Login from './pages/Login';
import Register from './pages/Register';
import Dashboard from './pages/Dashboard';
import Shadowing from './pages/Shadowing';
import Chat from './pages/Chat';
import Square from './pages/Square';
import Meeting from './pages/Meeting';

function PrivateRoute({ children }) {
  const token = localStorage.getItem('token');
  return token ? children : <Navigate to="/login" />;
}

export default function App() {
  return (
    <BrowserRouter>
      <Navbar />
      <main style={{ maxWidth: 960, margin: '0 auto', padding: 20 }}>
        <Routes>
          <Route path="/login" element={<Login />} />
          <Route path="/register" element={<Register />} />
          <Route path="/" element={<PrivateRoute><Dashboard /></PrivateRoute>} />
          <Route path="/shadowing" element={<PrivateRoute><Shadowing /></PrivateRoute>} />
          <Route path="/chat" element={<PrivateRoute><Chat /></PrivateRoute>} />
          <Route path="/square" element={<PrivateRoute><Square /></PrivateRoute>} />
          <Route path="/meeting" element={<PrivateRoute><Meeting /></PrivateRoute>} />
        </Routes>
      </main>
    </BrowserRouter>
  );
}
