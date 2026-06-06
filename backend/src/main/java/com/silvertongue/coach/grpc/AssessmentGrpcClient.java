package com.silvertongue.coach.grpc;

import com.google.protobuf.ByteString;
import com.silvertongue.grpc.assessment.AssessRequest;
import com.silvertongue.grpc.assessment.AssessResponse;
import com.silvertongue.grpc.assessment.AssessmentServiceGrpc;
import io.grpc.ManagedChannel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * gRPC 客户端 — 调用 Python Agent 的发音评估服务
 *
 * ⚠️ 依赖 proto 编译生成的桩代码：
 *   mvn compile  # protobuf-maven-plugin 自动生成 AssessmentServiceGrpc / AssessRequest / AssessResponse
 *   python -m grpc_tools.protoc  # 生成 Python 端桩代码
 */
@Slf4j
@Service
public class AssessmentGrpcClient {

    private final AssessmentServiceGrpc.AssessmentServiceBlockingStub stub;

    public AssessmentGrpcClient(ManagedChannel channel) {
        this.stub = AssessmentServiceGrpc.newBlockingStub(channel);
    }

    /**
     * 调用 Python Agent 进行发音评估
     *
     * @param userId    用户 ID
     * @param audioData 录音音频字节
     * @param targetText 目标文本
     * @return gRPC 原始评估响应
     */
    public AssessResponse assessPronunciation(String userId, byte[] audioData, String targetText) {
        AssessRequest request = AssessRequest.newBuilder()
                .setUserId(userId)
                .setAudioData(ByteString.copyFrom(audioData))
                .setTargetText(targetText)
                .build();

        log.info("Calling gRPC AssessPronunciation: userId={}, audioSize={} bytes, text='{}'",
                userId, audioData.length, targetText.length() > 30 ? targetText.substring(0, 30) + "..." : targetText);

        AssessResponse response = stub.assessPronunciation(request);

        log.info("gRPC AssessPronunciation result: score={}, accuracy={}, fluency={}, completeness={}",
                response.getFinalScore(), response.getAccuracy(), response.getFluency(), response.getCompleteness());

        return response;
    }
}
