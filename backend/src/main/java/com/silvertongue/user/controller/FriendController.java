package com.silvertongue.user.controller;

import com.silvertongue.common.ApiResult;
import com.silvertongue.security.AuthenticatedUser;
import com.silvertongue.user.dto.FriendAcceptRequest;
import com.silvertongue.user.dto.FriendApplyRequest;
import com.silvertongue.user.dto.FriendRemarkRequest;
import com.silvertongue.user.dto.FriendVO;
import com.silvertongue.user.service.FriendshipService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/friend")
@RequiredArgsConstructor
public class FriendController {

    private final FriendshipService friendshipService;

    /**
     * 发起好友申请
     */
    @PostMapping("/apply")
    public ApiResult<Void> apply(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @Valid @RequestBody FriendApplyRequest request
    ) {
        friendshipService.apply(currentUser.getUserId(), request.getFriendId());
        return ApiResult.success();
    }

    /**
     * 通过好友申请
     */
    @PostMapping("/accept")
    public ApiResult<Void> accept(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @Valid @RequestBody FriendAcceptRequest request
    ) {
        friendshipService.accept(currentUser.getUserId(), request.getApplyId());
        return ApiResult.success();
    }

    /**
     * 好友列表
     */
    @GetMapping
    public ApiResult<List<FriendVO>> listDefault(@AuthenticationPrincipal AuthenticatedUser currentUser) {
        return ApiResult.success(friendshipService.listFriends(currentUser.getUserId()));
    }

    /**
     * 好友列表
     */
    @GetMapping("/list")
    public ApiResult<List<FriendVO>> list(@AuthenticationPrincipal AuthenticatedUser currentUser) {
        return ApiResult.success(friendshipService.listFriends(currentUser.getUserId()));
    }

    /**
     * 修改备注名
     */
    @PutMapping("/remark")
    public ApiResult<Void> remark(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @Valid @RequestBody FriendRemarkRequest request
    ) {
        friendshipService.remark(currentUser.getUserId(), request.getFriendId(), request.getRemark());
        return ApiResult.success();
    }

    /**
     * 屏蔽好友
     */
    @PostMapping("/block")
    public ApiResult<Void> block(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @Valid @RequestBody FriendApplyRequest request
    ) {
        friendshipService.block(currentUser.getUserId(), request.getFriendId());
        return ApiResult.success();
    }
}
