package com.silvertongue.coach.controller;

import com.silvertongue.coach.dto.CardReviewRequest;
import com.silvertongue.coach.dto.CardVO;
import com.silvertongue.coach.dto.ChinglishCardRequest;
import com.silvertongue.coach.service.ChinglishCardService;
import com.silvertongue.coach.service.SrsService;
import com.silvertongue.common.ApiResult;
import com.silvertongue.security.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/card")
@RequiredArgsConstructor
public class CardController {

    private final SrsService srsService;
    private final ChinglishCardService chinglishCardService;

    /**
     * 获取今日待复习卡片
     */
    @GetMapping("/due")
    public ApiResult<List<CardVO>> getDue(@AuthenticationPrincipal AuthenticatedUser currentUser) {
        return ApiResult.success(srsService.getDueCards(currentUser.getUserId()));
    }

    /**
     * 复习反馈 (SuperMemo-2)
     */
    @PostMapping("/review")
    public ApiResult<CardVO> review(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @Valid @RequestBody CardReviewRequest request
    ) {
        return ApiResult.success(srsService.review(currentUser.getUserId(), request));
    }

    /**
     * Chinglish 检测结果 → 自动创建 SRS 卡片
     */
    @PostMapping("/chinglish")
    public ApiResult<Void> createFromChinglish(
            @Valid @RequestBody ChinglishCardRequest request
    ) {
        chinglishCardService.createFromChinglish(
                request.getUserId(),
                request.getOriginalExpression(),
                request.getCorrection(),
                request.getErrorPattern(),
                request.getContext()
        );
        return ApiResult.success();
    }
}
