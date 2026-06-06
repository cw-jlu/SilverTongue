package com.silvertongue.harvester.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 浏览器插件发来的语料采集请求
 */
@Data
public class HarvestClipRequest {

    @NotBlank(message = "url must not be blank")
    private String url;

    /** youtube, netflix */
    @NotBlank(message = "platform must not be blank")
    private String platform;

    @NotNull(message = "startTime must not be null")
    private Double startTime;

    @NotNull(message = "endTime must not be null")
    private Double endTime;
}
