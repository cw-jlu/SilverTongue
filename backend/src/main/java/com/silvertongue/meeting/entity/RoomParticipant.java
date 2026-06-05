package com.silvertongue.meeting.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("room_participants")
public class RoomParticipant {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long roomId;

    private Long userId;

    private LocalDateTime joinTime;

    private LocalDateTime leaveTime;
}
