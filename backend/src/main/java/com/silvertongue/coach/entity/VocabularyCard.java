package com.silvertongue.coach.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("vocabulary_cards")
public class VocabularyCard {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    private String word;

    private String phoneticUs;

    private String dictionarySource;

    private String phrase;

    private Long contextClipId;

    private LocalDateTime nextReviewTime;

    private BigDecimal easeFactor;

    private Integer repetitions;

    private Integer reviewInterval;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
