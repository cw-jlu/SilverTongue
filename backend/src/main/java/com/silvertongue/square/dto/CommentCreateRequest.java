package com.silvertongue.square.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CommentCreateRequest {

    @NotNull(message = "postId must not be null")
    private Long postId;

    private Long parentId;

    @NotBlank(message = "content must not be blank")
    private String content;
}
