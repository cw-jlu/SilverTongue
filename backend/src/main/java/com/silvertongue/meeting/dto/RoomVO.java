package com.silvertongue.meeting.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoomVO {

    private Long id;
    private String roomName;
    private Long creatorId;
    private int maxUsers;
    private int onlineCount;
    private Integer status;
    private LocalDateTime createTime;
}
