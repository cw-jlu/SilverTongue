package com.silvertongue.harvester.controller;

import com.silvertongue.common.ApiResult;
import com.silvertongue.harvester.dto.ClipCreateRequest;
import com.silvertongue.harvester.dto.ClipVO;
import com.silvertongue.harvester.dto.HarvestCallbackRequest;
import com.silvertongue.harvester.dto.HarvestClipRequest;
import com.silvertongue.harvester.service.ClipService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
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

    /**
     * 浏览器插件采集：一键创建 Material + Clip
     */
    @PostMapping("/harvest")
    public ApiResult<ClipVO> harvest(@Valid @RequestBody HarvestClipRequest request) {
        return ApiResult.success(
                clipService.harvest(request.getUrl(), request.getPlatform(),
                        request.getStartTime(), request.getEndTime())
        );
    }

    /**
     * 查询切片处理状态
     */
    @GetMapping("/status/{id}")
    public ApiResult<ClipVO> status(@PathVariable Long id) {
        return ApiResult.success(clipService.getStatus(id));
    }

    /**
     * Python Agent 回调：更新切片状态
     */
    @GetMapping("/{id}/audio")
    public ResponseEntity<?> audio(@PathVariable Long id) {
        try {
            java.io.InputStream stream = clipService.getAudioStream(id);
            if (stream != null) {
                return ResponseEntity.ok()
                        .contentType(org.springframework.http.MediaType.parseMediaType("audio/mpeg"))
                        .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"clip_" + id + ".mp3\"")
                        .body(new org.springframework.core.io.InputStreamResource(stream));
            }

            String redirectUrl = clipService.getPlaybackUrl(id);
            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(URI.create(redirectUrl))
                    .build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/callback")
    public ApiResult<String> callback(@RequestBody HarvestCallbackRequest request) {
        clipService.updateStatus(request.getClipId(), request.getStatus(), request.getStoragePath());
        return ApiResult.success("ok");
    }
}
