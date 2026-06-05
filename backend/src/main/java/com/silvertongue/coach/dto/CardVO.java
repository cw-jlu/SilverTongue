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
public class CardVO {

    private Long id;
    private String word;
    private String phoneticUs;
    private String dictionarySource;
    private String phrase;
    private Long contextClipId;
    private LocalDateTime nextReviewTime;
    private BigDecimal easeFactor;
    private Integer repetitions;
    private Integer reviewInterval;
}
