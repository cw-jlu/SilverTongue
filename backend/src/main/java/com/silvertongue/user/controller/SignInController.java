package com.silvertongue.user.controller;

import com.silvertongue.common.ApiResult;
import com.silvertongue.security.AuthenticatedUser;
import com.silvertongue.user.dto.CalendarDay;
import com.silvertongue.user.dto.SignInResponse;
import com.silvertongue.user.service.SignInService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/signin")
@RequiredArgsConstructor
public class SignInController {

    private final SignInService signInService;

    /**
     * 每日签到
     */
    @PostMapping
    public ApiResult<SignInResponse> signIn(@AuthenticationPrincipal AuthenticatedUser currentUser) {
        return ApiResult.success(signInService.signIn(currentUser.getUserId()));
    }

    /**
     * 月度签到日历
     */
    @GetMapping("/calendar")
    public ApiResult<List<CalendarDay>> calendar(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @RequestParam("year") int year,
            @RequestParam("month") int month
    ) {
        return ApiResult.success(signInService.calendar(currentUser.getUserId(), year, month));
    }
}
