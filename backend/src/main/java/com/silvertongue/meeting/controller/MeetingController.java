package com.silvertongue.meeting.controller;

import com.silvertongue.common.ApiResult;
import com.silvertongue.meeting.dto.RoomCreateRequest;
import com.silvertongue.meeting.dto.RoomVO;
import com.silvertongue.meeting.service.MeetingService;
import com.silvertongue.security.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/room")
@RequiredArgsConstructor
public class MeetingController {

    private final MeetingService meetingService;

    /** 创建房间 */
    @PostMapping
    public ApiResult<RoomVO> create(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @Valid @RequestBody RoomCreateRequest request
    ) {
        return ApiResult.success(meetingService.createRoom(currentUser.getUserId(), request));
    }

    /** 活跃房间列表 */
    @GetMapping
    public ApiResult<List<RoomVO>> list() {
        return ApiResult.success(meetingService.listActiveRooms());
    }

    /** 加入房间 */
    @PostMapping("/{roomId}/join")
    public ApiResult<Void> join(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long roomId
    ) {
        meetingService.joinRoom(roomId, currentUser.getUserId());
        return ApiResult.success();
    }

    /** 离开房间 */
    @PostMapping("/{roomId}/leave")
    public ApiResult<Void> leave(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long roomId
    ) {
        meetingService.leaveRoom(roomId, currentUser.getUserId());
        return ApiResult.success();
    }
}
