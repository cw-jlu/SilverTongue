package com.silvertongue.coach.grpc;

import com.google.protobuf.ByteString;
import com.silvertongue.grpc.agent.*;
import io.grpc.ManagedChannel;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * gRPC 客户端 - 调用 Python Agent 的 AgentService（会话控制与交互）
 */
@Slf4j
@Service
public class AgentGrpcClient {

    private final AgentServiceGrpc.AgentServiceBlockingStub blockingStub;
    private final AgentServiceGrpc.AgentServiceStub asyncStub;

    public AgentGrpcClient(ManagedChannel channel) {
        this.blockingStub = AgentServiceGrpc.newBlockingStub(channel);
        this.asyncStub = AgentServiceGrpc.newStub(channel);
    }

    /**
     * 调用 Python Agent 开启对练会话，在 Redis 中初始化会话状态和角色设定
     *
     * @param userId 用户 ID
     * @param sessionId 会话 ID
     * @param mode 交互模式 (full_duplex, half_duplex, guided, free_talk)
     * @param userLevel CEFR 等级 (如 B2)
     * @param topic 角色/场景 (如 "雅思考官", "日常闲聊")
     * @param contextFileUrl 场景辅助材料 URL
     * @return 是否开启成功
     */
    public boolean startSession(String userId, String sessionId, String mode, String userLevel, String topic, String contextFileUrl) {
        StartSessionRequest.Builder requestBuilder = StartSessionRequest.newBuilder()
                .setUserId(userId)
                .setSessionId(sessionId)
                .setMode(mode)
                .setUserLevel(userLevel)
                .setTopic(topic);

        if (contextFileUrl != null && !contextFileUrl.isBlank()) {
            requestBuilder.setContextFileUrl(contextFileUrl);
        }

        StartSessionRequest request = requestBuilder.build();

        log.info("Calling gRPC StartSession: userId={}, sessionId={}, mode={}, level={}, topic={}, contextFileUrl={}",
                userId, sessionId, mode, userLevel, topic, contextFileUrl);

        try {
            StartSessionResponse response = blockingStub.startSession(request);
            if (response.getSuccess()) {
                log.info("gRPC StartSession successful for session: {}", sessionId);
                return true;
            } else {
                log.error("gRPC StartSession failed for session: {}, error: {}", sessionId, response.getErrorMessage());
                return false;
            }
        } catch (Exception e) {
            log.error("gRPC StartSession call threw exception for session: {}", sessionId, e);
            return false;
        }
    }

    /**
     * 语音/文本对话流桥接，利用 StreamObserver 和 CountDownLatch 实现同步等待流结束
     *
     * @param sessionId 会话 ID
     * @param data 音频 PCM 或文本 UTF-8 字节
     * @return AI 响应数据汇总
     */
    public ChatStreamResult chatStream(String sessionId, byte[] data) {
        StringBuilder textDeltaAccumulator = new StringBuilder();
        ByteArrayOutputStream audioBytesAccumulator = new ByteArrayOutputStream();
        StringBuilder refinedAccumulator = new StringBuilder();
        StringBuilder userTranscriptAccumulator = new StringBuilder();
        final ChinglishAnalysis[] chinglishContainer = new ChinglishAnalysis[1];

        CountDownLatch latch = new CountDownLatch(1);

        StreamObserver<ChatStreamResponse> responseObserver = new StreamObserver<ChatStreamResponse>() {
            @Override
            public void onNext(ChatStreamResponse response) {
                if (response.getTextDelta() != null && !response.getTextDelta().isEmpty()) {
                    textDeltaAccumulator.append(response.getTextDelta());
                }
                if (response.getAudioChunk() != null && !response.getAudioChunk().isEmpty()) {
                    try {
                        audioBytesAccumulator.write(response.getAudioChunk().toByteArray());
                    } catch (Exception e) {
                        log.error("Failed to write response audio chunk", e);
                    }
                }
                if (response.getRefinedText() != null && !response.getRefinedText().isEmpty()) {
                    refinedAccumulator.append(response.getRefinedText());
                }
                if (response.hasChinglish() && response.getChinglish().getHasChinglish()) {
                    chinglishContainer[0] = response.getChinglish();
                }
                if (response.getUserTranscript() != null && !response.getUserTranscript().isEmpty()) {
                    userTranscriptAccumulator.append(response.getUserTranscript());
                }
            }

            @Override
            public void onError(Throwable t) {
                log.error("gRPC chatStream failed for session: {}", sessionId, t);
                latch.countDown();
            }

            @Override
            public void onCompleted() {
                log.info("gRPC chatStream completed for session: {}", sessionId);
                latch.countDown();
            }
        };

        StreamObserver<ChatStreamRequest> requestObserver = asyncStub.chatStream(responseObserver);

        try {
            requestObserver.onNext(ChatStreamRequest.newBuilder()
                    .setSessionId(sessionId)
                    .setAudioChunk(ByteString.copyFrom(data))
                    .setIsFinalChunk(true)
                    .build());
            requestObserver.onCompleted();
        } catch (Exception e) {
            log.error("Failed to send chat stream request for session: {}", sessionId, e);
            requestObserver.onError(e);
            return new ChatStreamResult("", new byte[0], "", "", "");
        }

        try {
            if (!latch.await(30, TimeUnit.SECONDS)) {
                log.warn("gRPC chatStream timeout for session: {}", sessionId);
            }
        } catch (InterruptedException e) {
            log.error("gRPC chatStream thread interrupted for session: {}", sessionId, e);
            Thread.currentThread().interrupt();
        }

        String chinglishFeedback = "";
        if (chinglishContainer[0] != null) {
            ChinglishAnalysis ca = chinglishContainer[0];
            chinglishFeedback = "检测到中式英语: " + ca.getOriginalPattern()
                    + "\n建议: " + ca.getSuggestion()
                    + "\n严重程度: " + ca.getSeverity();
        }

        return new ChatStreamResult(
                textDeltaAccumulator.toString(),
                audioBytesAccumulator.toByteArray(),
                refinedAccumulator.toString(),
                chinglishFeedback,
                userTranscriptAccumulator.toString()
        );
    }

    /**
     * 获取句式引导表达（脚手架）
     *
     * @param sessionId 会话 ID
     * @param incompleteText 输入的一半句子
     * @return 引导提示列表
     */
    public List<String> getScaffolding(String sessionId, String incompleteText) {
        ScaffoldingRequest request = ScaffoldingRequest.newBuilder()
                .setSessionId(sessionId)
                .setIncompleteText(incompleteText)
                .build();
        try {
            ScaffoldingResponse response = blockingStub.getScaffolding(request);
            return response.getCompletionHintsList();
        } catch (Exception e) {
            log.error("gRPC getScaffolding failed for session: {}", sessionId, e);
            return Collections.emptyList();
        }
    }

    /**
     * 辅助类：接收 gRPC 流返回的汇总数据
     */
    public static class ChatStreamResult {
        private final String replyText;
        private final byte[] audioBytes;
        private final String refinedText;
        private final String chinglishFeedback;
        private final String userTranscript;

        public ChatStreamResult(String replyText, byte[] audioBytes, String refinedText, String chinglishFeedback, String userTranscript) {
            this.replyText = replyText;
            this.audioBytes = audioBytes;
            this.refinedText = refinedText;
            this.chinglishFeedback = chinglishFeedback;
            this.userTranscript = userTranscript;
        }

        public String getReplyText() {
            return replyText;
        }

        public byte[] getAudioBytes() {
            return audioBytes;
        }

        public String getRefinedText() {
            return refinedText;
        }

        public String getChinglishFeedback() {
            return chinglishFeedback;
        }

        public String getUserTranscript() {
            return userTranscript;
        }
    }
}