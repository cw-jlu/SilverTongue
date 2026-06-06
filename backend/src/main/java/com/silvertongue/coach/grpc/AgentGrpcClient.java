package com.silvertongue.coach.grpc;

import com.silvertongue.grpc.agent.AgentServiceGrpc;
import com.silvertongue.grpc.agent.StartSessionRequest;
import com.silvertongue.grpc.agent.StartSessionResponse;
import io.grpc.ManagedChannel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * gRPC 客户端 — 调用 Python Agent 的 AgentService (会话控制与交互)
 */
@Slf4j
@Service
public class AgentGrpcClient {

    private final AgentServiceGrpc.AgentServiceBlockingStub stub;

    public AgentGrpcClient(ManagedChannel channel) {
        this.stub = AgentServiceGrpc.newBlockingStub(channel);
    }

    /**
     * 调用 Python Agent 开启对练会话，在 Redis 中初始化会话状态和角色设定
     *
     * @param userId    用户 ID
     * @param sessionId 会话 ID
     * @param mode      交互模式 (full_duplex, half_duplex, guided, free_talk)
     * @param userLevel CEFR 等级 (如 B2)
     * @param topic     角色/场景 (如 "雅思考官", "日常闲聊")
     * @return 是否开启成功
     */
    public boolean startSession(String userId, String sessionId, String mode, String userLevel, String topic) {
        StartSessionRequest request = StartSessionRequest.newBuilder()
                .setUserId(userId)
                .setSessionId(sessionId)
                .setMode(mode)
                .setUserLevel(userLevel)
                .setTopic(topic)
                .build();

        log.info("Calling gRPC StartSession: userId={}, sessionId={}, mode={}, level={}, topic={}",
                userId, sessionId, mode, userLevel, topic);

        try {
            StartSessionResponse response = stub.startSession(request);
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
}
