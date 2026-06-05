package com.silvertongue.user.service;

import com.silvertongue.user.dto.PointsRankItem;
import com.silvertongue.user.entity.User;
import com.silvertongue.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RankService {

    private static final String RANK_POINTS_KEY = "rank:points";

    private final RedisTemplate<String, String> redisTemplate;
    private final UserMapper userMapper;

    /**
     * 积分排行榜 Top N
     */
    public List<PointsRankItem> getPointsRank(int topN) {
        Set<ZSetOperations.TypedTuple<String>> topSet = redisTemplate.opsForZSet()
                .reverseRangeWithScores(RANK_POINTS_KEY, 0, topN - 1);

        if (topSet == null || topSet.isEmpty()) {
            return List.of();
        }

        // 批量查用户信息
        List<Long> userIds = topSet.stream()
                .map(t -> Long.valueOf(t.getValue()))
                .collect(Collectors.toList());
        List<User> users = userMapper.selectBatchIds(userIds);
        Map<Long, User> userMap = users.stream()
                .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));

        List<PointsRankItem> result = new ArrayList<>();
        int rank = 1;
        for (ZSetOperations.TypedTuple<String> tuple : topSet) {
            Long uid = Long.valueOf(tuple.getValue());
            User user = userMap.get(uid);
            result.add(PointsRankItem.builder()
                    .rank(rank++)
                    .userId(uid)
                    .nickname(user != null ? user.getNickname() : "unknown")
                    .avatarUrl(user != null ? user.getAvatarUrl() : null)
                    .points(tuple.getScore() != null ? tuple.getScore().longValue() : 0L)
                    .build());
        }
        return result;
    }
}
