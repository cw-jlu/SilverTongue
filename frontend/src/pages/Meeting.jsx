import { useState, useEffect } from 'react';
import api from '../api/client';

export default function Meeting() {
  const [rooms, setRooms] = useState([]);
  const [roomName, setRoomName] = useState('');
  const [maxUsers, setMaxUsers] = useState(10);

  const loadRooms = () => api.get('/room').then(r => setRooms(r.data || []));
  useEffect(() => { loadRooms(); }, []);

  const createRoom = async () => {
    if (!roomName.trim()) return;
    await api.post('/room', { roomName, maxUsers });
    setRoomName('');
    loadRooms();
  };

  const joinRoom = async (roomId) => {
    try {
      await api.post(`/room/${roomId}/join`);
      // 动态构建 WebSocket URL：开发模式连 localhost:8080，生产模式用当前 host
      const isDev = window.location.port === '3000';
      const wsProtocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
      const wsHost = window.location.hostname;
      const wsPort = isDev ? ':8080' : (window.location.port ? `:${window.location.port}` : '');
      const wsUrl = `${wsProtocol}//${wsHost}${wsPort}/ws/signaling?userId=${localStorage.getItem('userId') || 'guest'}`;
      const ws = new WebSocket(wsUrl);
      ws.onopen = () => ws.send(JSON.stringify({ type: 'join', roomId }));
      ws.onmessage = (e) => console.log('Signal:', JSON.parse(e.data));
      alert('已加入房间，WebSocket 信令已连接（查看 Console）');
    } catch (err) { alert(err?.message || '加入失败'); }
  };

  return (
    <div>
      <h2>🎧 语音大厅</h2>
      <div className="card">
        <input placeholder="房间名" value={roomName} onChange={e => setRoomName(e.target.value)} style={{ width: '60%', display: 'inline' }} />
        <input type="number" value={maxUsers} onChange={e => setMaxUsers(e.target.value)} style={{ width: 60, display: 'inline', marginLeft: 8 }} />
        <button className="btn btn-sm" onClick={createRoom} style={{ marginLeft: 8 }}>创建</button>
      </div>
      <div className="grid cols-2">
        {rooms.map(r => (
          <div key={r.id} className="card">
            <h3>{r.roomName}</h3>
            <p>👥 {r.onlineCount} / {r.maxUsers} | 房主 ID: {r.creatorId}</p>
            <button className="btn btn-sm" onClick={() => joinRoom(r.id)}>加入</button>
          </div>
        ))}
      </div>
    </div>
  );
}
