/* SilverTongue Harvester — content_script.js */
(function () {
  'use strict';

  const APP_ORIGIN = 'http://localhost:3000';
  const API_BASE_URL = `${APP_ORIGIN}/api`;

  // ─── 1. 页面检测 ───────────────────────────────
  function detectPlatform() {
    const host = window.location.hostname;
    if (host.includes('youtube.com') || host === 'youtu.be') return 'youtube';
    if (host.includes('netflix.com')) return 'netflix';
    if (host.includes('coursera.org')) return 'coursera';
    return null;
  }

  function getVideoId() {
    const host = window.location.hostname;
    if (host === 'youtu.be') return window.location.pathname.slice(1);
    const params = new URLSearchParams(window.location.search);
    return params.get('v') || null;
  }

  // ─── 2. UI: 注入浮动按钮 ────────────────────────
  function injectButton() {
    const platform = detectPlatform();
    if (!platform) return;

    // 避免重复注入
    if (document.getElementById('st-harvest-btn')) return;

    const btn = document.createElement('div');
    btn.id = 'st-harvest-btn';
    btn.innerHTML = '🎙️ 采集';
    btn.title = 'SilverTongue — 一键采集当前视频片段';
    Object.assign(btn.style, {
      position: 'fixed',
      bottom: '120px',
      right: '20px',
      zIndex: '9999',
      padding: '10px 16px',
      background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
      color: '#fff',
      borderRadius: '24px',
      cursor: 'pointer',
      fontSize: '14px',
      fontWeight: '600',
      fontFamily: 'sans-serif',
      boxShadow: '0 4px 15px rgba(102,126,234,0.4)',
      transition: 'transform 0.2s',
    });

    btn.addEventListener('mouseenter', () => (btn.style.transform = 'scale(1.05)'));
    btn.addEventListener('mouseleave', () => (btn.style.transform = 'scale(1)'));
    btn.addEventListener('click', () => showClipPanel(platform));

    document.body.appendChild(btn);
  }

  // ─── 3. 弹出裁切面板 ────────────────────────────
  function showClipPanel(platform) {
    // 移除旧面板
    const old = document.getElementById('st-clip-panel');
    if (old) old.remove();

    const video = document.querySelector('video');
    const currentTime = video ? video.currentTime : 0;

    const panel = document.createElement('div');
    panel.id = 'st-clip-panel';
    Object.assign(panel.style, {
      position: 'fixed',
      bottom: '180px',
      right: '20px',
      zIndex: '10000',
      width: '300px',
      padding: '20px',
      background: '#1e1e2e',
      color: '#cdd6f4',
      borderRadius: '16px',
      fontFamily: 'sans-serif',
      boxShadow: '0 8px 32px rgba(0,0,0,0.4)',
    });

    const formatTime = (s) => {
      const m = Math.floor(s / 60);
      const sec = Math.floor(s % 60);
      return `${m}:${String(sec).padStart(2, '0')}`;
    };

    const videoTitle = document.title.replace(' - YouTube', '').substring(0, 50);

    panel.innerHTML = `
      <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:16px">
        <span style="font-weight:700;font-size:16px">✂️ 采集语料</span>
        <button id="st-close-panel" style="background:none;border:none;color:#a6adc8;cursor:pointer;font-size:18px">&times;</button>
      </div>
      <div style="font-size:12px;color:#a6adc8;margin-bottom:12px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis">
        🎬 ${videoTitle}
      </div>
      <div style="margin-bottom:12px">
        <label style="font-size:12px;color:#a6adc8">起始时间</label>
        <input id="st-start-time" type="text" value="${formatTime(currentTime)}" 
          style="width:100%;padding:8px;margin-top:4px;border:1px solid #45475a;border-radius:8px;background:#313244;color:#cdd6f4">
      </div>
      <div style="margin-bottom:12px">
        <label style="font-size:12px;color:#a6adc8">结束时间</label>
        <input id="st-end-time" type="text" value="${formatTime(currentTime + 15)}" 
          style="width:100%;padding:8px;margin-top:4px;border:1px solid #45475a;border-radius:8px;background:#313244;color:#cdd6f4">
      </div>
      <button id="st-submit-clip" 
        style="width:100%;padding:10px;background:linear-gradient(135deg,#a6e3a1,#94e2d5);color:#1e1e2e;border:none;border-radius:8px;font-weight:700;cursor:pointer">
        📤 提交采集
      </button>
      <div id="st-status" style="margin-top:8px;font-size:12px;text-align:center"></div>
    `;

    document.body.appendChild(panel);

    // 事件绑定
    document.getElementById('st-close-panel').addEventListener('click', () => panel.remove());
    document.getElementById('st-submit-clip').addEventListener('click', () => {
      const start = parseTime(document.getElementById('st-start-time').value);
      const end = parseTime(document.getElementById('st-end-time').value);
      if (isNaN(start) || isNaN(end) || start >= end) {
        setStatus('⚠️ 时间格式错误或起始大于结束', 'error');
        return;
      }
      submitClip(window.location.href, start, end, platform);
    });
  }

  function parseTime(str) {
    const parts = str.split(':');
    if (parts.length === 2) return parseInt(parts[0]) * 60 + parseFloat(parts[1]);
    return parseFloat(str);
  }

  function setStatus(msg, type) {
    const el = document.getElementById('st-status');
    if (el) {
      el.textContent = msg;
      el.style.color = type === 'error' ? '#f38ba8' : '#a6e3a1';
    }
  }

  // ─── 4. 提交采集请求 ────────────────────────────
  async function submitClip(pageUrl, startTime, endTime, platform) {
    setStatus('⏳ 正在提交...', 'info');

    const token = await getToken();
    try {
      const resp = await fetch(`${API_BASE_URL}/clips/harvest`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          ...(token ? { Authorization: `Bearer ${token}` } : {}),
        },
        body: JSON.stringify({
          url: pageUrl,
          platform: platform,
          startTime: startTime,
          endTime: endTime,
        }),
      });

      // 后端返回: { id, materialId, startTime, endTime, status }
      const clip = await resp.json();
      if (clip && clip.id) {
        setStatus('✅ 采集成功！等待后台下载切割...', 'success');
        // 轮询状态
        pollStatus(clip.id);
      } else {
        setStatus(`❌ ${data.message || '请求失败'}`, 'error');
      }
    } catch (err) {
      setStatus('❌ 网络错误，请检查后端是否启动', 'error');
    }
  }

  async function pollStatus(clipId) {
    let attempts = 0;
    const maxAttempts = 30;
    const interval = setInterval(async () => {
      attempts++;
      try {
        const resp = await fetch(`${API_BASE_URL}/clips/status/${clipId}`);
        const clip = await resp.json();
        // 后端返回 ClipVO: { id, materialId, status (0待处理/1下载中/3完成/4失败), ... }
        const status = clip.status;
        if (status === 3) {
          setStatus('✅ 采集完成！已存入语料库', 'success');
          clearInterval(interval);
        } else if (status === 4) {
          setStatus('❌ 采集失败', 'error');
          clearInterval(interval);
        } else if (status === 1) {
          setStatus('⏳ 下载切割中...', 'info');
        } else {
          setStatus('⏳ 等待处理...', 'info');
        }
      } catch (err) {
        // ignore transient errors
      }
      if (attempts >= maxAttempts) {
        setStatus('⏱️ 超时，请稍后查看', 'error');
        clearInterval(interval);
      }
    }, 3000);
  }

  async function getToken() {
    return new Promise((resolve) => {
      chrome.storage.local.get(['silvertongue_token'], (result) => {
        resolve(result.silvertongue_token || null);
      });
    });
  }

  // ─── 5. 启动 ────────────────────────────────────
  function init() {
    const platform = detectPlatform();
    if (!platform) return;

    // 等待页面完全加载后再注入
    const observer = new MutationObserver(() => {
      if (document.querySelector('video')) {
        injectButton();
        observer.disconnect();
      }
    });
    observer.observe(document.body, { childList: true, subtree: true });

    // 兜底：5 秒后强制注入
    setTimeout(injectButton, 5000);
  }

  init();
})();
