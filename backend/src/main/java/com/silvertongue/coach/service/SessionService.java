package com.silvertongue.coach.service;

import com.silvertongue.coach.dto.SessionCreateRequest;
import com.silvertongue.coach.dto.SessionVO;
import com.silvertongue.coach.entity.PracticeSession;
import com.silvertongue.coach.grpc.AgentGrpcClient;
import com.silvertongue.coach.mapper.PracticeSessionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class SessionService {

    private final PracticeSessionMapper sessionMapper;
    private final AgentGrpcClient agentGrpcClient;

    /**
     * 创建练习会话
     */
    @Transactional
    public SessionVO create(Long userId, SessionCreateRequest request) {
        LocalDateTime now = LocalDateTime.now();
        PracticeSession session = new PracticeSession();
        session.setUserId(userId);
        session.setType(request.getType());
        session.setMode(request.getMode());
        
        String topic = request.getTopic() != null && !request.getTopic().isBlank() ? request.getTopic() : "日常闲聊";
        session.setTopic(topic);
        
        session.setStatus(0); // 进行中
        session.setCreateTime(now);
        session.setUpdateTime(now);
        sessionMapper.insert(session);

        log.info("Session created: id={}, userId={}, type={}, mode={}, topic={}", 
                 session.getId(), userId, request.getType(), request.getMode(), topic);

        // 调用 Python Agent 开启会话 (初始化 Redis 中的角色/场景等上下文)
        if ("ai_chat".equals(request.getType())) {
            // 目前默认传入 B2 级别，可后续扩展用户级别字段
            agentGrpcClient.startSession(userId.toString(), session.getId().toString(), request.getMode(), "B2", topic);
        }

        return toVO(session);
    }

    /**
     * 结束会话
     */
    @Transactional
    public SessionVO complete(Long sessionId, Long userId) {
        PracticeSession session = sessionMapper.selectById(sessionId);
        if (session == null) {
            throw new IllegalArgumentException("session not found");
        }
        if (!session.getUserId().equals(userId)) {
            throw new IllegalArgumentException("not your session");
        }
        if (session.getStatus() == 1) {
            throw new IllegalArgumentException("session already completed");
        }

        session.setStatus(1);
        session.setUpdateTime(LocalDateTime.now());
        sessionMapper.updateById(session);

        log.info("Session completed: id={}", sessionId);

        return toVO(session);
    }

    private SessionVO toVO(PracticeSession s) {
        return SessionVO.builder()
                .id(s.getId())
                .userId(s.getUserId())
                .type(s.getType())
                .mode(s.getMode())
                .topic(s.getTopic())
                .status(s.getStatus())
                .createTime(s.getCreateTime())
                .updateTime(s.getUpdateTime())
                .build();
    }
}
