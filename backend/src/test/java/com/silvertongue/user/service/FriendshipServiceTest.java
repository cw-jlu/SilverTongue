package com.silvertongue.user.service;

import com.silvertongue.user.dto.FriendVO;
import com.silvertongue.user.entity.Friendship;
import com.silvertongue.user.entity.User;
import com.silvertongue.user.mapper.FriendshipMapper;
import com.silvertongue.user.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FriendshipServiceTest {

    @Mock
    private FriendshipMapper friendshipMapper;

    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private FriendshipService friendshipService;

    @Test
    void applyShouldRejectAddingYourself() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> friendshipService.apply(3L, 3L));

        assertEquals("cannot add yourself as friend", exception.getMessage());
    }

    @Test
    void applyShouldInsertPendingRequestWhenTargetIsActive() {
        User target = new User();
        target.setId(8L);
        target.setStatus(0);

        when(userMapper.selectById(8L)).thenReturn(target);
        when(friendshipMapper.selectOne(any())).thenReturn(null);

        friendshipService.apply(5L, 8L);

        ArgumentCaptor<Friendship> captor = ArgumentCaptor.forClass(Friendship.class);
        verify(friendshipMapper).insert(captor.capture());
        Friendship inserted = captor.getValue();
        assertEquals(5L, inserted.getUserId());
        assertEquals(8L, inserted.getFriendId());
        assertEquals(0, inserted.getStatus());
        assertNotNull(inserted.getCreateTime());
    }

    @Test
    void acceptShouldMarkApplyAcceptedAndCreateReverseRecord() {
        Friendship apply = new Friendship();
        apply.setId(12L);
        apply.setUserId(5L);
        apply.setFriendId(8L);
        apply.setStatus(0);

        when(friendshipMapper.selectById(12L)).thenReturn(apply);
        when(friendshipMapper.selectOne(any())).thenReturn(null);

        friendshipService.accept(8L, 12L);

        ArgumentCaptor<Friendship> updateCaptor = ArgumentCaptor.forClass(Friendship.class);
        ArgumentCaptor<Friendship> insertCaptor = ArgumentCaptor.forClass(Friendship.class);
        verify(friendshipMapper).updateById(updateCaptor.capture());
        verify(friendshipMapper).insert(insertCaptor.capture());

        assertEquals(1, updateCaptor.getValue().getStatus());
        assertEquals(8L, insertCaptor.getValue().getUserId());
        assertEquals(5L, insertCaptor.getValue().getFriendId());
        assertEquals(1, insertCaptor.getValue().getStatus());
    }

    @Test
    void listFriendsShouldMergeFriendshipAndUserProfileData() {
        Friendship friendship = new Friendship();
        friendship.setUserId(1L);
        friendship.setFriendId(2L);
        friendship.setStatus(1);
        friendship.setRemark("teammate");
        friendship.setUpdateTime(LocalDateTime.now());

        User friend = new User();
        friend.setId(2L);
        friend.setNickname("Bob");
        friend.setAvatarUrl("avatar.png");
        friend.setLevel("B2");

        when(friendshipMapper.selectList(any())).thenReturn(List.of(friendship));
        when(userMapper.selectBatchIds(List.of(2L))).thenReturn(List.of(friend));

        List<FriendVO> friends = friendshipService.listFriends(1L);

        assertEquals(1, friends.size());
        assertEquals(2L, friends.get(0).getFriendId());
        assertEquals("Bob", friends.get(0).getNickname());
        assertEquals("teammate", friends.get(0).getRemark());
        assertEquals("B2", friends.get(0).getLevel());
    }

    @Test
    void blockShouldRejectPendingRelationship() {
        Friendship applying = new Friendship();
        applying.setStatus(0);

        when(friendshipMapper.selectOne(any())).thenReturn(applying);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> friendshipService.block(1L, 2L));

        assertEquals("not friends yet", exception.getMessage());
        verify(friendshipMapper, never()).updateById(any(Friendship.class));
    }
}
