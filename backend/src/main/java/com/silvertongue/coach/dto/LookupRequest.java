package com.silvertongue.coach.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LookupRequest {

    @NotBlank(message = "word must not be blank")
    private String word;

    /** 可选：关联的视频切片 ID */
    private Long clipId;
}
