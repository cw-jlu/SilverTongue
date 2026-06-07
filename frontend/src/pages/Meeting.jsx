import { useState, useEffect } from 'react';
import api from '../api/client';

export default function Meeting() {
  const [rooms, setRooms] = useState([]);
  const [roomName, setRoomName] = useState('');
  const [maxUsers, setMaxUsers] = useState(10);
  const [userId, setUserId] = useState(null);

  const loadRooms = () => api.get('/room').then((response) => setRooms(response.data || []));

  useEffect(() => {
    loadRooms();
  }, []);

  useEffect(() => {
    api.get('/user/me')
      .then((response) => {
        if (response.data?.id) {
          setUserId(response.data.id);
        }
      })
      .catch(() => {});
  }, []);

  const createRoom = async () => {
    if (!roomName.trim()) return;
    await api.post('/room', { roomName, maxUsers });
    setRoomName('');
    loadRooms();
  };

  const joinRoom = async (roomId) => {
    try {
      await api.post(`/room/${roomId}/join`);
      const wsProtocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
      const wsUserId = encodeURIComponent(userId || 'guest');
      const wsUrl = `${wsProtocol}//${window.location.host}/ws/signaling?userId=${wsUserId}`;
      const ws = new WebSocket(wsUrl);
      ws.onopen = () => ws.send(JSON.stringify({ type: 'join', roomId }));
      ws.onmessage = (event) => console.log('Signal:', JSON.parse(event.data));
      alert('已加入房间，WebSocket 信令已连接，请查看 Console。');
    } catch (error) {
      alert(error?.message || '加入失败');
    }
  };

  return (
    <div>
      <h2>🎤 语音大厅</h2>
      <div className="card">
        <input
          placeholder="房间名称"
          value={roomName}
          onChange={(event) => setRoomName(event.target.value)}
          style={{ width: '60%', display: 'inline' }}
        />
        <input
          type="number"
          value={maxUsers}
          onChange={(event) => setMaxUsers(event.target.value)}
          style={{ width: 60, display: 'inline', marginLeft: 8 }}
        />
        <button className="btn btn-sm" onClick={createRoom} style={{ marginLeft: 8 }}>
          创建
        </button>
      </div>
      <div className="grid cols-2">
        {rooms.map((room) => (
          <div key={room.id} className="card">
            <h3>{room.roomName}</h3>
            <p>👥 {room.onlineCount} / {room.maxUsers} | 房主 ID: {room.creatorId}</p>
            <button className="btn btn-sm" onClick={() => joinRoom(room.id)}>
              加入
            </button>
          </div>
        ))}
      </div>
    </div>
  );
}
