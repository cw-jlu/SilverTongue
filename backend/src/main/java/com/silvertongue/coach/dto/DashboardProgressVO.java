package com.silvertongue.coach.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 仪表盘进度概览 — 1000 小时热力图追踪
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardProgressVO {

    /** 总练习时长（小时，保留 1 位小数） */
    private double totalHours;

    /** 输入时长（小时） */
    private double inputHours;

    /** 输出时长（小时） */
    private double outputHours;

    /** 距 1000 小时的完成百分比 (0.0 ~ 100.0) */
    private double percentTo1000;

    /** 剩余小时数 */
    private double remainingHours;

    /** SRS 待复习卡片数 */
    private int dueCardsCount;

    /** SRS 卡片总数 */
    private int totalCardsCount;

    /** 累计签到天数 */
    private int signInCount;
}
