package com.silvertongue.meeting.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("topic_cards")
public class TopicCard {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long roomId;

    private String type;

    private String content;

    private String translation;

    private Integer displayOrder;

    private LocalDateTime createTime;
}
