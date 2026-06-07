// 微信 OAuth 配置 — 部署时替换为正式值
const WECHAT_APP_ID = import.meta.env.VITE_WECHAT_APP_ID || 'wxffb3637a228223b8';

/**
 * 获取微信公众号 OAuth 授权地址（普通浏览器打开会显示二维码）
 * 公众号 appid 使用 oauth2/authorize + snsapi_userinfo
 */
export function getWeChatOAuthUrl() {
  const redirectUri = encodeURIComponent(window.location.origin + '/wx/callback');
  return `https://open.weixin.qq.com/connect/oauth2/authorize`
    + `?appid=${WECHAT_APP_ID}`
    + `&redirect_uri=${redirectUri}`
    + `&response_type=code`
    + `&scope=snsapi_userinfo`
    + `&state=${Date.now()}`
    + `#wechat_redirect`;
}

export default WECHAT_APP_ID;
