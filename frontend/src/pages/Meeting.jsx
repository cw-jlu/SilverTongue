import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import api from '../api/client';

export default function Meeting() {
  const [rooms, setRooms] = useState([]);
  const [roomName, setRoomName] = useState('');
  const [maxUsers, setMaxUsers] = useState(10);
  const [userId, setUserId] = useState(null);
  const navigate = useNavigate();

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
    try {
      const res = await api.post('/room', { roomName, maxUsers });
      if (res?.data?.id) {
        navigate(`/meeting/${res.data.id}`);
      }
    } catch (error) {
      alert(error?.message || '创建失败');
    }
  };

  const joinRoom = async (roomId) => {
    try {
      await api.post(`/room/${roomId}/join`);
      navigate(`/meeting/${roomId}`);
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
