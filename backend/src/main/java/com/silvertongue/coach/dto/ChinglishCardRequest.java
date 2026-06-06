package com.silvertongue.coach.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ChinglishCardRequest {

    @NotNull(message = "userId must not be null")
    private Long userId;

    @NotBlank(message = "originalExpression must not be blank")
    private String originalExpression;

    @NotBlank(message = "correction must not be blank")
    private String correction;

    /** 错误模式描述 (例如: "very like", "play phone") */
    private String errorPattern;

    /** 会话上下文 */
    private String context;
}
