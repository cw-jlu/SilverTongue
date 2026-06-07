package com.silvertongue.meeting.controller;

import com.silvertongue.common.ApiResult;
import com.silvertongue.meeting.dto.RoomCreateRequest;
import com.silvertongue.meeting.dto.RoomVO;
import com.silvertongue.meeting.service.MeetingService;
import com.silvertongue.security.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

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

    /** 获取房间详情 */
    @GetMapping("/{roomId}")
    public ApiResult<RoomVO> detail(@PathVariable Long roomId) {
        return ApiResult.success(meetingService.getRoomDetail(roomId));
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

    /** 邀请好友 */
    @PostMapping("/{roomId}/invite/{friendId}")
    public ApiResult<Void> invite(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long roomId,
            @PathVariable Long friendId
    ) {
        meetingService.inviteFriend(roomId, friendId, currentUser.getUserId());
        return ApiResult.success();
    }

    /** 添加 AI 成员 */
    @PostMapping("/{roomId}/ai")
    public ApiResult<Void> addAi(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long roomId,
            @RequestBody Map<String, String> body
    ) {
        String aiName = body.getOrDefault("aiName", "AI Assistant");
        String aiSetting = body.getOrDefault("aiSetting", "You are a helpful AI Assistant.");
        meetingService.addAiParticipant(roomId, aiName, aiSetting, currentUser.getUserId());
        return ApiResult.success();
    }

    /** 更新 AI 成员 */
    @PutMapping("/{roomId}/ai/{participantId}")
    public ApiResult<Void> updateAi(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long roomId,
            @PathVariable Long participantId,
            @RequestBody Map<String, String> body
    ) {
        String aiName = body.get("aiName");
        String aiSetting = body.get("aiSetting");
        meetingService.updateAiParticipant(roomId, participantId, aiName, aiSetting, currentUser.getUserId());
        return ApiResult.success();
    }

    /** 移除参与者 */
    @DeleteMapping("/{roomId}/participant/{participantId}")
    public ApiResult<Void> removeParticipant(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long roomId,
            @PathVariable Long participantId
    ) {
        meetingService.removeParticipant(roomId, participantId, currentUser.getUserId());
        return ApiResult.success();
    }
}
