package com.silvertongue.harvester.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.silvertongue.harvester.dto.ClipCreateRequest;
import com.silvertongue.harvester.dto.ClipVO;
import com.silvertongue.harvester.entity.Clip;
import com.silvertongue.harvester.entity.Material;
import com.silvertongue.harvester.mapper.ClipMapper;
import com.silvertongue.harvester.mapper.MaterialMapper;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClipService {

    private static final Set<String> SUPPORTED_PLATFORMS = Set.of("youtube");

    private final ClipMapper clipMapper;
    private final MaterialMapper materialMapper;
    private final ObjectMapper objectMapper;
    private final MinioClient minioClient;

    @Value("${ai.agent.base-url:http://localhost:8089}")
    private String aiAgentBaseUrl;

    @Value("${minio.buckets.materials:st-materials}")
    private String materialsBucketName;

    @Transactional
    public ClipVO create(ClipCreateRequest request) {
        Material material = materialMapper.selectById(request.getMaterialId());
        if (material == null) {
            throw new IllegalArgumentException("material not found");
        }

        if (request.getStartTime().compareTo(request.getEndTime()) >= 0) {
            throw new IllegalArgumentException("startTime must be before endTime");
        }
        if (request.getStartTime().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("startTime must be non-negative");
        }

        Clip clip = new Clip();
        clip.setMaterialId(request.getMaterialId());
        clip.setStartTime(request.getStartTime());
        clip.setEndTime(request.getEndTime());
        clip.setContent(request.getContent());
        clip.setTranslation(request.getTranslation());
        clip.setCreateTime(LocalDateTime.now());
        clipMapper.insert(clip);

        log.info("Clip created: id={}, materialId={}, time=[{}-{}]",
                clip.getId(), request.getMaterialId(), request.getStartTime(), request.getEndTime());

        return toVO(clip);
    }

    public List<ClipVO> listByMaterial(Long materialId) {
        List<Clip> clips = clipMapper.selectList(new LambdaQueryWrapper<Clip>()
                .eq(Clip::getMaterialId, materialId)
                .orderByAsc(Clip::getStartTime));
        return clips.stream().map(this::toVO).collect(Collectors.toList());
    }

    public List<ClipVO> listAll(int page, int size) {
        int offset = (page - 1) * size;
        List<Clip> clips = clipMapper.selectList(new LambdaQueryWrapper<Clip>()
                .orderByDesc(Clip::getCreateTime)
                .last("LIMIT " + offset + "," + size));
        return clips.stream().map(this::toVO).collect(Collectors.toList());
    }

    @Transactional
    public ClipVO harvest(String url, String platform, Double startTime, Double endTime) {
        String normalizedPlatform = normalizePlatform(platform);
        validateHarvestRequest(url, startTime, endTime, normalizedPlatform);

        Material material = buildMaterial(url, normalizedPlatform);
        Material existing = materialMapper.selectOne(new LambdaQueryWrapper<Material>()
                .eq(Material::getMd5, material.getMd5()));
        if (existing != null) {
            material = existing;
            material.setStatus(1);
            material.setUpdateTime(LocalDateTime.now());
            materialMapper.updateById(material);
        } else {
            materialMapper.insert(material);
        }

        Clip clip = new Clip();
        clip.setMaterialId(material.getId());
        clip.setStartTime(BigDecimal.valueOf(startTime));
        clip.setEndTime(BigDecimal.valueOf(endTime));
        clip.setStatus(1);
        clip.setCreateTime(LocalDateTime.now());
        clipMapper.insert(clip);

        log.info("Harvest clip created: clipId={}, materialId={}, url={}, time=[{}-{}]",
                clip.getId(), material.getId(), url, startTime, endTime);

        dispatchHarvestJob(clip.getId(), material.getId(), url, startTime, endTime);
        return toVO(clip);
    }

    public ClipVO getStatus(Long clipId) {
        Clip clip = clipMapper.selectById(clipId);
        if (clip == null) {
            throw new IllegalArgumentException("clip not found");
        }
        return toVO(clip);
    }

    public String getPlaybackUrl(Long clipId) {
        Clip clip = clipMapper.selectById(clipId);
        if (clip == null) {
            throw new IllegalArgumentException("clip not found");
        }

        Material material = materialMapper.selectById(clip.getMaterialId());
        if (material == null) {
            throw new IllegalArgumentException("material not found");
        }

        if (material.getStoragePath() != null && !material.getStoragePath().isBlank()) {
            try {
                return minioClient.getPresignedObjectUrl(
                        GetPresignedObjectUrlArgs.builder()
                                .method(Method.GET)
                                .bucket(materialsBucketName)
                                .object(material.getStoragePath())
                                .expiry(1, TimeUnit.HOURS)
                                .build()
                );
            } catch (Exception e) {
                throw new IllegalStateException("failed to create playback url", e);
            }
        }

        if (material.getSourceUrl() != null && !material.getSourceUrl().isBlank()) {
            return material.getSourceUrl();
        }

        throw new IllegalStateException("clip media is not available");
    }

    public void updateStatus(Long clipId, int status, String storagePath) {
        Clip clip = clipMapper.selectById(clipId);
        if (clip == null) {
            return;
        }

        clip.setStatus(status);
        clipMapper.updateById(clip);

        Material material = materialMapper.selectById(clip.getMaterialId());
        if (material != null) {
            if (storagePath != null && !storagePath.isEmpty()) {
                material.setStoragePath(storagePath);
            }
            material.setStatus(status == 3 ? 2 : 4);
            material.setUpdateTime(LocalDateTime.now());
            materialMapper.updateById(material);
        }

        log.info("Clip status updated: clipId={}, status={}", clipId, status);
    }

    private Material buildMaterial(String url, String platform) {
        Material material = new Material();
        material.setMd5(DigestUtils.md5Hex(url));
        material.setTitle("Harvested from " + platform);
        material.setType("video");
        material.setSourceUrl(url);
        material.setStoragePath("pending/" + material.getMd5());
        material.setStatus(1);
        material.setCreateTime(LocalDateTime.now());
        material.setUpdateTime(LocalDateTime.now());
        return material;
    }

    private void dispatchHarvestJob(Long clipId, Long materialId, String url, Double startTime, Double endTime) {
        Thread dispatcherThread = new Thread(() -> {
            try {
                Map<String, Object> payload = Map.of(
                        "clipId", clipId,
                        "url", url,
                        "startTime", startTime,
                        "endTime", endTime
                );
                byte[] requestBody = toJson(payload).getBytes(StandardCharsets.UTF_8);
                HttpURLConnection connection = (HttpURLConnection) new URL(aiAgentBaseUrl + "/api/harvester/jobs")
                        .openConnection();
                connection.setRequestMethod("POST");
                connection.setDoOutput(true);
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(10000);
                connection.setRequestProperty("Content-Type", MediaType.APPLICATION_JSON_VALUE);
                connection.setRequestProperty("Accept", MediaType.APPLICATION_JSON_VALUE);
                connection.setFixedLengthStreamingMode(requestBody.length);
                try (OutputStream outputStream = connection.getOutputStream()) {
                    outputStream.write(requestBody);
                }
                int statusCode = connection.getResponseCode();
                if (statusCode / 100 != 2) {
                    throw new IllegalStateException("unexpected AI agent response: " + statusCode);
                }

                log.info("Harvest job dispatched to AI Agent: clipId={}, materialId={}, baseUrl={}",
                        clipId, materialId, aiAgentBaseUrl);
            } catch (Exception e) {
                markDispatchFailed(clipId, materialId, e);
            }
        }, "harvest-dispatch-" + clipId);
        dispatcherThread.setDaemon(true);
        dispatcherThread.start();
    }

    private String toJson(Map<String, Object> payload) throws JsonProcessingException {
        return objectMapper.writeValueAsString(payload);
    }

    private void validateHarvestRequest(String url, Double startTime, Double endTime, String platform) {
        if (!SUPPORTED_PLATFORMS.contains(platform)) {
            throw new IllegalArgumentException("unsupported platform: " + platform);
        }
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("url must not be blank");
        }
        if (startTime == null || endTime == null) {
            throw new IllegalArgumentException("startTime and endTime are required");
        }
        if (startTime < 0 || endTime <= startTime) {
            throw new IllegalArgumentException("startTime must be >= 0 and endTime must be greater than startTime");
        }
    }

    private String normalizePlatform(String platform) {
        return platform == null ? "" : platform.trim().toLowerCase();
    }

    private void markDispatchFailed(Long clipId, Long materialId, Exception e) {
        log.error("Failed to dispatch harvest job: clipId={}, materialId={}", clipId, materialId, e);

        Clip clip = clipMapper.selectById(clipId);
        if (clip != null) {
            clip.setStatus(4);
            clipMapper.updateById(clip);
        }

        Material material = materialMapper.selectById(materialId);
        if (material != null) {
            material.setStatus(4);
            material.setUpdateTime(LocalDateTime.now());
            materialMapper.updateById(material);
        }
    }

    private ClipVO toVO(Clip clip) {
        Material material = materialMapper.selectById(clip.getMaterialId());
        return ClipVO.builder()
                .id(clip.getId())
                .materialId(clip.getMaterialId())
                .title(material != null ? material.getTitle() : null)
                .startTime(clip.getStartTime())
                .endTime(clip.getEndTime())
                .content(clip.getContent())
                .translation(clip.getTranslation())
                .vectorId(clip.getVectorId())
                .sourceUrl(material != null ? material.getSourceUrl() : null)
                .audioPath("/api/clips/" + clip.getId() + "/audio")
                .status(clip.getStatus())
                .createTime(clip.getCreateTime())
                .build();
    }
}
