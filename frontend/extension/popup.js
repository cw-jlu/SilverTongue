/* SilverTongue Harvester — popup.js */
document.addEventListener('DOMContentLoaded', () => {
  const APP_ORIGIN = 'http://localhost:3000';
  const tokenInput = document.getElementById('token-input');
  const tokenStatus = document.getElementById('token-status');

  // 加载已保存的 Token
  chrome.storage.local.get(['silvertongue_token'], (result) => {
    if (result.silvertongue_token) {
      tokenInput.value = result.silvertongue_token;
    }
  });

  // 保存 Token
  document.getElementById('save-token').addEventListener('click', () => {
    const token = tokenInput.value.trim();
    chrome.storage.local.set({ silvertongue_token: token }, () => {
      tokenStatus.textContent = '✅ Token 已保存';
      tokenStatus.style.color = '#a6e3a1';
      setTimeout(() => (tokenStatus.textContent = ''), 2000);
    });
  });

  // 快捷入口
  document.getElementById('open-youtube').addEventListener('click', () => {
    chrome.tabs.create({ url: 'https://www.youtube.com' });
  });

  document.getElementById('open-app').addEventListener('click', () => {
    chrome.tabs.create({ url: APP_ORIGIN });
  });
});
