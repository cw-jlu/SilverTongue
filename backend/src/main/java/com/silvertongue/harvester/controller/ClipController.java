package com.silvertongue.harvester.controller;

import com.silvertongue.common.ApiResult;
import com.silvertongue.harvester.dto.ClipCreateRequest;
import com.silvertongue.harvester.dto.ClipVO;
import com.silvertongue.harvester.service.ClipService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/clips")
@RequiredArgsConstructor
public class ClipController {

    private final ClipService clipService;

    /**
     * 创建切片
     */
    @PostMapping
    public ApiResult<ClipVO> create(@Valid @RequestBody ClipCreateRequest request) {
        return ApiResult.success(clipService.create(request));
    }

    /**
     * 查询某素材下的切片列表
     */
    @GetMapping("/material/{materialId}")
    public ApiResult<List<ClipVO>> listByMaterial(@PathVariable Long materialId) {
        return ApiResult.success(clipService.listByMaterial(materialId));
    }

    /**
     * 分页查询所有切片
     */
    @GetMapping
    public ApiResult<List<ClipVO>> listAll(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResult.success(clipService.listAll(page, size));
    }
}
