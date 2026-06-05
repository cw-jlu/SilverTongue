package com.silvertongue.coach.controller;

import com.silvertongue.coach.dto.RecordingVO;
import com.silvertongue.coach.dto.SessionCreateRequest;
import com.silvertongue.coach.dto.SessionVO;
import com.silvertongue.coach.service.RecordingService;
import com.silvertongue.coach.service.SessionService;
import com.silvertongue.common.ApiResult;
import com.silvertongue.security.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@RestController
@RequestMapping("/api/session")
@RequiredArgsConstructor
public class SessionController {

    private final SessionService sessionService;
    private final RecordingService recordingService;

    /**
     * 创建练习会话
     */
    @PostMapping("/create")
    public ApiResult<SessionVO> create(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @Valid @RequestBody SessionCreateRequest request
    ) {
        return ApiResult.success(sessionService.create(currentUser.getUserId(), request));
    }

    /**
     * 结束会话
     */
    @PostMapping("/{id}/complete")
    public ApiResult<SessionVO> complete(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long id
    ) {
        return ApiResult.success(sessionService.complete(id, currentUser.getUserId()));
    }

    /**
     * 提交练习录音（上传 MinIO + gRPC 评分）
     */
    @PostMapping("/{id}/recording")
    public ApiResult<RecordingVO> submitRecording(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long id,
            @RequestParam("audio") MultipartFile audio,
            @RequestParam(value = "clipId", required = false) Long clipId,
            @RequestParam(value = "targetText", required = false) String targetText
    ) {
        try {
            return ApiResult.success(recordingService.submit(id, currentUser.getUserId(), clipId, targetText, audio));
        } catch (Exception e) {
            log.error("Recording submission failed: sessionId={}", id, e);
            return ApiResult.error(500, "recording failed: " + e.getMessage());
        }
    }
}
