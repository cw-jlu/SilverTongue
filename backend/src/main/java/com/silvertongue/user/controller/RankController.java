package com.silvertongue.user.controller;

import com.silvertongue.common.ApiResult;
import com.silvertongue.user.dto.PointsRankItem;
import com.silvertongue.user.service.RankService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/rank")
@RequiredArgsConstructor
public class RankController {

    private final RankService rankService;

    /**
     * 积分排行榜 Top N（默认 20）
     */
    @GetMapping("/points")
    public ApiResult<List<PointsRankItem>> pointsRank(
            @RequestParam(defaultValue = "20") int top
    ) {
        if (top < 1 || top > 100) {
            top = 20;
        }
        return ApiResult.success(rankService.getPointsRank(top));
    }
}
