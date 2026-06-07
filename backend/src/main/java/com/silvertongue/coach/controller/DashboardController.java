package com.silvertongue.coach.controller;

import com.silvertongue.coach.dto.DashboardProgressVO;
import com.silvertongue.coach.dto.HeatmapDay;
import com.silvertongue.coach.service.DashboardService;
import com.silvertongue.common.ApiResult;
import com.silvertongue.security.AuthenticatedUser;
import com.silvertongue.user.entity.User;
import com.silvertongue.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;
    private final UserMapper userMapper;

    /**
     * 月度热力图 — 每日练习时长（秒）
     */
    @GetMapping("/heatmap")
    public ApiResult<List<HeatmapDay>> heatmap(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @RequestParam int year,
            @RequestParam int month
    ) {
        return ApiResult.success(dashboardService.heatmap(currentUser.getUserId(), year, month));
    }

    /**
     * 仪表盘进度概览 — 1000h 追踪 + 输入/输出分解
     */
    @GetMapping("/progress")
    public ApiResult<DashboardProgressVO> progress(
            @AuthenticationPrincipal AuthenticatedUser currentUser
    ) {
        User user = userMapper.selectById(currentUser.getUserId());
        int signInCount = user != null ? user.getSignInCount() : 0;
        return ApiResult.success(dashboardService.progress(currentUser.getUserId(), signInCount));
    }
}
