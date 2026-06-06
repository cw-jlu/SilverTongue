package com.silvertongue.coach.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 热力图单日数据点
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HeatmapDay {

    /** 日期序号 (1-31) */
    private int day;

    /** 当天练习总时长（秒） */
    private int totalSeconds;

    /** 当天输入时长（秒） */
    private int inputSeconds;

    /** 当天输出时长（秒） */
    private int outputSeconds;
}
