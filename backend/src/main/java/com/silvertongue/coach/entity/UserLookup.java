package com.silvertongue.coach.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_lookups")
public class UserLookup {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    private String word;

    private Long clipId;

    private LocalDateTime createTime;
}
