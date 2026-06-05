package com.silvertongue.user.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class UserProfileResponse {

    private Long id;
    private String username;
    private String nickname;
    private String avatarUrl;
    private Long points;
    private String level;
    private Integer signInCount;
    private Integer status;
    private LocalDateTime createTime;
}
