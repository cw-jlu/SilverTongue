package com.silvertongue.user.service;

import com.silvertongue.user.dto.CalendarDay;
import com.silvertongue.user.dto.SignInResponse;
import com.silvertongue.user.entity.PointsLog;
import com.silvertongue.user.entity.User;
import com.silvertongue.user.entity.UserSignIn;
import com.silvertongue.user.mapper.PointsLogMapper;
import com.silvertongue.user.mapper.UserMapper;
import com.silvertongue.user.mapper.UserSignInMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;

import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SignInServiceTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private UserMapper userMapper;

    @Mock
    private UserSignInMapper signInMapper;

    @Mock
    private PointsLogMapper pointsLogMapper;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private ZSetOperations<String, String> zSetOperations;

    @InjectMocks
    private SignInService signInService;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
    }

    @Test
    void signInShouldRejectDuplicateDailySignIn() {
        when(valueOperations.getBit(startsWith("user:signin:8:"), anyLong())).thenReturn(true);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> signInService.signIn(8L));

        assertNotNull(exception.getMessage());
        verify(userMapper, never()).selectById(anyLong());
    }

    @Test
    void signInShouldUpdateUserAndPersistBackups() {
        User user = new User();
        user.setId(9L);
        user.setPoints(100L);
        user.setSignInCount(3);

        when(valueOperations.getBit(startsWith("user:signin:9:"), anyLong())).thenReturn(false);
        when(userMapper.selectById(9L)).thenReturn(user);

        SignInResponse response = signInService.signIn(9L);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        ArgumentCaptor<UserSignIn> signInCaptor = ArgumentCaptor.forClass(UserSignIn.class);
        ArgumentCaptor<PointsLog> pointsLogCaptor = ArgumentCaptor.forClass(PointsLog.class);

        verify(valueOperations).setBit(startsWith("user:signin:9:"), anyLong(), eq(true));
        verify(userMapper).updateById(userCaptor.capture());
        verify(signInMapper).insert(signInCaptor.capture());
        verify(pointsLogMapper).insert(pointsLogCaptor.capture());
        verify(zSetOperations).incrementScore("rank:points", "9", response.getPointsRewarded());

        User updatedUser = userCaptor.getValue();
        assertTrue(response.getPointsRewarded() >= 5 && response.getPointsRewarded() <= 15);
        assertEquals(4, updatedUser.getSignInCount());
        assertEquals(100L + response.getPointsRewarded(), updatedUser.getPoints());
        assertEquals(9L, signInCaptor.getValue().getUserId());
        assertEquals(response.getPointsRewarded(), signInCaptor.getValue().getPointsRewarded());
        assertEquals(9L, pointsLogCaptor.getValue().getUserId());
        assertEquals(response.getPointsRewarded(), pointsLogCaptor.getValue().getChangeAmount());
    }

    @Test
    void calendarShouldDecodeBitmapInMonthOrder() {
        int year = 2025;
        int month = 2;
        int daysInMonth = YearMonth.of(year, month).lengthOfMonth();
        long bits = (1L << (daysInMonth - 1)) | 1L;

        when(valueOperations.bitField(eq("user:signin:7:202502"), any(BitFieldSubCommands.class)))
                .thenReturn(List.of(bits));

        List<CalendarDay> calendar = signInService.calendar(7L, year, month);

        assertEquals(daysInMonth, calendar.size());
        assertTrue(calendar.get(0).isSigned());
        assertFalse(calendar.get(1).isSigned());
        assertTrue(calendar.get(calendar.size() - 1).isSigned());
    }
}
