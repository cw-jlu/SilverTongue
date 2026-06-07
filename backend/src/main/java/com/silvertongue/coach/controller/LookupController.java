package com.silvertongue.coach.controller;

import com.silvertongue.coach.dto.LookupRequest;
import com.silvertongue.coach.service.LookupService;
import com.silvertongue.common.ApiResult;
import com.silvertongue.security.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/lookup")
@RequiredArgsConstructor
public class LookupController {

    private final LookupService lookupService;

    /**
     * 记录用户查词。同一单词查询 ≥3 次自动创建 SRS 卡片。
     */
    @PostMapping
    public ApiResult<Void> recordLookup(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @Valid @RequestBody LookupRequest request
    ) {
        lookupService.recordLookup(currentUser.getUserId(), request.getWord(), request.getClipId());
        return ApiResult.success();
    }
}
