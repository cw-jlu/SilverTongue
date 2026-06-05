package com.silvertongue.harvester.controller;

import com.silvertongue.common.ApiResult;
import com.silvertongue.harvester.dto.MaterialVO;
import com.silvertongue.harvester.service.MaterialService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@RestController
@RequestMapping("/api/material")
@RequiredArgsConstructor
public class MaterialController {

    private final MaterialService materialService;

    /**
     * 素材上传（MinIO + MD5 秒传去重）
     */
    @PostMapping("/upload")
    public ApiResult<MaterialVO> upload(@RequestParam("file") MultipartFile file) {
        try {
            return ApiResult.success(materialService.upload(file));
        } catch (Exception e) {
            log.error("Material upload failed", e);
            return ApiResult.error(500, "upload failed: " + e.getMessage());
        }
    }
}
