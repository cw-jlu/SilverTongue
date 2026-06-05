package com.silvertongue.harvester.dto;

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
public class ClipVO {

    private Long id;
    private Long materialId;
    private BigDecimal startTime;
    private BigDecimal endTime;
    private String content;
    private String translation;
    private String vectorId;
    private LocalDateTime createTime;
}
