package com.silvertongue.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.silvertongue.user.dto.LoginRequest;
import com.silvertongue.user.dto.LoginResponse;
import com.silvertongue.user.dto.RegisterRequest;
import com.silvertongue.user.dto.UserProfileResponse;
import com.silvertongue.user.entity.User;
import com.silvertongue.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class UserService {

    private static final int STATUS_NORMAL = 0;

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Transactional
    public UserProfileResponse register(RegisterRequest request) {
        String username = request.getUsername().trim();
        if (existsByUsername(username)) {
            throw new IllegalArgumentException("username already exists");
        }

        LocalDateTime now = LocalDateTime.now();
        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setNickname(StringUtils.hasText(request.getNickname()) ? request.getNickname().trim() : username);
        user.setPoints(0L);
        user.setLevel("beginner");
        user.setSignInCount(0);
        user.setStatus(STATUS_NORMAL);
        user.setDeleted(0);
        user.setCreateTime(now);
        user.setUpdateTime(now);
        userMapper.insert(user);

        return toProfile(user);
    }

    public LoginResponse login(LoginRequest request) {
        User user = findByUsername(request.getUsername().trim());
        if (user == null || user.getStatus() == null || user.getStatus() != STATUS_NORMAL) {
            throw new IllegalArgumentException("invalid username or password");
        }
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("invalid username or password");
        }

        String token = jwtService.generateToken(user);
        return new LoginResponse(token, "Bearer", jwtService.getExpirationSeconds(), toProfile(user));
    }

    public UserProfileResponse getProfile(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null || user.getStatus() == null || user.getStatus() != STATUS_NORMAL) {
            throw new IllegalArgumentException("user not found");
        }
        return toProfile(user);
    }

    private boolean existsByUsername(String username) {
        return userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, username)) > 0;
    }

    private User findByUsername(String username) {
        return userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, username)
                .last("LIMIT 1"));
    }

    private UserProfileResponse toProfile(User user) {
        return UserProfileResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .avatarUrl(user.getAvatarUrl())
                .points(user.getPoints())
                .level(user.getLevel())
                .signInCount(user.getSignInCount())
                .status(user.getStatus())
                .createTime(user.getCreateTime())
                .build();
    }
}
