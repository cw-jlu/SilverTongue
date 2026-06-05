package com.silvertongue.coach.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("practice_sessions")
public class PracticeSession {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    /** shadowing, ai_chat */
    private String type;

    /** full_duplex, half_duplex, guided, free_talk */
    private String mode;

    /** 0: 进行中, 1: 已完成 */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
