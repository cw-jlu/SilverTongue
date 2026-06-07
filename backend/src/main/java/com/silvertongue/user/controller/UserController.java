package com.silvertongue.user.controller;

import com.silvertongue.common.ApiResult;
import com.silvertongue.security.AuthenticatedUser;
import com.silvertongue.user.dto.LoginRequest;
import com.silvertongue.user.dto.LoginResponse;
import com.silvertongue.user.dto.RegisterRequest;
import com.silvertongue.user.dto.UserProfileResponse;
import com.silvertongue.user.dto.WxBindRequest;
import com.silvertongue.user.service.WeChatService;
import com.silvertongue.user.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final WeChatService weChatService;

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

    /**
     * 微信 OAuth2 回调登录 — 前端引导用户跳转微信授权页后，微信回调时带 code 参数
     */
    @GetMapping("/wx/callback")
    public ApiResult<LoginResponse> wxCallback(@RequestParam("code") String code) {
        return ApiResult.success(userService.wxLogin(code));
    }

    @GetMapping("/wx/authorize-url")
    public ApiResult<String> wxAuthorizeUrl(
            HttpServletRequest request,
            @RequestParam(value = "state", required = false) String state
    ) {
        String redirectUri = request.getParameter("redirectUri");
        return ApiResult.success(weChatService.buildAuthorizeUrl(redirectUri, state));
    }

    /**
     * 已有密码账号绑定微信
     */
    @PostMapping("/bindWx")
    public ApiResult<UserProfileResponse> bindWx(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @Valid @RequestBody WxBindRequest request
    ) {
        return ApiResult.success(userService.bindWx(currentUser.getUserId(), request.getCode()));
    }
}
