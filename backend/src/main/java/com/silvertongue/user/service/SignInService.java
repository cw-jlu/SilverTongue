package com.silvertongue.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.silvertongue.user.dto.CalendarDay;
import com.silvertongue.user.dto.SignInResponse;
import com.silvertongue.user.entity.PointsLog;
import com.silvertongue.user.entity.User;
import com.silvertongue.user.entity.UserSignIn;
import com.silvertongue.user.mapper.PointsLogMapper;
import com.silvertongue.user.mapper.UserMapper;
import com.silvertongue.user.mapper.UserSignInMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
@RequiredArgsConstructor
public class SignInService {

    private static final String SIGNIN_BITMAP_PREFIX = "user:signin:";
    private static final String RANK_POINTS_KEY = "rank:points";

    private final RedisTemplate<String, String> redisTemplate;
    private final UserMapper userMapper;
    private final UserSignInMapper signInMapper;
    private final PointsLogMapper pointsLogMapper;

    /**
     * 每日签到 — Redis Bitmap + MySQL 双写
     */
    @Transactional
    public SignInResponse signIn(Long userId) {
        LocalDate today = LocalDate.now();
        String bitmapKey = buildBitmapKey(userId, today);

        // 1. 检查是否已签到
        Boolean hasSigned = redisTemplate.opsForValue().getBit(bitmapKey, today.getDayOfMonth() - 1);
        if (Boolean.TRUE.equals(hasSigned)) {
            throw new IllegalArgumentException("今日已签到");
        }

        // 2. 随机奖励积分 (5-15)
        int pointsRewarded = ThreadLocalRandom.current().nextInt(5, 16);

        LocalDateTime now = LocalDateTime.now();

        // 3. 更新 Redis Bitmap
        redisTemplate.opsForValue().setBit(bitmapKey, today.getDayOfMonth() - 1, true);

        // 4. 更新用户积分与签到次数
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new IllegalArgumentException("user not found");
        }
        user.setPoints(user.getPoints() + pointsRewarded);
        user.setSignInCount(user.getSignInCount() + 1);
        user.setUpdateTime(now);
        userMapper.updateById(user);

        // 5. MySQL 签到记录备份
        UserSignIn signIn = new UserSignIn();
        signIn.setUserId(userId);
        signIn.setSignInDate(today);
        signIn.setPointsRewarded(pointsRewarded);
        signIn.setCreateTime(now);
        signInMapper.insert(signIn);

        // 6. 积分变动流水
        PointsLog pointsLog = new PointsLog();
        pointsLog.setUserId(userId);
        pointsLog.setChangeAmount(pointsRewarded);
        pointsLog.setReason("每日签到奖励");
        pointsLog.setCreateTime(now);
        pointsLogMapper.insert(pointsLog);

        // 7. 更新排行榜
        redisTemplate.opsForZSet().incrementScore(RANK_POINTS_KEY, userId.toString(), pointsRewarded);

        log.info("User {} signed in, rewarded {} points, total sign-ins: {}", userId, pointsRewarded, user.getSignInCount());

        return SignInResponse.builder()
                .pointsRewarded(pointsRewarded)
                .totalSignInCount(user.getSignInCount())
                .totalPoints(user.getPoints())
                .build();
    }

    /**
     * 月度签到日历 — 用 Redis Bitmap BITFIELD 批量获取
     */
    public List<CalendarDay> calendar(Long userId, int year, int month) {
        YearMonth ym = YearMonth.of(year, month);
        int daysInMonth = ym.lengthOfMonth();
        String bitmapKey = buildBitmapKey(userId, year, month);

        // BITFIELD GET u31 一次取 31 位
        List<Long> bitfields = redisTemplate.opsForValue()
                .bitField(bitmapKey, BitFieldSubCommands.create()
                        .get(BitFieldSubCommands.BitFieldType.unsigned(daysInMonth))
                        .valueAt(0));

        long bits = (bitfields != null && !bitfields.isEmpty() && bitfields.get(0) != null) ? bitfields.get(0) : 0L;

        List<CalendarDay> calendar = new ArrayList<>();
        for (int day = 1; day <= daysInMonth; day++) {
            boolean signed = ((bits >> (daysInMonth - day)) & 1L) == 1L;
            calendar.add(new CalendarDay(day, signed));
        }
        return calendar;
    }

    private String buildBitmapKey(Long userId, LocalDate date) {
        return buildBitmapKey(userId, date.getYear(), date.getMonthValue());
    }

    private String buildBitmapKey(Long userId, int year, int month) {
        return String.format("%s%d:%04d%02d", SIGNIN_BITMAP_PREFIX, userId, year, month);
    }
}
