package com.silvertongue.meeting.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("rooms")
public class Room {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long creatorId;

    private String roomName;

    private Integer maxUsers;

    private Integer status;

    private LocalDateTime createTime;
}
