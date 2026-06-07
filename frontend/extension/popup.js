document.addEventListener('DOMContentLoaded', () => {
  const APP_ORIGIN = 'http://localhost:3000';
  const tokenInput = document.getElementById('token-input');
  const tokenStatus = document.getElementById('token-status');

  chrome.storage.local.get(['silvertongue_token'], (result) => {
    if (result.silvertongue_token) {
      tokenInput.value = result.silvertongue_token;
    }
  });

  document.getElementById('save-token').addEventListener('click', () => {
    const token = tokenInput.value.trim();
    chrome.storage.local.set({ silvertongue_token: token }, () => {
      tokenStatus.textContent = token ? 'Token 已保存。' : '已清空保存的 Token。';
      setTimeout(() => {
        tokenStatus.textContent = '';
      }, 2000);
    });
  });

  document.getElementById('open-youtube').addEventListener('click', () => {
    chrome.tabs.create({ url: 'https://www.youtube.com' });
  });

  document.getElementById('open-app').addEventListener('click', () => {
    chrome.tabs.create({ url: APP_ORIGIN });
  });
});
