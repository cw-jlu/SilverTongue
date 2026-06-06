package com.silvertongue.coach.service;

import com.silvertongue.coach.dto.RecordingVO;
import com.silvertongue.coach.entity.PracticeSession;
import com.silvertongue.coach.entity.Recording;
import com.silvertongue.coach.grpc.AssessmentGrpcClient;
import com.silvertongue.coach.mapper.PracticeSessionMapper;
import com.silvertongue.coach.mapper.RecordingMapper;
import com.silvertongue.grpc.assessment.AssessResponse;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecordingService {

    private final MinioClient minioClient;
    private final RecordingMapper recordingMapper;
    private final PracticeSessionMapper sessionMapper;
    private final AssessmentGrpcClient assessmentClient;

    @Value("${minio.buckets.recordings:st-recordings}")
    private String bucketName;

    /**
     * 提交练习录音 → MinIO 存储 → gRPC 发音评测 → 写入评分
     */
    @Transactional
    public RecordingVO submit(Long sessionId, Long userId, Long clipId, String targetText, MultipartFile audio) throws Exception {
        // 1. 校验会话
        PracticeSession session = sessionMapper.selectById(sessionId);
        if (session == null || !session.getUserId().equals(userId)) {
            throw new IllegalArgumentException("session not found");
        }
        if (session.getStatus() == 1) {
            throw new IllegalArgumentException("session already completed");
        }

        // 2. 上传录音到 MinIO
        String objectName = String.format("recordings/%d/%s_%s",
                userId, UUID.randomUUID(), audio.getOriginalFilename() != null ? audio.getOriginalFilename() : "recording.webm");
        byte[] audioBytes = audio.getBytes();

        minioClient.putObject(PutObjectArgs.builder()
                .bucket(bucketName)
                .object(objectName)
                .stream(new ByteArrayInputStream(audioBytes), audioBytes.length, -1)
                .contentType(audio.getContentType() != null ? audio.getContentType() : "audio/webm")
                .build());

        // 3. 调用 gRPC 发音评估
        BigDecimal score = null;
        if (targetText != null && !targetText.isBlank()) {
            try {
                AssessResponse response = assessmentClient.assessPronunciation(
                        userId.toString(), audioBytes, targetText);
                score = BigDecimal.valueOf(response.getFinalScore()).setScale(2, RoundingMode.HALF_UP);
            } catch (Exception e) {
                // gRPC 评分失败不阻塞录音保存
                log.error("gRPC pronunciation assessment failed for session={}, userId={}", sessionId, userId, e);
            }
        }

        // 4. 写 recording 记录
        LocalDateTime now = LocalDateTime.now();
        Recording recording = new Recording();
        recording.setSessionId(sessionId);
        recording.setClipId(clipId);
        recording.setAudioUrl(objectName);
        recording.setScore(score);
        recording.setCreateTime(now);
        recordingMapper.insert(recording);

        log.info("Recording saved: id={}, sessionId={}, score={}, path={}", recording.getId(), sessionId, score, objectName);

        return toVO(recording);
    }

    private RecordingVO toVO(Recording r) {
        return RecordingVO.builder()
                .id(r.getId())
                .sessionId(r.getSessionId())
                .clipId(r.getClipId())
                .audioUrl(r.getAudioUrl())
                .score(r.getScore())
                .createTime(r.getCreateTime())
                .build();
    }
}
