package com.silvertongue.user.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 微信 OAuth2 access_token 接口响应
 */
@Data
public class WxAccessTokenResponse {

    @JsonProperty("access_token")
    private String accessToken;

    @JsonProperty("expires_in")
    private Integer expiresIn;

    @JsonProperty("refresh_token")
    private String refreshToken;

    private String openid;

    private String scope;

    private String unionid;

    @JsonProperty("errcode")
    private Integer errCode;

    @JsonProperty("errmsg")
    private String errMsg;

    public boolean isSuccess() {
        return errCode == null || errCode == 0;
    }
}
