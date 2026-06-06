package com.silvertongue.user.service;

import com.silvertongue.user.dto.WxAccessTokenResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * 微信 OAuth2 服务 — 负责与微信服务端交互
 */
@Slf4j
@Service
public class WeChatService {

    private final String appId;
    private final String appSecret;
    private final String accessTokenUrl;
    private final RestTemplate restTemplate;

    public WeChatService(
            @Value("${wechat.app-id}") String appId,
            @Value("${wechat.app-secret}") String appSecret,
            @Value("${wechat.oauth2.access-token-url}") String accessTokenUrl
    ) {
        this.appId = appId;
        this.appSecret = appSecret;
        this.accessTokenUrl = accessTokenUrl;
        this.restTemplate = new RestTemplate();
    }

    /**
     * 用临时 code 换取 access_token 及 openid / unionid
     */
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
}
