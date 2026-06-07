package com.silvertongue.user.service;

import com.silvertongue.user.dto.WxAccessTokenResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Slf4j
@Service
public class WeChatService {

    private static final String CALLBACK_PATH = "/wx/callback";

    private final String appId;
    private final String appSecret;
    private final String redirectBaseUrl;
    private final String authorizeUrl;
    private final String accessTokenUrl;
    private final RestTemplate restTemplate;

    public WeChatService(
            @Value("${wechat.app-id}") String appId,
            @Value("${wechat.app-secret}") String appSecret,
            @Value("${wechat.redirect-base-url:}") String redirectBaseUrl,
            @Value("${wechat.oauth2.authorize-url}") String authorizeUrl,
            @Value("${wechat.oauth2.access-token-url}") String accessTokenUrl
    ) {
        this.appId = appId;
        this.appSecret = appSecret;
        this.redirectBaseUrl = redirectBaseUrl;
        this.authorizeUrl = authorizeUrl;
        this.accessTokenUrl = accessTokenUrl;
        this.restTemplate = new RestTemplate();
    }

    public String buildAuthorizeUrl(String redirectUri, String state) {
        String finalRedirectUri = resolveRedirectUri(redirectUri);
        String encodedRedirectUri = URLEncoder.encode(finalRedirectUri, StandardCharsets.UTF_8);
        String safeState = state == null || state.isBlank() ? "silvertongue" : state;
        return String.format(
                "%s?appid=%s&redirect_uri=%s&response_type=code&scope=snsapi_login&state=%s#wechat_redirect",
                authorizeUrl, appId, encodedRedirectUri, safeState
        );
    }

    public WxAccessTokenResponse exchangeCodeForToken(String code) {
        String url = String.format("%s?appid=%s&secret=%s&code=%s&grant_type=authorization_code",
                accessTokenUrl, appId, appSecret, code);
        log.info("Exchanging WeChat code for token, url={}", url.replace(appSecret, "***"));

        WxAccessTokenResponse response = restTemplate.getForObject(url, WxAccessTokenResponse.class);
        if (response == null) {
            throw new RuntimeException("WeChat access_token response is null");
        }
        if (!response.isSuccess()) {
            log.error("WeChat OAuth2 error: errcode={}, errmsg={}", response.getErrCode(), response.getErrMsg());
            throw new RuntimeException("WeChat OAuth2 failed: " + response.getErrMsg());
        }
        return response;
    }

    private String resolveRedirectUri(String redirectUri) {
        if (redirectUri != null && !redirectUri.isBlank()) {
            return redirectUri;
        }
        if (redirectBaseUrl == null || redirectBaseUrl.isBlank()) {
            throw new IllegalArgumentException("WeChat redirect URI is not configured");
        }
        return redirectBaseUrl.endsWith("/")
                ? redirectBaseUrl.substring(0, redirectBaseUrl.length() - 1) + CALLBACK_PATH
                : redirectBaseUrl + CALLBACK_PATH;
    }
}
