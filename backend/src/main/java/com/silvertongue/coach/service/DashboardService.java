package com.silvertongue.coach.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.silvertongue.coach.dto.DashboardProgressVO;
import com.silvertongue.coach.dto.HeatmapDay;
import com.silvertongue.coach.entity.ActivityLog;
import com.silvertongue.coach.entity.VocabularyCard;
import com.silvertongue.coach.mapper.ActivityLogMapper;
import com.silvertongue.coach.mapper.VocabularyCardMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardService {

    private static final double TARGET_HOURS = 1000.0;

    private final ActivityLogMapper activityLogMapper;
    private final VocabularyCardMapper cardMapper;

    /**
     * 月度热力图数据 — 按天汇总练习时长
     */
    public List<HeatmapDay> heatmap(Long userId, int year, int month) {
        YearMonth ym = YearMonth.of(year, month);
        LocalDateTime monthStart = ym.atDay(1).atStartOfDay();
        LocalDateTime monthEnd = ym.atEndOfMonth().atTime(23, 59, 59);

        // 查询当月所有活动日志
        List<ActivityLog> logs = activityLogMapper.selectList(
                new LambdaQueryWrapper<ActivityLog>()
                        .eq(ActivityLog::getUserId, userId)
                        .between(ActivityLog::getCreateTime, monthStart, monthEnd)
        );

        // 按天分组聚合
        Map<Integer, List<ActivityLog>> byDay = logs.stream()
                .collect(Collectors.groupingBy(log -> log.getCreateTime().getDayOfMonth()));

        int daysInMonth = ym.lengthOfMonth();
        List<HeatmapDay> result = new ArrayList<>();
        for (int day = 1; day <= daysInMonth; day++) {
            List<ActivityLog> dayLogs = byDay.getOrDefault(day, List.of());
            int totalSeconds = dayLogs.stream().mapToInt(ActivityLog::getDurationSeconds).sum();
            int inputSeconds = dayLogs.stream()
                    .filter(l -> "input".equals(l.getActivityType()))
                    .mapToInt(ActivityLog::getDurationSeconds).sum();
            int outputSeconds = dayLogs.stream()
                    .filter(l -> "output".equals(l.getActivityType()))
                    .mapToInt(ActivityLog::getDurationSeconds).sum();

            result.add(HeatmapDay.builder()
                    .day(day)
                    .totalSeconds(totalSeconds)
                    .inputSeconds(inputSeconds)
                    .outputSeconds(outputSeconds)
                    .build());
        }

        return result;
    }

    /**
     * 仪表盘进度概览 — 1000h 目标追踪
     */
    public DashboardProgressVO progress(Long userId, int signInCount) {
        // 查询全部活动日志
        List<ActivityLog> allLogs = activityLogMapper.selectList(
                new LambdaQueryWrapper<ActivityLog>()
                        .eq(ActivityLog::getUserId, userId)
        );

        int totalSeconds = allLogs.stream().mapToInt(ActivityLog::getDurationSeconds).sum();
        int inputSeconds = allLogs.stream()
                .filter(l -> "input".equals(l.getActivityType()))
                .mapToInt(ActivityLog::getDurationSeconds).sum();
        int outputSeconds = allLogs.stream()
                .filter(l -> "output".equals(l.getActivityType()))
                .mapToInt(ActivityLog::getDurationSeconds).sum();

        double totalHours = totalSeconds / 3600.0;
        double inputHours = inputSeconds / 3600.0;
        double outputHours = outputSeconds / 3600.0;
        double percent = Math.min(totalHours / TARGET_HOURS * 100.0, 100.0);
        double remaining = Math.max(TARGET_HOURS - totalHours, 0);

        // SRS 卡片统计
        LocalDateTime now = LocalDateTime.now();
        long dueCards = cardMapper.selectCount(
                new LambdaQueryWrapper<VocabularyCard>()
                        .eq(VocabularyCard::getUserId, userId)
                        .le(VocabularyCard::getNextReviewTime, now)
        );
        long totalCards = cardMapper.selectCount(
                new LambdaQueryWrapper<VocabularyCard>()
                        .eq(VocabularyCard::getUserId, userId)
        );

        return DashboardProgressVO.builder()
                .totalHours(round1(totalHours))
                .inputHours(round1(inputHours))
                .outputHours(round1(outputHours))
                .percentTo1000(round1(percent))
                .remainingHours(round1(remaining))
                .dueCardsCount((int) dueCards)
                .totalCardsCount((int) totalCards)
                .signInCount(signInCount)
                .build();
    }

    private double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
