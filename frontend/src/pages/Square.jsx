import { useState, useEffect } from 'react';
import api from '../api/client';

export default function Square() {
  const [posts, setPosts] = useState([]);
  const [content, setContent] = useState('');
  const [search, setSearch] = useState('');

  const loadPosts = () => api.get('/post/list').then(r => setPosts(r.data || []));

  useEffect(() => { loadPosts(); }, []);

  const createPost = async () => {
    if (!content.trim()) return;
    await api.post('/post', { content });
    setContent('');
    loadPosts();
  };

  const doSearch = async () => {
    if (!search.trim()) return loadPosts();
    const r = await api.get(`/post/search?q=${encodeURIComponent(search)}`);
    setPosts(r.data || []);
  };

  return (
    <div>
      <h2>🏘 社区广场</h2>
      <div className="card">
        <textarea placeholder="分享你的学习心得..." value={content} onChange={e => setContent(e.target.value)} rows={3} />
        <button className="btn" onClick={createPost}>发布</button>
      </div>
      <div style={{ marginBottom: 12 }}>
        <input placeholder="🔍 搜索帖子..." value={search} onChange={e => setSearch(e.target.value)} style={{ width: '70%', display: 'inline' }} />
        <button className="btn btn-sm" onClick={doSearch} style={{ marginLeft: 8 }}>搜索</button>
      </div>
      {posts.map(p => (
        <div key={p.id} className="card">
          <p style={{ margin: 0 }}><strong>{p.nickname}</strong> <span style={{ color: '#6b7280', fontSize: 12 }}>{new Date(p.createTime).toLocaleString()}</span></p>
          <p style={{ whiteSpace: 'pre-wrap' }}>{p.content}</p>
          <button className="btn btn-sm" onClick={async () => { await api.post(`/post/${p.id}/like`); loadPosts(); }}>👍 {p.likeCount}</button>
        </div>
      ))}
    </div>
  );
}
