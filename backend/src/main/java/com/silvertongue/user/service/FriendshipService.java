package com.silvertongue.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.silvertongue.user.dto.FriendVO;
import com.silvertongue.user.entity.Friendship;
import com.silvertongue.user.entity.User;
import com.silvertongue.user.mapper.FriendshipMapper;
import com.silvertongue.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FriendshipService {

    private static final int STATUS_APPLYING = 0;
    private static final int STATUS_ACCEPTED = 1;
    private static final int STATUS_BLOCKED = 2;

    private final FriendshipMapper friendshipMapper;
    private final UserMapper userMapper;

    /**
     * 发起好友申请
     */
    @Transactional
    public void apply(Long userId, Long friendId) {
        if (userId.equals(friendId)) {
            throw new IllegalArgumentException("cannot add yourself as friend");
        }

        // 检查目标用户存在且正常
        User target = userMapper.selectById(friendId);
        if (target == null || target.getStatus() == null || target.getStatus() != 0) {
            throw new IllegalArgumentException("target user not found");
        }

        // 检查是否已有关系
        Friendship existing = friendshipMapper.selectOne(new LambdaQueryWrapper<Friendship>()
                .eq(Friendship::getUserId, userId)
                .eq(Friendship::getFriendId, friendId));
        if (existing != null) {
            if (existing.getStatus() == STATUS_APPLYING) {
                throw new IllegalArgumentException("already applied, waiting for response");
            }
            if (existing.getStatus() == STATUS_ACCEPTED) {
                throw new IllegalArgumentException("already friends");
            }
            // 屏蔽状态可以重新发起
        }

        LocalDateTime now = LocalDateTime.now();
        Friendship friendship = new Friendship();
        friendship.setUserId(userId);
        friendship.setFriendId(friendId);
        friendship.setStatus(STATUS_APPLYING);
        friendship.setCreateTime(now);
        friendship.setUpdateTime(now);
        friendshipMapper.insert(friendship);

        log.info("Friend request: {} -> {}", userId, friendId);
    }

    /**
     * 通过好友申请（写入双向记录）
     */
    @Transactional
    public void accept(Long currentUserId, Long applyId) {
        // applyId 对应的记录中 friend_id 必须是当前用户
        Friendship apply = friendshipMapper.selectById(applyId);
        if (apply == null) {
            throw new IllegalArgumentException("apply record not found");
        }
        if (!apply.getFriendId().equals(currentUserId)) {
            throw new IllegalArgumentException("not your pending request");
        }
        if (apply.getStatus() != STATUS_APPLYING) {
            throw new IllegalArgumentException("apply not in pending status");
        }

        LocalDateTime now = LocalDateTime.now();

        // 更新申请方的状态为已通过
        apply.setStatus(STATUS_ACCEPTED);
        apply.setUpdateTime(now);
        friendshipMapper.updateById(apply);

        // 写入当前用户的反向记录
        Friendship reverse = friendshipMapper.selectOne(new LambdaQueryWrapper<Friendship>()
                .eq(Friendship::getUserId, currentUserId)
                .eq(Friendship::getFriendId, apply.getUserId()));
        if (reverse == null) {
            reverse = new Friendship();
            reverse.setUserId(currentUserId);
            reverse.setFriendId(apply.getUserId());
            reverse.setStatus(STATUS_ACCEPTED);
            reverse.setCreateTime(now);
            reverse.setUpdateTime(now);
            friendshipMapper.insert(reverse);
        } else {
            reverse.setStatus(STATUS_ACCEPTED);
            reverse.setUpdateTime(now);
            friendshipMapper.updateById(reverse);
        }

        log.info("Friend accepted: {} <-> {}", apply.getUserId(), currentUserId);
    }

    /**
     * 好友列表（含备注名、头像、昵称等）
     */
    public List<FriendVO> listFriends(Long userId) {
        List<Friendship> friendships = friendshipMapper.selectList(new LambdaQueryWrapper<Friendship>()
                .eq(Friendship::getUserId, userId)
                .eq(Friendship::getStatus, STATUS_ACCEPTED)
                .orderByDesc(Friendship::getUpdateTime));

        if (friendships.isEmpty()) {
            return List.of();
        }

        // 批量查用户信息
        List<Long> friendIds = friendships.stream()
                .map(Friendship::getFriendId)
                .collect(Collectors.toList());
        List<User> users = userMapper.selectBatchIds(friendIds);
        Map<Long, User> userMap = users.stream()
                .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));

        List<FriendVO> result = new ArrayList<>();
        for (Friendship f : friendships) {
            User friend = userMap.get(f.getFriendId());
            if (friend == null) continue;
            result.add(FriendVO.builder()
                    .friendId(f.getFriendId())
                    .nickname(friend.getNickname())
                    .avatarUrl(friend.getAvatarUrl())
                    .remark(f.getRemark())
                    .status(f.getStatus())
                    .level(friend.getLevel())
                    .becomeTime(f.getUpdateTime())
                    .build());
        }
        return result;
    }

    /**
     * 修改备注名（只影响自己的那条记录）
     */
    @Transactional
    public void remark(Long userId, Long friendId, String remark) {
        Friendship friendship = getOwnRecord(userId, friendId);
        friendship.setRemark(remark);
        friendship.setUpdateTime(LocalDateTime.now());
        friendshipMapper.updateById(friendship);
    }

    /**
     * 屏蔽好友
     */
    @Transactional
    public void block(Long userId, Long friendId) {
        Friendship friendship = getOwnRecord(userId, friendId);
        if (friendship.getStatus() == STATUS_APPLYING) {
            throw new IllegalArgumentException("not friends yet");
        }
        friendship.setStatus(STATUS_BLOCKED);
        friendship.setUpdateTime(LocalDateTime.now());
        friendshipMapper.updateById(friendship);
    }

    private Friendship getOwnRecord(Long userId, Long friendId) {
        Friendship friendship = friendshipMapper.selectOne(new LambdaQueryWrapper<Friendship>()
                .eq(Friendship::getUserId, userId)
                .eq(Friendship::getFriendId, friendId));
        if (friendship == null) {
            throw new IllegalArgumentException("friendship not found");
        }
        return friendship;
    }
}
