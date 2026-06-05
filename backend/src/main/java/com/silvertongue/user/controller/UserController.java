package com.silvertongue.user.controller;

import com.silvertongue.common.ApiResult;
import com.silvertongue.security.AuthenticatedUser;
import com.silvertongue.user.dto.LoginRequest;
import com.silvertongue.user.dto.LoginResponse;
import com.silvertongue.user.dto.RegisterRequest;
import com.silvertongue.user.dto.UserProfileResponse;
import com.silvertongue.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping("/register")
    public ApiResult<UserProfileResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResult.success(userService.register(request));
    }

    @PostMapping("/login")
    public ApiResult<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResult.success(userService.login(request));
    }

    @GetMapping("/me")
    public ApiResult<UserProfileResponse> me(@AuthenticationPrincipal AuthenticatedUser currentUser) {
        return ApiResult.success(userService.getProfile(currentUser.getUserId()));
    }
}
