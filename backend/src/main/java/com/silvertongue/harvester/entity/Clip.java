package com.silvertongue.harvester.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("clips")
public class Clip {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long materialId;

    private BigDecimal startTime;

    private BigDecimal endTime;

    private String content;

    private String translation;

    private String vectorId;

    private LocalDateTime createTime;
}
