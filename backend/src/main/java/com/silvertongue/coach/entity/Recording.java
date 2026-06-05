package com.silvertongue.coach.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("recordings")
public class Recording {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long sessionId;

    private Long clipId;

    private String audioUrl;

    private BigDecimal score;

    private LocalDateTime createTime;
}
