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
public class CommentVO {

    private Long id;
    private Long postId;
    private Long userId;
    private String nickname;
    private String avatarUrl;
    private Long parentId;
    private String content;
    private LocalDateTime createTime;

    /** 子评论（二级回复） */
    private List<CommentVO> replies;
}
