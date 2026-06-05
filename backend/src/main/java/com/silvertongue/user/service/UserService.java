package com.silvertongue.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.silvertongue.user.dto.LoginRequest;
import com.silvertongue.user.dto.LoginResponse;
import com.silvertongue.user.dto.RegisterRequest;
import com.silvertongue.user.dto.UserProfileResponse;
import com.silvertongue.user.entity.User;
import com.silvertongue.user.mapper.UserMapper;
import com.silvertongue.user.dto.WxAccessTokenResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Slf4j
@Service
public class UserService {

    private static final int STATUS_NORMAL = 0;

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final WeChatService weChatService;

    public UserService(UserMapper userMapper,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       WeChatService weChatService) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.weChatService = weChatService;
    }

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

    /**
     * 微信 OAuth 回调登录：用 code 换 token → 查 unionid → 已有则登录，没有则自动注册
     */
    @Transactional
    public LoginResponse wxLogin(String code) {
        WxAccessTokenResponse wxToken = weChatService.exchangeCodeForToken(code);

        String unionid = wxToken.getUnionid();
        String openid = wxToken.getOpenid();

        // 优先按 unionid 查找（跨应用统一标识）
        User user = null;
        if (StringUtils.hasText(unionid)) {
            user = findByUnionId(unionid);
        }

        // unionid 未找到，尝试按 openid 查找（本应用内）
        if (user == null && StringUtils.hasText(openid)) {
            user = findByOpenId(openid);
        }

        if (user != null) {
            // 已有账号：补全缺失的微信字段后直接登录
            boolean updated = false;
            if (!StringUtils.hasText(user.getWxUnionid()) && StringUtils.hasText(unionid)) {
                user.setWxUnionid(unionid);
                updated = true;
            }
            if (!StringUtils.hasText(user.getWxOpenid()) && StringUtils.hasText(openid)) {
                user.setWxOpenid(openid);
                updated = true;
            }
            if (updated) {
                user.setUpdateTime(LocalDateTime.now());
                userMapper.updateById(user);
            }
        } else {
            // 新用户：自动注册
            user = autoRegisterByWx(unionid, openid);
        }

        String token = jwtService.generateToken(user);
        return new LoginResponse(token, "Bearer", jwtService.getExpirationSeconds(), toProfile(user));
    }

    /**
     * 已有密码账号绑定微信
     */
    @Transactional
    public UserProfileResponse bindWx(Long userId, String code) {
        User user = userMapper.selectById(userId);
        if (user == null || user.getStatus() == null || user.getStatus() != STATUS_NORMAL) {
            throw new IllegalArgumentException("user not found");
        }

        WxAccessTokenResponse wxToken = weChatService.exchangeCodeForToken(code);
        String unionid = wxToken.getUnionid();
        String openid = wxToken.getOpenid();

        // 防止一个微信绑定多个账号
        if (StringUtils.hasText(unionid)) {
            User existing = findByUnionId(unionid);
            if (existing != null && !existing.getId().equals(userId)) {
                throw new IllegalArgumentException("this WeChat account is already bound to another user");
            }
        }
        if (StringUtils.hasText(openid)) {
            User existing = findByOpenId(openid);
            if (existing != null && !existing.getId().equals(userId)) {
                throw new IllegalArgumentException("this WeChat account is already bound to another user");
            }
        }

        user.setWxOpenid(openid);
        if (StringUtils.hasText(unionid)) {
            user.setWxUnionid(unionid);
        }
        user.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(user);

        return toProfile(user);
    }

    private User autoRegisterByWx(String unionid, String openid) {
        String uniqueId = StringUtils.hasText(unionid) ? unionid : openid;
        if (uniqueId == null) {
            throw new RuntimeException("WeChat returned neither unionid nor openid");
        }

        String username = "wx_" + uniqueId.substring(0, Math.min(uniqueId.length(), 12));

        LocalDateTime now = LocalDateTime.now();
        User user = new User();
        user.setUsername(username);
        user.setPasswordHash("");
        user.setNickname(username);
        user.setWxOpenid(openid);
        user.setWxUnionid(unionid);
        user.setPoints(0L);
        user.setLevel("beginner");
        user.setSignInCount(0);
        user.setStatus(STATUS_NORMAL);
        user.setDeleted(0);
        user.setCreateTime(now);
        user.setUpdateTime(now);
        userMapper.insert(user);

        log.info("Auto-registered WeChat user: id={}, username={}, unionid={}", user.getId(), username, unionid);
        return user;
    }

    private User findByUnionId(String unionid) {
        return userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getWxUnionid, unionid)
                .last("LIMIT 1"));
    }

    private User findByOpenId(String openid) {
        return userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getWxOpenid, openid)
                .last("LIMIT 1"));
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
