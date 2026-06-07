package com.silvertongue.coach.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("activity_log")
public class ActivityLog {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    private Long sessionId;

    /** input (听/读) | output (说/写) */
    private String activityType;

    /** shadowing | ai_chat | lookup | srs_review | chinglish */
    private String source;

    /** 活动时长（秒） */
    private Integer durationSeconds;

    private String description;

    private LocalDateTime createTime;
}
