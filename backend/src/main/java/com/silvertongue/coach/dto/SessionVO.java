package com.silvertongue.coach.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionVO {

    private Long id;
    private Long userId;
    private String type;
    private String mode;
    private String topic;
    private String contextFileUrl;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
