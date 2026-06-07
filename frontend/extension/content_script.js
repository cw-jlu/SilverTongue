(() => {
  'use strict';

  const APP_ORIGIN = 'http://localhost:3000';
  const API_BASE_URL = `${APP_ORIGIN}/api`;
  const POLL_INTERVAL_MS = 3000;
  const MAX_POLL_ATTEMPTS = 40;

  let lastUrl = window.location.href;

  function detectPlatform() {
    const host = window.location.hostname;
    if (host.includes('youtube.com') || host === 'youtu.be') {
      return 'youtube';
    }
    return null;
  }

  function parseTimeInput(value) {
    const trimmed = value.trim();
    if (!trimmed) {
      return Number.NaN;
    }

    const parts = trimmed.split(':').map((part) => part.trim());
    if (parts.some((part) => part === '' || Number.isNaN(Number(part)))) {
      return Number.NaN;
    }

    if (parts.length === 1) {
      return Number(parts[0]);
    }

    if (parts.length === 2) {
      return Number(parts[0]) * 60 + Number(parts[1]);
    }

    if (parts.length === 3) {
      return Number(parts[0]) * 3600 + Number(parts[1]) * 60 + Number(parts[2]);
    }

    return Number.NaN;
  }

  function formatTime(seconds) {
    const total = Math.max(0, Math.floor(seconds));
    const hours = Math.floor(total / 3600);
    const minutes = Math.floor((total % 3600) / 60);
    const secs = total % 60;

    if (hours > 0) {
      return `${hours}:${String(minutes).padStart(2, '0')}:${String(secs).padStart(2, '0')}`;
    }
    return `${minutes}:${String(secs).padStart(2, '0')}`;
  }

  function getVideoElement() {
    return document.querySelector('video');
  }

  function getToken() {
    return new Promise((resolve) => {
      chrome.storage.local.get(['silvertongue_token'], (result) => {
        resolve(result.silvertongue_token || null);
      });
    });
  }

  function setStatus(message, type = 'info') {
    const el = document.getElementById('st-status');
    if (!el) {
      return;
    }

    el.textContent = message;
    if (type === 'error') {
      el.style.color = '#f87171';
    } else if (type === 'success') {
      el.style.color = '#34d399';
    } else {
      el.style.color = '#cbd5f5';
    }
  }

  async function apiFetch(path, options = {}) {
    const token = await getToken();
    const headers = {
      'Content-Type': 'application/json',
      ...(options.headers || {}),
    };

    if (token) {
      headers.Authorization = `Bearer ${token}`;
    }

    const response = await fetch(`${API_BASE_URL}${path}`, {
      ...options,
      headers,
    });

    let payload = null;
    try {
      payload = await response.json();
    } catch (error) {
      throw new Error('服务返回了无法解析的响应。');
    }

    if (!response.ok || payload?.code !== 200) {
      throw new Error(payload?.message || '请求失败，请检查服务状态。');
    }

    return payload.data;
  }

  async function submitClip(pageUrl, startTime, endTime, platform) {
    setStatus('正在提交采集任务...', 'info');

    try {
      const clip = await apiFetch('/clips/harvest', {
        method: 'POST',
        body: JSON.stringify({
          url: pageUrl,
          platform,
          startTime,
          endTime,
        }),
      });

      setStatus('采集任务已创建，正在等待后端处理...', 'success');
      await pollStatus(clip.id);
    } catch (error) {
      setStatus(error.message || '提交失败，请确认已登录并启动本地服务。', 'error');
    }
  }

  async function pollStatus(clipId) {
    let attempts = 0;

    const timer = setInterval(async () => {
      attempts += 1;

      try {
        const clip = await apiFetch(`/clips/status/${clipId}`);
        if (clip.status === 3) {
          setStatus('采集完成，素材已保存到 SilverTongue。', 'success');
          clearInterval(timer);
          return;
        }

        if (clip.status === 4) {
          setStatus('采集失败，请查看后端日志。', 'error');
          clearInterval(timer);
          return;
        }

        setStatus('素材处理中，请稍候...', 'info');
      } catch (error) {
        if (attempts >= MAX_POLL_ATTEMPTS) {
          setStatus(error.message || '状态轮询超时，请稍后重试。', 'error');
          clearInterval(timer);
        }
      }

      if (attempts >= MAX_POLL_ATTEMPTS) {
        setStatus('状态轮询超时，请稍后在平台内查看结果。', 'error');
        clearInterval(timer);
      }
    }, POLL_INTERVAL_MS);
  }

  function removePanel() {
    document.getElementById('st-clip-panel')?.remove();
  }

  function showClipPanel(platform) {
    removePanel();

    const video = getVideoElement();
    const currentTime = video ? video.currentTime : 0;
    const title = (document.title || 'YouTube Clip').replace(' - YouTube', '').trim();

    const panel = document.createElement('div');
    panel.id = 'st-clip-panel';
    panel.innerHTML = `
      <div class="st-panel-header">
        <strong>Save Clip</strong>
        <button id="st-close-panel" type="button" aria-label="Close">&times;</button>
      </div>
      <div class="st-panel-title" title="${title}">${title}</div>
      <label class="st-panel-label" for="st-start-time">Start time</label>
      <input id="st-start-time" type="text" value="${formatTime(currentTime)}" />
      <label class="st-panel-label" for="st-end-time">End time</label>
      <input id="st-end-time" type="text" value="${formatTime(currentTime + 15)}" />
      <button id="st-submit-clip" type="button">Send to SilverTongue</button>
      <div id="st-status" class="st-panel-status"></div>
    `;

    document.body.appendChild(panel);

    document.getElementById('st-close-panel')?.addEventListener('click', removePanel);
    document.getElementById('st-submit-clip')?.addEventListener('click', () => {
      const start = parseTimeInput(document.getElementById('st-start-time')?.value || '');
      const end = parseTimeInput(document.getElementById('st-end-time')?.value || '');

      if (Number.isNaN(start) || Number.isNaN(end) || start < 0 || start >= end) {
        setStatus('请输入正确的开始和结束时间，例如 0:15 或 1:02:03。', 'error');
        return;
      }

      submitClip(window.location.href, start, end, platform);
    });
  }

  function injectButton() {
    const platform = detectPlatform();
    if (!platform || document.getElementById('st-harvest-btn')) {
      return;
    }

    const button = document.createElement('button');
    button.id = 'st-harvest-btn';
    button.type = 'button';
    button.textContent = 'Save Clip';
    button.title = 'Send the current YouTube segment to SilverTongue';
    button.addEventListener('click', () => showClipPanel(platform));

    document.body.appendChild(button);
  }

  function resetUiOnNavigation() {
    if (window.location.href === lastUrl) {
      return;
    }

    lastUrl = window.location.href;
    document.getElementById('st-harvest-btn')?.remove();
    removePanel();
    injectWhenReady();
  }

  function injectWhenReady() {
    if (getVideoElement()) {
      injectButton();
      return;
    }

    const observer = new MutationObserver(() => {
      if (getVideoElement()) {
        injectButton();
        observer.disconnect();
      }
    });

    observer.observe(document.body, { childList: true, subtree: true });
    setTimeout(() => observer.disconnect(), 15000);
  }

  function init() {
    if (!detectPlatform()) {
      return;
    }

    injectWhenReady();
    setInterval(resetUiOnNavigation, 1000);
  }

  init();
})();
