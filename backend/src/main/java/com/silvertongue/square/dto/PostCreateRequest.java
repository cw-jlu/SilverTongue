package com.silvertongue.square.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PostCreateRequest {

    @NotBlank(message = "content must not be blank")
    private String content;

    private Long clipId;
}
