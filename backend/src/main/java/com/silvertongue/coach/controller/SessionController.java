package com.silvertongue.coach.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.silvertongue.coach.dto.MessageRequest;
import com.silvertongue.coach.dto.MessageVO;
import com.silvertongue.coach.dto.RecordingVO;
import com.silvertongue.coach.dto.SessionCreateRequest;
import com.silvertongue.coach.dto.SessionVO;
import com.silvertongue.coach.entity.Message;
import com.silvertongue.coach.entity.PracticeSession;
import com.silvertongue.coach.grpc.AgentGrpcClient;
import com.silvertongue.coach.mapper.MessageMapper;
import com.silvertongue.coach.mapper.PracticeSessionMapper;
import com.silvertongue.coach.service.RecordingService;
import com.silvertongue.coach.service.SessionService;
import com.silvertongue.common.ApiResult;
import com.silvertongue.security.AuthenticatedUser;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/session")
@RequiredArgsConstructor
public class SessionController {

    private final SessionService sessionService;
    private final RecordingService recordingService;
    private final AgentGrpcClient agentGrpcClient;
    private final MessageMapper messageMapper;
    private final PracticeSessionMapper practiceSessionMapper;
    private final MinioClient minioClient;

    @Value("${minio.buckets.recordings:st-recordings}")
    private String bucketName;

    /**
     * 创建练习会话
     */
    @PostMapping("/create")
    public ApiResult<SessionVO> create(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @Valid @RequestBody SessionCreateRequest request
    ) {
        return ApiResult.success(sessionService.create(currentUser.getUserId(), request));
    }

    /**
     * 结束会话
     */
    @PostMapping("/{id}/complete")
    public ApiResult<SessionVO> complete(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long id
    ) {
        return ApiResult.success(sessionService.complete(id, currentUser.getUserId()));
    }

    /**
     * 提交练习录音（上传 MinIO + gRPC 评分）
     */
    @PostMapping("/{id}/recording")
    public ApiResult<RecordingVO> submitRecording(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long id,
            @RequestParam("audio") MultipartFile audio,
            @RequestParam(value = "clipId", required = false) Long clipId,
            @RequestParam(value = "targetText", required = false) String targetText
    ) {
        try {
            return ApiResult.success(recordingService.submit(id, currentUser.getUserId(), clipId, targetText, audio));
        } catch (Exception e) {
            log.error("Recording submission failed: sessionId={}", id, e);
            return ApiResult.error(500, "recording failed: " + e.getMessage());
        }
    }

    /**
     * 口语对练聊天接口（支持文本与音频）
     */
    @PostMapping("/{id}/chat")
    public ApiResult<MessageVO> chat(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long id,
            @RequestBody MessageRequest request
    ) {
        try {
            // 1. 验证会话存在
            PracticeSession session = practiceSessionMapper.selectById(id);
            if (session == null) {
                return ApiResult.error(404, "Session not found");
            }

            // 2. 解析输入字节
            byte[] inputBytes;
            String userAudioUrl = null;
            if ("audio".equalsIgnoreCase(request.getType())) {
                inputBytes = Base64.getDecoder().decode(request.getAudioBase64());
                // 保存录音到 MinIO
                String objectName = "chat/user_" + UUID.randomUUID() + ".wav";
                try (ByteArrayInputStream bais = new ByteArrayInputStream(inputBytes)) {
                    minioClient.putObject(PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .stream(bais, inputBytes.length, -1)
                            .contentType("audio/wav")
                            .build());
                    userAudioUrl = "/" + bucketName + "/" + objectName;
                }
            } else {
                inputBytes = request.getContent().getBytes(StandardCharsets.UTF_8);
            }

            // 3. 调用 AI Agent (gRPC ChatStream)
            AgentGrpcClient.ChatStreamResult agentResult = agentGrpcClient.chatStream(String.valueOf(id), inputBytes);

            // 4. 保存用户发送的消息
            String userText = request.getContent();
            if ("audio".equalsIgnoreCase(request.getType())) {
                userText = agentResult.getUserTranscript();
                if (userText == null || userText.isBlank()) {
                    userText = "[语音输入]";
                }
            }

            Message userMsg = new Message();
            userMsg.setSessionId(id);
            userMsg.setSender("user");
            userMsg.setContent(userText);
            userMsg.setAudioUrl(userAudioUrl);
            userMsg.setCreateTime(LocalDateTime.now());
            messageMapper.insert(userMsg);

            // 5. 保存 AI 回复的消息
            String aiAudioUrl = null;
            String aiAudioBase64 = null;
            if (agentResult.getAudioBytes() != null && agentResult.getAudioBytes().length > 0) {
                byte[] aiAudio = agentResult.getAudioBytes();
                aiAudioBase64 = Base64.getEncoder().encodeToString(aiAudio);
                String objectName = "chat/ai_" + UUID.randomUUID() + ".wav";
                try (ByteArrayInputStream bais = new ByteArrayInputStream(aiAudio)) {
                    minioClient.putObject(PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .stream(bais, aiAudio.length, -1)
                            .contentType("audio/wav")
                            .build());
                    aiAudioUrl = "/" + bucketName + "/" + objectName;
                }
            }

            Message aiMsg = new Message();
            aiMsg.setSessionId(id);
            aiMsg.setSender("ai");
            aiMsg.setContent(agentResult.getReplyText());
            aiMsg.setAudioUrl(aiAudioUrl);
            aiMsg.setRefinedContent(agentResult.getRefinedText());
            aiMsg.setChinglishFeedback(agentResult.getChinglishFeedback());
            aiMsg.setCreateTime(LocalDateTime.now());
            messageMapper.insert(aiMsg);

            // 6. 返回结果（返回 AI 消息，并携带刚才转写出的 userText，以便前端局部更新历史）
            MessageVO responseVO = MessageVO.builder()
                    .id(aiMsg.getId())
                    .sessionId(id)
                    .sender("ai")
                    .content(aiMsg.getContent())
                    .audioUrl(aiAudioUrl)
                    .audioBase64(aiAudioBase64)
                    .refinedContent(aiMsg.getRefinedContent())
                    .chinglishFeedback(aiMsg.getChinglishFeedback())
                    .createTime(aiMsg.getCreateTime())
                    .userTranscript(userText)
                    .build();

            return ApiResult.success(responseVO);
        } catch (Exception e) {
            log.error("Chat failure on session={}", id, e);
            return ApiResult.error(500, "chat failed: " + e.getMessage());
        }
    }

    /**
     * 获取会话对话历史记录
     */
    @GetMapping("/{id}/messages")
    public ApiResult<List<MessageVO>> getMessages(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long id
    ) {
        try {
            LambdaQueryWrapper<Message> query = new LambdaQueryWrapper<>();
            query.eq(Message::getSessionId, id).orderByAsc(Message::getCreateTime);
            List<Message> list = messageMapper.selectList(query);
            List<MessageVO> volist = list.stream().map(m -> MessageVO.builder()
                    .id(m.getId())
                    .sessionId(m.getSessionId())
                    .sender(m.getSender())
                    .content(m.getContent())
                    .audioUrl(m.getAudioUrl())
                    .refinedContent(m.getRefinedContent())
                    .chinglishFeedback(m.getChinglishFeedback())
                    .createTime(m.getCreateTime())
                    .build()
            ).collect(Collectors.toList());
            return ApiResult.success(volist);
        } catch (Exception e) {
            log.error("Failed to load messages for session: {}", id, e);
            return ApiResult.success(Collections.emptyList());
        }
    }

    /**
     * 获取脚手架句式提示
     */
    @GetMapping("/{id}/scaffolding")
    public ApiResult<List<String>> getScaffolding(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long id,
            @RequestParam("q") String query
    ) {
        try {
            List<String> hints = agentGrpcClient.getScaffolding(String.valueOf(id), query);
            return ApiResult.success(hints);
        } catch (Exception e) {
            log.error("Failed to get scaffolding for session: {}", id, e);
            return ApiResult.success(Collections.emptyList());
        }
    }
}
