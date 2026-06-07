package com.silvertongue.meeting.service;

import com.silvertongue.meeting.dto.RoomVO;
import com.silvertongue.meeting.entity.Room;
import com.silvertongue.meeting.entity.RoomParticipant;
import com.silvertongue.meeting.mapper.RoomMapper;
import com.silvertongue.meeting.mapper.RoomParticipantMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MeetingServiceTest {

    @Mock
    private RoomMapper roomMapper;

    @Mock
    private RoomParticipantMapper participantMapper;

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private SetOperations<String, String> setOperations;

    @InjectMocks
    private MeetingService meetingService;

    @Test
    void joinRoomShouldRejectFullRoom() {
        Room room = new Room();
        room.setId(4L);
        room.setStatus(0);
        room.setMaxUsers(2);

        when(roomMapper.selectById(4L)).thenReturn(room);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.members("meeting:room:4")).thenReturn(Set.of("1", "2"));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> meetingService.joinRoom(4L, 9L));

        assertEquals("room is full", exception.getMessage());
        verify(participantMapper, never()).insert(any(RoomParticipant.class));
    }

    @Test
    void joinRoomShouldPersistParticipantAndMarkUserOnline() {
        Room room = new Room();
        room.setId(5L);
        room.setStatus(0);
        room.setMaxUsers(3);

        when(roomMapper.selectById(5L)).thenReturn(room);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.members("meeting:room:5")).thenReturn(Set.of("1"));

        meetingService.joinRoom(5L, 9L);

        ArgumentCaptor<RoomParticipant> captor = ArgumentCaptor.forClass(RoomParticipant.class);
        verify(participantMapper).insert(captor.capture());
        verify(setOperations).add("meeting:room:5", "9");
        assertEquals(5L, captor.getValue().getRoomId());
        assertEquals(9L, captor.getValue().getUserId());
        assertNotNull(captor.getValue().getJoinTime());
    }

    @Test
    void leaveRoomShouldUpdateActiveParticipantRecord() {
        RoomParticipant participant = new RoomParticipant();
        participant.setId(11L);
        participant.setRoomId(5L);
        participant.setUserId(9L);
        participant.setJoinTime(LocalDateTime.now().minusMinutes(10));

        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(participantMapper.selectOne(any())).thenReturn(participant);

        meetingService.leaveRoom(5L, 9L);

        verify(setOperations).remove("meeting:room:5", "9");
        ArgumentCaptor<RoomParticipant> captor = ArgumentCaptor.forClass(RoomParticipant.class);
        verify(participantMapper).updateById(captor.capture());
        assertNotNull(captor.getValue().getLeaveTime());
    }

    @Test
    void listActiveRoomsShouldIncludeOnlineCountsFromRedis() {
        Room room = new Room();
        room.setId(6L);
        room.setRoomName("Speaking Club");
        room.setCreatorId(1L);
        room.setMaxUsers(6);
        room.setStatus(0);
        room.setCreateTime(LocalDateTime.now());

        when(roomMapper.selectList(any())).thenReturn(List.of(room));
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.members("meeting:room:6")).thenReturn(Set.of("1", "2", "3"));

        List<RoomVO> rooms = meetingService.listActiveRooms();

        assertEquals(1, rooms.size());
        assertEquals("Speaking Club", rooms.get(0).getRoomName());
        assertEquals(3, rooms.get(0).getOnlineCount());
    }
}
