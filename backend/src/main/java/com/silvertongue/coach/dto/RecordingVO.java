package com.silvertongue.coach.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecordingVO {

    private Long id;
    private Long sessionId;
    private Long clipId;
    private String audioUrl;
    private BigDecimal score;
    private LocalDateTime createTime;
}
