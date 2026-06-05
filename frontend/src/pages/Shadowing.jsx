import { useState } from 'react';
import api from '../api/client';

export default function Shadowing() {
  const [clips, setClips] = useState([]);
  const [file, setFile] = useState(null);
  const [uploadMsg, setUploadMsg] = useState('');

  const loadClips = () => api.get('/clips?page=1&size=20').then(r => setClips(r.data || []));

  const upload = async () => {
    if (!file) return;
    const fd = new FormData();
    fd.append('file', file);
    try {
      const r = await api.post('/material/upload', fd);
      setUploadMsg(`上传成功: ${r.data.title}`);
      setFile(null);
    } catch (err) { setUploadMsg(err?.message || '上传失败'); }
  };

  return (
    <div>
      <h2>🎬 影子跟读</h2>
      <div className="card">
        <h3>📤 上传素材</h3>
        <input type="file" onChange={e => setFile(e.target.files[0])} />
        <button className="btn btn-sm" onClick={upload} style={{ marginLeft: 8 }}>上传</button>
        {uploadMsg && <p style={{ marginTop: 8 }}>{uploadMsg}</p>}
      </div>

      <div className="card">
        <h3>📋 语料切片 <button className="btn btn-sm" onClick={loadClips}>刷新</button></h3>
        {clips.map(c => (
          <div key={c.id} style={{ padding: '8px 0', borderBottom: '1px solid #f3f4f6' }}>
            <p style={{ margin: 0 }}><strong>{c.content || '(无字幕)'}</strong></p>
            <small style={{ color: '#6b7280' }}>⏱ {c.startTime}s - {c.endTime}s | {c.translation || ''}</small>
          </div>
        ))}
      </div>
    </div>
  );
}
