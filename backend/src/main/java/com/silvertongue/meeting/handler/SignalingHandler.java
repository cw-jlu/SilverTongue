package com.silvertongue.meeting.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebRTC 信令转发 — WebSocket
 *
 * 消息格式: {"type":"offer|answer|candidate|join|leave", "roomId":1, "target":"userId", "data":{...}}
 */
@Slf4j
@Component
public class SignalingHandler extends TextWebSocketHandler {

    private static final String ROOM_PREFIX = "meeting:room:";
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, Long> userRooms = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RedisTemplate<String, String> redisTemplate;

    public SignalingHandler(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String userId = getUserId(session);
        sessions.put(userId, session);
        log.info("WebSocket connected: userId={}", userId);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        JsonNode msg = objectMapper.readTree(message.getPayload());
        String type = msg.get("type").asText();
        long roomId = msg.get("roomId").asLong();
        String fromUserId = getUserId(session);

        switch (type) {
            case "join" -> {
                userRooms.put(fromUserId, roomId);
                redisTemplate.opsForSet().add(ROOM_PREFIX + roomId, fromUserId);
                broadcastToRoom(roomId, buildMsg("user-joined", roomId, fromUserId));
                log.info("User {} joined room {}", fromUserId, roomId);
            }
            case "leave" -> {
                userRooms.remove(fromUserId);
                redisTemplate.opsForSet().remove(ROOM_PREFIX + roomId, fromUserId);
                broadcastToRoom(roomId, buildMsg("user-left", roomId, fromUserId));
            }
            case "offer", "answer", "candidate" -> {
                String target = msg.has("target") ? msg.get("target").asText() : null;
                if (target != null) {
                    WebSocketSession targetSession = sessions.get(target);
                    if (targetSession != null && targetSession.isOpen()) {
                        String forwardMsg = objectMapper.createObjectNode()
                                .put("type", type)
                                .put("from", fromUserId)
                                .set("data", msg.get("data"))
                                .toString();
                        targetSession.sendMessage(new TextMessage(forwardMsg));
                    }
                }
            }
            default -> log.warn("Unknown signaling type: {}", type);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String userId = getUserId(session);
        sessions.remove(userId);
        log.info("WebSocket disconnected: userId={}", userId);

        Long roomId = userRooms.get(userId);
        if (roomId != null) {
            java.util.concurrent.CompletableFuture.runAsync(() -> {
                if (!sessions.containsKey(userId)) {
                    userRooms.remove(userId);
                    redisTemplate.opsForSet().remove(ROOM_PREFIX + roomId, userId);
                    broadcastToRoom(roomId, buildMsg("user-left", roomId, userId));
                    log.info("User {} disconnected for 3 seconds, automatically removed from room {}", userId, roomId);
                }
            }, java.util.concurrent.CompletableFuture.delayedExecutor(3, java.util.concurrent.TimeUnit.SECONDS));
        }
    }

    private void broadcastToRoom(long roomId, String message) {
        String key = ROOM_PREFIX + roomId;
        var members = redisTemplate.opsForSet().members(key);
        if (members != null) {
            for (String uid : members) {
                WebSocketSession s = sessions.get(uid);
                if (s != null && s.isOpen()) {
                    try {
                        s.sendMessage(new TextMessage(message));
                    } catch (Exception ignored) {}
                }
            }
        }
    }

    private String buildMsg(String type, long roomId, String fromUserId) {
        return objectMapper.createObjectNode()
                .put("type", type)
                .put("roomId", roomId)
                .put("from", fromUserId)
                .toString();
    }

    private String getUserId(WebSocketSession session) {
        // 从 URL query 中取 userId: /ws/signaling?userId=123
        String query = session.getUri() != null ? session.getUri().getQuery() : null;
        if (query != null) {
            for (String param : query.split("&")) {
                String[] kv = param.split("=");
                if (kv.length == 2 && "userId".equals(kv[0])) {
                    return kv[1];
                }
            }
        }
        return session.getId();
    }
}
