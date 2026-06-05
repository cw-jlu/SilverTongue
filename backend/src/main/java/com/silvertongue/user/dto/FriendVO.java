package com.silvertongue.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FriendVO {

    private Long friendId;
    private String nickname;
    private String avatarUrl;
    private String remark;
    private Integer status;         // 1: 正常好友, 2: 已屏蔽
    private String level;
    private LocalDateTime becomeTime;
}
