package com.silvertongue.coach.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("messages")
public class Message {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long sessionId;

    private String sender; // user, ai

    private String content;

    private String audioUrl;

    private String refinedContent;

    private String chinglishFeedback;

    private LocalDateTime createTime;
}
