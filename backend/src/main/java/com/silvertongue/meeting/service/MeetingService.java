package com.silvertongue.meeting.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.silvertongue.meeting.dto.RoomCreateRequest;
import com.silvertongue.meeting.dto.RoomVO;
import com.silvertongue.meeting.entity.Room;
import com.silvertongue.meeting.entity.RoomParticipant;
import com.silvertongue.meeting.mapper.RoomMapper;
import com.silvertongue.meeting.mapper.RoomParticipantMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MeetingService {

    private static final String ROOM_PREFIX = "meeting:room:";

    private final RoomMapper roomMapper;
    private final RoomParticipantMapper participantMapper;
    private final RedisTemplate<String, String> redisTemplate;

    @Transactional
    public RoomVO createRoom(Long creatorId, RoomCreateRequest request) {
        Room room = new Room();
        room.setCreatorId(creatorId);
        room.setRoomName(request.getRoomName());
        room.setMaxUsers(request.getMaxUsers());
        room.setStatus(0);
        room.setCreateTime(LocalDateTime.now());
        roomMapper.insert(room);

        return toVO(room);
    }

    public List<RoomVO> listActiveRooms() {
        List<Room> rooms = roomMapper.selectList(new LambdaQueryWrapper<Room>()
                .eq(Room::getStatus, 0)
                .orderByDesc(Room::getCreateTime));
        return rooms.stream().map(this::toVO).collect(Collectors.toList());
    }

    @Transactional
    public void joinRoom(Long roomId, Long userId) {
        Room room = roomMapper.selectById(roomId);
        if (room == null || room.getStatus() == 1) {
            throw new IllegalArgumentException("room not found or closed");
        }

        // 检查在线人数
        Set<String> online = redisTemplate.opsForSet().members(ROOM_PREFIX + roomId);
        if (online != null && online.size() >= room.getMaxUsers()) {
            throw new IllegalArgumentException("room is full");
        }

        // 写入 MySQL 参与记录
        RoomParticipant participant = new RoomParticipant();
        participant.setRoomId(roomId);
        participant.setUserId(userId);
        participant.setJoinTime(LocalDateTime.now());
        participantMapper.insert(participant);

        // 同步加入 Redis 在线集合
        redisTemplate.opsForSet().add(ROOM_PREFIX + roomId, userId.toString());
    }

    @Transactional
    public void leaveRoom(Long roomId, Long userId) {
        redisTemplate.opsForSet().remove(ROOM_PREFIX + roomId, userId.toString());
        // 更新 leave_time
        RoomParticipant participant = participantMapper.selectOne(new LambdaQueryWrapper<RoomParticipant>()
                .eq(RoomParticipant::getRoomId, roomId)
                .eq(RoomParticipant::getUserId, userId)
                .isNull(RoomParticipant::getLeaveTime)
                .last("LIMIT 1"));
        if (participant != null) {
            participant.setLeaveTime(LocalDateTime.now());
            participantMapper.updateById(participant);
        }
    }

    private RoomVO toVO(Room room) {
        Set<String> online = redisTemplate.opsForSet().members(ROOM_PREFIX + room.getId());
        return RoomVO.builder()
                .id(room.getId())
                .roomName(room.getRoomName())
                .creatorId(room.getCreatorId())
                .maxUsers(room.getMaxUsers())
                .onlineCount(online != null ? online.size() : 0)
                .status(room.getStatus())
                .createTime(room.getCreateTime())
                .build();
    }
}
