package com.silvertongue.square.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostVO {

    private Long id;
    private Long userId;
    private String nickname;
    private String avatarUrl;
    private String content;
    private Long clipId;
    private Integer likeCount;
    private LocalDateTime createTime;
    private List<CommentVO> comments;
}
