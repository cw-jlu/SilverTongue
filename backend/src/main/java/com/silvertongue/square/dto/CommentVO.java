package com.silvertongue.square.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommentVO {

    private Long id;
    private Long postId;
    private Long userId;
    private String nickname;
    private String avatarUrl;
    private Long parentId;
    private String content;
    private LocalDateTime createTime;
}
