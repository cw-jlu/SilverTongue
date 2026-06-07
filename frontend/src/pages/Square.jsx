import { useEffect, useState } from 'react';
import { Search, Send, ShieldAlert, Sparkles, ThumbsUp } from 'lucide-react';
import api from '../api/client';

function normalizeComment(comment) {
  return {
    ...comment,
    id: String(comment.id),
    postId: comment.postId == null ? null : String(comment.postId),
    userId: comment.userId == null ? null : String(comment.userId),
    parentId: comment.parentId == null ? null : String(comment.parentId),
    replies: Array.isArray(comment.replies) ? comment.replies.map(normalizeComment) : []
  };
}

function normalizePost(post) {
  return {
    ...post,
    id: String(post.id),
    userId: post.userId == null ? null : String(post.userId),
    clipId: post.clipId == null ? null : String(post.clipId),
    comments: Array.isArray(post.comments) ? post.comments.map(normalizeComment) : []
  };
}

export default function Square() {
  const [posts, setPosts] = useState([]);
  const [content, setContent] = useState('');
  const [search, setSearch] = useState('');
  const [loading, setLoading] = useState(false);

  const loadPosts = async () => {
    try {
      const res = await api.get('/post/list');
      setPosts((res || []).map(normalizePost));
    } catch (e) {
      console.error('Load posts failed', e);
    }
  };

  useEffect(() => {
    loadPosts();
  }, []);

  const createPost = async () => {
    if (!content.trim()) {
      return;
    }
    setLoading(true);
    try {
      await api.post('/post', { content });
      setContent('');
      loadPosts();
    } catch (e) {
      console.error('Failed to post', e);
      alert('发布失败，请重试');
    } finally {
      setLoading(false);
    }
  };

  const doSearch = async () => {
    if (!search.trim()) {
      loadPosts();
      return;
    }
    try {
      const res = await api.get(`/post/search?q=${encodeURIComponent(search)}`);
      setPosts((res || []).map(normalizePost));
    } catch (e) {
      console.error('Search failed', e);
    }
  };

  const likePost = async (id) => {
    const postId = String(id);
    try {
      await api.post(`/post/${postId}/like`);
      setPosts((prev) =>
        prev.map((post) =>
          post.id === postId ? { ...post, likeCount: post.likeCount + 1 } : post
        )
      );
    } catch (e) {
      console.error('Like failed', e);
    }
  };

  return (
    <div
      style={{
        maxWidth: '800px',
        margin: '0 auto',
        padding: '24px 16px',
        fontFamily: "-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif"
      }}
    >
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          marginBottom: '24px'
        }}
      >
        <div>
          <h2 style={{ fontSize: '24px', fontWeight: 'bold', color: '#111827', margin: '0 0 4px' }}>
            学习社区广场
          </h2>
          <p style={{ fontSize: '14px', color: '#6b7280', margin: 0 }}>
            分享你的学习心得、表达积累和练习故事。
          </p>
        </div>
        <div
          style={{
            padding: '6px 12px',
            backgroundColor: '#f5f3ff',
            borderRadius: '12px',
            fontSize: '12px',
            color: '#6c3ff5',
            fontWeight: 600,
            display: 'flex',
            alignItems: 'center',
            gap: '4px'
          }}
        >
          <Sparkles size={14} />
          <span>活跃社区</span>
        </div>
      </div>

      <div
        className="card"
        style={{
          backgroundColor: '#ffffff',
          borderRadius: '12px',
          padding: '20px',
          border: '1px solid #e5e7eb',
          boxShadow: '0 4px 6px -1px rgba(0,0,0,0.05)',
          marginBottom: '20px',
          display: 'flex',
          flexDirection: 'column',
          gap: '12px'
        }}
      >
        <textarea
          placeholder="今天学到了什么好表达？或者想分享一段练习体验？"
          value={content}
          onChange={(e) => setContent(e.target.value)}
          rows={3}
          style={{
            width: '100%',
            padding: '12px',
            borderRadius: '8px',
            border: '1px solid #d1d5db',
            fontSize: '14px',
            outline: 'none',
            resize: 'none',
            boxSizing: 'border-box',
            fontFamily: 'inherit'
          }}
        />
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <span style={{ fontSize: '12px', color: '#9ca3af' }}>{content.length} / 500</span>
          <button
            className="btn"
            onClick={createPost}
            disabled={!content.trim() || loading}
            style={{
              backgroundColor: '#6c3ff5',
              padding: '8px 20px',
              height: '38px',
              borderRadius: '8px',
              fontSize: '14px',
              fontWeight: 600,
              display: 'flex',
              alignItems: 'center',
              gap: '6px',
              border: 'none',
              color: '#fff',
              cursor: 'pointer',
              opacity: content.trim() ? 1 : 0.6
            }}
          >
            <Send size={14} />
            <span>{loading ? '发布中...' : '发布'}</span>
          </button>
        </div>
      </div>

      <div style={{ display: 'flex', gap: '10px', marginBottom: '24px' }}>
        <div style={{ position: 'relative', flex: 1 }}>
          <input
            placeholder="输入关键词搜索帖子内容..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && doSearch()}
            style={{
              width: '100%',
              height: '42px',
              padding: '0 12px 0 38px',
              borderRadius: '8px',
              border: '1px solid #d1d5db',
              fontSize: '14px',
              outline: 'none',
              boxSizing: 'border-box',
              margin: 0
            }}
          />
          <Search
            size={16}
            color="#9ca3af"
            style={{
              position: 'absolute',
              left: '12px',
              top: '50%',
              transform: 'translateY(-50%)'
            }}
          />
        </div>
        <button
          className="btn"
          onClick={doSearch}
          style={{
            backgroundColor: '#ffffff',
            color: '#374151',
            border: '1px solid #d1d5db',
            padding: '0 20px',
            height: '42px',
            borderRadius: '8px',
            fontSize: '14px',
            fontWeight: 600,
            cursor: 'pointer'
          }}
        >
          搜索
        </button>
      </div>

      <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
        {posts.length === 0 ? (
          <div
            style={{
              textAlign: 'center',
              padding: '40px',
              color: '#9ca3af',
              backgroundColor: '#ffffff',
              borderRadius: '12px',
              border: '1px dashed #e5e7eb'
            }}
          >
            <ShieldAlert size={32} style={{ marginBottom: '8px', color: '#9ca3af' }} />
            <p style={{ margin: 0, fontSize: '14px' }}>暂无相关帖子，来发第一条动态吧。</p>
          </div>
        ) : (
          posts.map((post) => {
            const colors = ['#f43f5e', '#ec4899', '#d946ef', '#a855f7', '#8b5cf6', '#6366f1', '#3b82f6', '#0ea5e9'];
            const displayName = post.nickname || post.username || 'U';
            const charCodeSum = displayName.split('').reduce((acc, char) => acc + char.charCodeAt(0), 0);
            const avatarBg = colors[charCodeSum % colors.length];

            return (
              <div
                key={post.id}
                className="card"
                style={{
                  backgroundColor: '#ffffff',
                  borderRadius: '12px',
                  padding: '20px',
                  border: '1px solid #e5e7eb',
                  boxShadow: '0 1px 3px rgba(0,0,0,0.02)',
                  display: 'flex',
                  flexDirection: 'column',
                  gap: '12px',
                  margin: 0
                }}
              >
                <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                  <div
                    style={{
                      width: '38px',
                      height: '38px',
                      borderRadius: '50%',
                      backgroundColor: avatarBg,
                      color: '#ffffff',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      fontWeight: 700,
                      fontSize: '14px'
                    }}
                  >
                    {displayName.substring(0, 1).toUpperCase()}
                  </div>

                  <div>
                    <div style={{ fontSize: '14px', fontWeight: 'bold', color: '#111827' }}>{displayName}</div>
                    <div style={{ fontSize: '11px', color: '#9ca3af', marginTop: '2px' }}>
                      {new Date(post.createTime).toLocaleString()}
                    </div>
                  </div>
                </div>

                <div
                  style={{
                    fontSize: '14px',
                    lineHeight: '1.6',
                    color: '#374151',
                    whiteSpace: 'pre-wrap',
                    paddingLeft: '50px'
                  }}
                >
                  {post.content}
                </div>

                <div style={{ display: 'flex', paddingLeft: '50px' }}>
                  <button
                    onClick={() => likePost(post.id)}
                    style={{
                      border: 'none',
                      background: '#f9fafb',
                      cursor: 'pointer',
                      display: 'flex',
                      alignItems: 'center',
                      gap: '6px',
                      padding: '6px 12px',
                      borderRadius: '16px',
                      fontSize: '12px',
                      fontWeight: 600,
                      color: '#4b5563',
                      transition: 'all 0.2s'
                    }}
                    onMouseEnter={(e) => {
                      e.currentTarget.style.backgroundColor = '#f3f4f6';
                      e.currentTarget.style.color = '#6c3ff5';
                    }}
                    onMouseLeave={(e) => {
                      e.currentTarget.style.backgroundColor = '#f9fafb';
                      e.currentTarget.style.color = '#4b5563';
                    }}
                  >
                    <ThumbsUp size={13} />
                    <span>{post.likeCount}</span>
                  </button>
                </div>
              </div>
            );
          })
        )}
      </div>
    </div>
  );
}
