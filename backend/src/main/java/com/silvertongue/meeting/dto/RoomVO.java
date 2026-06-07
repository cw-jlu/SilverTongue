package com.silvertongue.meeting.dto;

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
public class RoomVO {

    private Long id;
    private String roomName;
    private Long creatorId;
    private int maxUsers;
    private int onlineCount;
    private Integer status;
    private LocalDateTime createTime;
    private List<ParticipantVO> participants;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ParticipantVO {
        private Long id;
        private Long userId;
        private String nickname;
        private String avatarUrl;
        private Integer role;
        private String aiRoleName;
        private String aiRoleSetting;
    }
}
