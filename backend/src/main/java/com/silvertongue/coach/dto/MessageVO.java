package com.silvertongue.coach.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageVO {
    private Long id;
    private Long sessionId;
    private String sender; // user, ai
    private String content;
    private String audioUrl;
    private String audioBase64;
    private String refinedContent;
    private String chinglishFeedback;
    private LocalDateTime createTime;
    private String userTranscript; // 用于在语音聊天的响应中把识别出的用户文字吐给前端
}
