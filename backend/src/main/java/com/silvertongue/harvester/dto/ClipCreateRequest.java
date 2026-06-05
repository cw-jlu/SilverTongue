package com.silvertongue.harvester.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ClipCreateRequest {

    @NotNull(message = "materialId must not be null")
    private Long materialId;

    @NotNull(message = "startTime must not be null")
    private BigDecimal startTime;

    @NotNull(message = "endTime must not be null")
    private BigDecimal endTime;

    private String content;

    private String translation;
}
