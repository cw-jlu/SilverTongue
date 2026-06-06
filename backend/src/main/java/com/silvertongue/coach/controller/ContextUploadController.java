package com.silvertongue.coach.controller;

import com.silvertongue.common.ApiResult;
import com.silvertongue.security.AuthenticatedUser;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/session")
@RequiredArgsConstructor
public class ContextUploadController {

    private final MinioClient minioClient;

    @Value("${minio.buckets.recordings:st-recordings}")
    private String bucketName;

    /**
     * 上传场景辅助材料 (简历/菜单等) 到 MinIO
     * @param file 辅助材料文件
     * @return 文件的 URL / ObjectName
     */
    @PostMapping("/upload_context")
    public ApiResult<String> uploadContext(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @RequestParam("file") MultipartFile file) {
        
        if (file.isEmpty()) {
            return ApiResult.error("File is empty");
        }

        try {
            String originalFilename = file.getOriginalFilename();
            String extension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }
            
            // 存入 contexts 目录
            String objectName = String.format("contexts/%d/%s%s", 
                    currentUser.getUserId(), UUID.randomUUID().toString(), extension);

            try (InputStream is = file.getInputStream()) {
                minioClient.putObject(PutObjectArgs.builder()
                        .bucket(bucketName)
                        .object(objectName)
                        .stream(is, file.getSize(), -1)
                        .contentType(file.getContentType())
                        .build());
            }

            log.info("Context file uploaded to MinIO: {}", objectName);
            // 这里返回 objectName 给前端，前端稍后提交时作为 contextFileUrl
            return ApiResult.success(objectName);
            
        } catch (Exception e) {
            log.error("Failed to upload context file", e);
            return ApiResult.error("Upload failed: " + e.getMessage());
        }
    }
}
