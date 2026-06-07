package com.silvertongue.meeting.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.silvertongue.meeting.dto.RoomCreateRequest;
import com.silvertongue.meeting.dto.RoomVO;
import com.silvertongue.meeting.entity.Room;
import com.silvertongue.meeting.entity.RoomParticipant;
import com.silvertongue.meeting.mapper.RoomMapper;
import com.silvertongue.meeting.mapper.RoomParticipantMapper;
import com.silvertongue.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MeetingService {

    private static final String ROOM_PREFIX = "meeting:room:";

    private final RoomMapper roomMapper;
    private final RoomParticipantMapper participantMapper;
    private final UserMapper userMapper;
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

        // Creator automatically joins the room as host
        RoomParticipant participant = new RoomParticipant();
        participant.setRoomId(room.getId());
        participant.setUserId(creatorId);
        participant.setJoinTime(LocalDateTime.now());
        participant.setRole(1); // Host
        participantMapper.insert(participant);

        redisTemplate.opsForSet().add(ROOM_PREFIX + room.getId(), creatorId.toString());

        return toVO(room);
    }

    public List<RoomVO> listActiveRooms() {
        List<Room> rooms = roomMapper.selectList(new LambdaQueryWrapper<Room>()
                .eq(Room::getStatus, 0)
                .orderByDesc(Room::getCreateTime));
        return rooms.stream().map(this::toVO).collect(Collectors.toList());
    }

    public RoomVO getRoomDetail(Long roomId) {
        Room room = roomMapper.selectById(roomId);
        if (room == null) {
            throw new IllegalArgumentException("room not found");
        }
        RoomVO vo = toVO(room);

        // Fetch participants (including AI and real users)
        List<RoomParticipant> participants = participantMapper.selectList(new LambdaQueryWrapper<RoomParticipant>()
                .eq(RoomParticipant::getRoomId, roomId)
                .isNull(RoomParticipant::getLeaveTime));
        
        List<RoomVO.ParticipantVO> pVOs = participants.stream().map(p -> {
            RoomVO.ParticipantVO pVO = new RoomVO.ParticipantVO();
            pVO.setId(p.getId());
            pVO.setUserId(p.getUserId());
            pVO.setRole(p.getRole() != null ? p.getRole() : 0);
            pVO.setAiRoleName(p.getAiRoleName());
            pVO.setAiRoleSetting(p.getAiRoleSetting());
            if (p.getUserId() < 0) {
                pVO.setNickname(p.getAiRoleName() != null ? p.getAiRoleName() : "AI Assistant");
                pVO.setAvatarUrl("");
            } else {
                com.silvertongue.user.entity.User user = userMapper.selectById(p.getUserId());
                if (user != null) {
                    pVO.setNickname(user.getNickname());
                    pVO.setAvatarUrl(user.getAvatarUrl());
                } else {
                    pVO.setNickname("User " + p.getUserId());
                }
            }
            return pVO;
        }).collect(Collectors.toList());

        vo.setParticipants(pVOs);
        return vo;
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

        // Check if already in the room
        RoomParticipant existing = participantMapper.selectOne(new LambdaQueryWrapper<RoomParticipant>()
                .eq(RoomParticipant::getRoomId, roomId)
                .eq(RoomParticipant::getUserId, userId)
                .isNull(RoomParticipant::getLeaveTime)
                .last("LIMIT 1"));
        if (existing == null) {
            // 写入 MySQL 参与记录
            RoomParticipant participant = new RoomParticipant();
            participant.setRoomId(roomId);
            participant.setUserId(userId);
            participant.setJoinTime(LocalDateTime.now());
            participant.setRole(Objects.equals(room.getCreatorId(), userId) ? 1 : 0);
            participantMapper.insert(participant);
        }

        // 同步加入 Redis 在线集合
        redisTemplate.opsForSet().add(ROOM_PREFIX + roomId, userId.toString());
    }

    @Transactional
    public void inviteFriend(Long roomId, Long friendId, Long userId) {
        Room room = roomMapper.selectById(roomId);
        if (room == null || room.getStatus() == 1) {
            throw new IllegalArgumentException("room not found or closed");
        }
        RoomParticipant existing = participantMapper.selectOne(new LambdaQueryWrapper<RoomParticipant>()
                .eq(RoomParticipant::getRoomId, roomId)
                .eq(RoomParticipant::getUserId, friendId)
                .isNull(RoomParticipant::getLeaveTime)
                .last("LIMIT 1"));
        if (existing == null) {
            RoomParticipant participant = new RoomParticipant();
            participant.setRoomId(roomId);
            participant.setUserId(friendId);
            participant.setJoinTime(LocalDateTime.now());
            participant.setRole(0);
            participantMapper.insert(participant);
        }
        redisTemplate.opsForSet().add(ROOM_PREFIX + roomId, friendId.toString());
    }

    @Transactional
    public void addAiParticipant(Long roomId, String aiName, String aiSetting, Long userId) {
        Room room = roomMapper.selectById(roomId);
        if (room == null || room.getStatus() == 1) {
            throw new IllegalArgumentException("room not found or closed");
        }
        
        List<RoomParticipant> aiParticipants = participantMapper.selectList(new LambdaQueryWrapper<RoomParticipant>()
                .eq(RoomParticipant::getRoomId, roomId)
                .lt(RoomParticipant::getUserId, 0)
                .isNull(RoomParticipant::getLeaveTime));
        
        long aiUserId = -1L;
        for (RoomParticipant ap : aiParticipants) {
            if (ap.getUserId() <= aiUserId) {
                aiUserId = ap.getUserId() - 1;
            }
        }

        RoomParticipant participant = new RoomParticipant();
        participant.setRoomId(roomId);
        participant.setUserId(aiUserId);
        participant.setJoinTime(LocalDateTime.now());
        participant.setRole(0);
        participant.setAiRoleName(aiName);
        participant.setAiRoleSetting(aiSetting);
        participantMapper.insert(participant);

        redisTemplate.opsForSet().add(ROOM_PREFIX + roomId, String.valueOf(aiUserId));
    }

    @Transactional
    public void removeParticipant(Long roomId, Long participantId, Long userId) {
        RoomParticipant participant = participantMapper.selectById(participantId);
        if (participant != null) {
            participant.setLeaveTime(LocalDateTime.now());
            participantMapper.updateById(participant);
            redisTemplate.opsForSet().remove(ROOM_PREFIX + roomId, participant.getUserId().toString());
        }
    }

    @Transactional
    public void updateAiParticipant(Long roomId, Long participantId, String aiName, String aiSetting, Long userId) {
        RoomParticipant participant = participantMapper.selectById(participantId);
        if (participant != null && participant.getUserId() < 0) {
            participant.setAiRoleName(aiName);
            participant.setAiRoleSetting(aiSetting);
            participantMapper.updateById(participant);
        }
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

