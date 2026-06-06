package com.silvertongue.harvester.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.silvertongue.harvester.dto.MaterialVO;
import com.silvertongue.harvester.entity.Material;
import com.silvertongue.harvester.mapper.MaterialMapper;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.DigestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MaterialService {

    private final MinioClient minioClient;
    private final MaterialMapper materialMapper;

    @Value("${minio.buckets.materials:st-materials}")
    private String bucketName;

    /**
     * 素材上传 — MinIO + MD5 秒传去重
     */
    @Transactional
    public MaterialVO upload(MultipartFile file) throws Exception {
        // 1. 计算 MD5
        String md5 = DigestUtils.md5DigestAsHex(file.getInputStream());

        // 2. MD5 秒传去重
        Material existing = materialMapper.selectOne(new LambdaQueryWrapper<Material>()
                .eq(Material::getMd5, md5)
                .last("LIMIT 1"));
        if (existing != null) {
            log.info("MD5 duplicate found, skip upload: md5={}, existingId={}", md5, existing.getId());
            return toVO(existing);
        }

        // 3. 上传 MinIO
        String originalFilename = file.getOriginalFilename();
        String objectName = String.format("materials/%s/%s", md5.substring(0, 2), UUID.randomUUID() + "_" + (originalFilename != null ? originalFilename : "unknown"));
        byte[] bytes = file.getBytes();

        minioClient.putObject(PutObjectArgs.builder()
                .bucket(bucketName)
                .object(objectName)
                .stream(new ByteArrayInputStream(bytes), bytes.length, -1)
                .contentType(file.getContentType() != null ? file.getContentType() : "application/octet-stream")
                .build());

        // 4. 写 MySQL
        String title = originalFilename != null ? originalFilename : "Untitled";
        // 去掉扩展名作为标题
        if (title.contains(".")) {
            title = title.substring(0, title.lastIndexOf('.'));
        }

        LocalDateTime now = LocalDateTime.now();
        Material material = new Material();
        material.setMd5(md5);
        material.setTitle(title);
        material.setType(detectType(file.getContentType()));
        material.setStoragePath(objectName);
        material.setStatus(0);
        material.setCreateTime(now);
        material.setUpdateTime(now);
        materialMapper.insert(material);

        log.info("Material uploaded: id={}, md5={}, path={}", material.getId(), md5, objectName);

        return toVO(material);
    }

    private String detectType(String contentType) {
        if (contentType == null) return "ebook";
        if (contentType.startsWith("video/")) return "video";
        if (contentType.startsWith("audio/")) return "audio";
        return "ebook";
    }

    private MaterialVO toVO(Material m) {
        return MaterialVO.builder()
                .id(m.getId())
                .md5(m.getMd5())
                .title(m.getTitle())
                .type(m.getType())
                .sourceUrl(m.getSourceUrl())
                .metadata(m.getMetadata())
                .storagePath(m.getStoragePath())
                .status(m.getStatus())
                .createTime(m.getCreateTime())
                .build();
    }
}
