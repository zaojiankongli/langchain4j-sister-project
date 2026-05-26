package com.zjkl.auth.service;

import com.zjkl.auth.dto.CompleteProfileRequest;
import com.zjkl.auth.dto.LoginRequest;
import com.zjkl.auth.exception.UnauthorizedException;
import com.zjkl.common.util.JwtUtil;
import com.zjkl.user.domain.User;
import com.zjkl.user.mapper.UserMapper;
import com.zjkl.user.service.UserProfileManageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import static java.util.Map.of;

/**
 * 认证服务
 */
@Service
public class AuthService {
    
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final String CODE_PREFIX = "auth:code:";
    private static final long CODE_EXPIRE_MINUTES = 5;
    private static final String TOKEN_BLACKLIST_PREFIX = "auth:token:blacklist:";
    private static final long REFRESH_TOKEN_EXPIRE_SECONDS = 7 * 24 * 3600; // 7 days

    private static final String VERIFY_AND_DEL_SCRIPT =
        "local code = redis.call('get', KEYS[1]) " +
        "if code == ARGV[1] then " +
        "    redis.call('del', KEYS[1]) " +
        "    return 1 " +
        "elseif code == false then " +
        "    return -1 " +
        "else " +
        "    return 0 " +
        "end";

    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;
    private final StringRedisTemplate redisTemplate;
    private final JavaMailSender mailSender;
    private final UserProfileManageService userProfileManageService;
    private final Random random = new Random();
    private final String fromAddress;
    private final DefaultRedisScript<Long> verifyAndDelScript;
    
    public AuthService(UserMapper userMapper, JwtUtil jwtUtil, 
                       StringRedisTemplate redisTemplate,
                       JavaMailSender mailSender,
                       Environment env,
                       UserProfileManageService userProfileManageService) {
        this.userMapper = userMapper;
        this.jwtUtil = jwtUtil;
        this.redisTemplate = redisTemplate;
        this.mailSender = mailSender;
        this.fromAddress = env.getProperty("spring.mail.username");
        this.verifyAndDelScript = new DefaultRedisScript<>(VERIFY_AND_DEL_SCRIPT, Long.class);
        this.userProfileManageService = userProfileManageService;
    }
    
    /**
     * 发送验证码
     */
    public void sendCode(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("邮箱不能为空");
        }

        String code = String.format("%06d", random.nextInt(1000000));

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(email);
        message.setSubject("知微 Zeeva - 验证码");
        message.setText("您的验证码是：" + code + "\n\n验证码 " + CODE_EXPIRE_MINUTES + " 分钟内有效，请勿泄露给他人。");
        mailSender.send(message);

        redisTemplate.opsForValue().set(CODE_PREFIX + email, code, CODE_EXPIRE_MINUTES, TimeUnit.MINUTES);
        log.info("验证码已发送至 {}", email);
    }
    
    /**
     * 登录/注册（统一入口）
     *
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> login(LoginRequest request) {
        String email = request.email();
        String code = request.code();
        String username = request.username();

        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("邮箱不能为空");
        }
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("验证码不能为空");
        }

        // 1. 原子化验证+删除验证码
        Long result = redisTemplate.execute(verifyAndDelScript, List.of(CODE_PREFIX + email), code);
        if (result == null || result == -1) {
            throw new UnauthorizedException("验证码已过期，请重新获取");
        }
        if (result == 0) {
            throw new UnauthorizedException("验证码错误");
        }

        // 2. 分布式锁防止并发注册同一邮箱
        String lockKey = "auth:login:lock:" + email;
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(lockKey, "1", 10, TimeUnit.SECONDS);
        if (Boolean.FALSE.equals(locked)) {
            throw new IllegalArgumentException("操作过于频繁，请稍后重试");
        }
        try {
            // 3. 查找用户
            User user = userMapper.findByEmail(email);
            boolean isNewUser = false;

            if (user == null) {
                String tempUsername = (username != null && !username.isBlank()) ? username.trim() : email.substring(0, email.indexOf('@'));
                user = userProfileManageService.createUser(email, tempUsername);
                isNewUser = true;
                log.info("新用户注册成功：email={}, userId={}, username={}", email, user.getId(), user.getUsername());
            } else {
                if (username != null && !username.isBlank() && !username.equals(user.getUsername())) {
                    user.setUsername(username.trim());
                    userMapper.update(user);
                }
                log.info("用户登录成功：email={}, userId={}", email, user.getId());
            }

            // 4. 更新最后活跃时间
            userMapper.updateLastActiveAt(user.getId());

            // 5. 生成 token
            String accessToken = jwtUtil.generateAccessToken(user);
            String refreshToken = jwtUtil.generateRefreshToken(user);

            // 6. 构建响应
            return of(
                "accessToken", accessToken,
                "refreshToken", refreshToken,
                "user", userProfileManageService.buildUserInfo(user),
                "requiresProfileComplete", user.requiresProfileComplete(),
                "isNewUser", isNewUser
            );
        } finally {
            redisTemplate.delete(lockKey);
        }
    }
    
    /**
     * 刷新 token
     */
    public Map<String, Object> refreshToken(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new IllegalArgumentException("refreshToken 不能为空");
        }

        String blacklistKey = TOKEN_BLACKLIST_PREFIX + refreshToken;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(blacklistKey))) {
            throw new UnauthorizedException("refreshToken 已失效，请重新登录");
        }

        String userId = jwtUtil.parseRefreshToken(refreshToken);
        if (userId == null) {
            throw new UnauthorizedException("refreshToken 已过期或无效");
        }

        User user = userMapper.findById(userId);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在");
        }

        String newAccessToken = jwtUtil.generateAccessToken(user);
        String newRefreshToken = jwtUtil.generateRefreshToken(user);

        return of("accessToken", newAccessToken, "refreshToken", newRefreshToken);
    }

    public void logout(String userId) {
        log.info("用户登出: userId={}", userId);
    }

    public void logout(String userId, String refreshToken) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            String blacklistKey = TOKEN_BLACKLIST_PREFIX + refreshToken;
            redisTemplate.opsForValue().set(blacklistKey, "1", REFRESH_TOKEN_EXPIRE_SECONDS, TimeUnit.SECONDS);
        }
        log.info("用户登出: userId={}", userId);
    }
    
    /**
     * 完善个人资料（首次登录必填）
     * 
     * @param userId 用户 ID
     * @param request 完善资料请求
     */
    @Transactional(rollbackFor = Exception.class)
    public void completeProfile(String userId, CompleteProfileRequest request) {
        userProfileManageService.completeProfile(userId, request);
    }
}
