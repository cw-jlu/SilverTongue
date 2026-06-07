package com.silvertongue.shadowing.controller;

import com.silvertongue.coach.grpc.AssessmentGrpcClient;
import com.silvertongue.common.ApiResult;
import com.silvertongue.grpc.assessment.AssessResponse;
import com.silvertongue.grpc.assessment.WordAssessment;
import com.silvertongue.grpc.assessment.PhonemeDetail;
import com.silvertongue.security.AuthenticatedUser;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 影子跟读 REST 控制器
 *
 * POST /api/shadowing/record — 上传跟读录音 → MFA 音素评测 → 返回评分 + 对齐数据
 * 前端 Shadowing.jsx 使用返回的 phoneme 时间轴渲染波形高亮和音高对比
 */
@Slf4j
@RestController
@RequestMapping("/api/shadowing")
@RequiredArgsConstructor
public class ShadowingController {

    private final MinioClient minioClient;
    private final AssessmentGrpcClient assessmentClient;

    @Value("${minio.buckets.recordings:st-recordings}")
    private String bucketName;

    @PostMapping("/record")
    public ApiResult<Map<String, Object>> record(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @RequestParam("clipId") Long clipId,
            @RequestParam(value = "targetText", required = false, defaultValue = "") String targetText,
            @RequestParam("audio") MultipartFile audio) {

        try {
            // 1. 上传录音到 MinIO（按用户 ID 分目录）
            String objectName = String.format("shadowing/%d/%d_%s",
                    currentUser.getUserId(), clipId, UUID.randomUUID(),
                    audio.getOriginalFilename() != null ? audio.getOriginalFilename() : "recording.webm");
            byte[] audioBytes = audio.getBytes();

            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectName)
                    .stream(new ByteArrayInputStream(audioBytes), audioBytes.length, -1)
                    .contentType(audio.getContentType() != null ? audio.getContentType() : "audio/webm")
                    .build());

            log.info("Shadowing recording uploaded: userId={}, clipId={}, path={}, size={} bytes",
                    currentUser.getUserId(), clipId, objectName, audioBytes.length);

            // 2. 调用 gRPC 发音评估 (MFA 对齐 + 评分)
            Map<String, Object> assessment = null;
            if (targetText != null && !targetText.isBlank()) {
                try {
                    AssessResponse response = assessmentClient.assessPronunciation(
                            String.valueOf(clipId), audioBytes, targetText);

                    assessment = new LinkedHashMap<>();
                    assessment.put("finalScore", response.getFinalScore());
                    assessment.put("accuracy", response.getAccuracy());
                    assessment.put("fluency", response.getFluency());
                    assessment.put("completeness", response.getCompleteness());

                    // 词级评分 + 音素时间轴（前端波形高亮用）
                    List<Map<String, Object>> words = new ArrayList<>();
                    for (WordAssessment w : response.getWordsList()) {
                        Map<String, Object> wordMap = new LinkedHashMap<>();
                        wordMap.put("word", w.getWord());
                        wordMap.put("score", w.getScore());

                        List<Map<String, Object>> phonemes = new ArrayList<>();
                        for (PhonemeDetail p : w.getPhonemesList()) {
                            Map<String, Object> pMap = new LinkedHashMap<>();
                            pMap.put("phoneme", p.getPhoneme());
                            pMap.put("score", p.getScore());
                            pMap.put("startTime", p.getStartTime());
                            pMap.put("endTime", p.getEndTime());
                            phonemes.add(pMap);
                        }
                        wordMap.put("phonemes", phonemes);
                        words.add(wordMap);
                    }
                    assessment.put("words", words);

                    log.info("Shadowing assessment: userId={}, clipId={}, score={}",
                            currentUser.getUserId(), clipId, response.getFinalScore());
                } catch (Exception e) {
                    log.error("gRPC assessment failed for userId={}, clipId={}: {}",
                            currentUser.getUserId(), clipId, e.getMessage());
                    assessment = Map.of("error", e.getMessage());
                }
            }

            // 3. 返回结果
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("clipId", clipId);
            result.put("audioUrl", objectName);
            result.put("targetText", targetText);
            result.put("assessment", assessment);

            return ApiResult.success(result);

        } catch (Exception e) {
            log.error("Shadowing record failed for userId={}, clipId={}: {}",
                    currentUser.getUserId(), clipId, e.getMessage(), e);
            return ApiResult.error(500, "recording failed: " + e.getMessage());
        }
    }
}
