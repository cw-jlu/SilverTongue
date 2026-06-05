package com.silvertongue.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 已有账号绑定微信的请求
 */
@Data
public class WxBindRequest {

    @NotBlank(message = "code must not be blank")
    private String code;
}
