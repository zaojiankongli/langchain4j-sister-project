package com.zjkl.auth.service;

import com.zjkl.auth.dto.CompleteProfileRequest;
import com.zjkl.auth.dto.BindEmailRequest;
import com.zjkl.auth.dto.LoginRequest;
import com.zjkl.auth.exception.UnauthorizedException;
import com.zjkl.common.ErrorCode;
import com.zjkl.common.exception.BusinessException;
import com.zjkl.common.config.properties.AuthProperties;
import com.zjkl.common.config.properties.WechatProperties;
import com.zjkl.common.util.HashUtil;
import com.zjkl.common.util.JwtUtil;
import com.zjkl.user.domain.User;
import com.zjkl.user.domain.UserWechatBinding;
import com.zjkl.user.mapper.UserMapper;
import com.zjkl.user.mapper.UserWechatBindingMapper;
import com.zjkl.user.service.UserProfileManageService;
import com.zjkl.user.service.UserProfileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
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
    private static final String WX_BIND_PREFIX = "auth:wx:bind:";
    private static final long WX_BIND_EXPIRE_MINUTES = 10;

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

    private static final String COMPARE_AND_DELETE_LOCK_SCRIPT =
        "local val = redis.call('get', KEYS[1]) " +
        "if val == ARGV[1] then " +
        "    return redis.call('del', KEYS[1]) " +
        "else " +
        "    return 0 " +
        "end";

    private static final String ATOMIC_BLACKLIST_SCRIPT =
        "local exists = redis.call('exists', KEYS[1]) " +
        "if exists == 1 then return 0 end " +
        "redis.call('set', KEYS[1], ARGV[1], 'EX', tonumber(ARGV[2])) " +
        "return 1";

    private final UserMapper userMapper;
    private final UserWechatBindingMapper userWechatBindingMapper;
    private final JwtUtil jwtUtil;
    private final StringRedisTemplate redisTemplate;
    private final JavaMailSender mailSender;
    private final UserProfileManageService userProfileManageService;
    private final UserProfileService userProfileService;
    private final RestClient restClient;
    private final Random random = new SecureRandom();
    private final String fromAddress;
    private final DefaultRedisScript<Long> verifyAndDelScript;
    private final DefaultRedisScript<Long> compareAndDeleteLockScript;
    private final DefaultRedisScript<Long> atomicBlacklistScript;
    private final AuthProperties authProperties;
    private final WechatProperties wechatProperties;
    
    public AuthService(UserMapper userMapper,
                       UserWechatBindingMapper userWechatBindingMapper,
                       JwtUtil jwtUtil,
                       StringRedisTemplate redisTemplate,
                       JavaMailSender mailSender,
                       Environment env,
                       UserProfileManageService userProfileManageService,
                       UserProfileService userProfileService,
                       AuthProperties authProperties,
                       WechatProperties wechatProperties,
                       RestClient restClient) {
        this.userMapper = userMapper;
        this.userWechatBindingMapper = userWechatBindingMapper;
        this.jwtUtil = jwtUtil;
        this.redisTemplate = redisTemplate;
        this.mailSender = mailSender;
        this.fromAddress = env.getProperty("spring.mail.username");
        this.verifyAndDelScript = new DefaultRedisScript<>(VERIFY_AND_DEL_SCRIPT, Long.class);
        this.compareAndDeleteLockScript = new DefaultRedisScript<>(COMPARE_AND_DELETE_LOCK_SCRIPT, Long.class);
        this.atomicBlacklistScript = new DefaultRedisScript<>(ATOMIC_BLACKLIST_SCRIPT, Long.class);
        this.userProfileManageService = userProfileManageService;
        this.userProfileService = userProfileService;
        this.authProperties = authProperties;
        this.wechatProperties = wechatProperties;
        this.restClient = restClient;
    }

    /** 邮箱脱敏：a***@example.com */
    private static String maskEmail(String email) {
        if (email == null || !email.contains("@")) return "***";
        int at = email.indexOf('@');
        return email.charAt(0) + "***" + email.substring(at);
    }
    
    /**
     * 发送验证码
     */
    public void sendCode(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("邮箱不能为空");
        }
        email = email.trim().toLowerCase(java.util.Locale.ROOT);

        String code = String.format("%06d", random.nextInt(1000000));

        // 先将验证码存入 Redis，再发送邮件（确保存储成功后再发）
        redisTemplate.opsForValue().set(CODE_PREFIX + email, code, CODE_EXPIRE_MINUTES, TimeUnit.MINUTES);

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(email);
        message.setSubject("知微 Zeeva - 验证码");
        message.setText("您的验证码是：" + code + "\n\n验证码 " + CODE_EXPIRE_MINUTES + " 分钟内有效，请勿泄露给他人。");
        try {
            mailSender.send(message);
        } catch (Exception e) {
            redisTemplate.delete(CODE_PREFIX + email);
            log.error("验证码邮件发送失败: email={}", maskEmail(email), e);
            throw new BusinessException(ErrorCode.AUTH_CODE_SEND_FAILED, "验证码发送失败，请稍后再试");
        }

        log.info("验证码已发送至 {}", maskEmail(email));
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
        email = email.trim().toLowerCase(java.util.Locale.ROOT);
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
        String lockToken = UUID.randomUUID().toString();
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(lockKey, lockToken, 10, TimeUnit.SECONDS);
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
                log.info("新用户注册成功：email={}, userId={}, username={}", maskEmail(email), user.getId(), user.getUsername());
            } else {
                if (username != null && !username.isBlank() && !username.equals(user.getUsername())) {
                    user.setUsername(username.trim());
                    userMapper.update(user);
                }
                log.info("用户登录成功：email={}, userId={}", maskEmail(email), user.getId());
            }

            // 4. 更新最后活跃时间
            userMapper.updateLastActiveAt(user.getId());

            // 5. 生成 token
            String accessToken = jwtUtil.generateAccessToken(user);
            String refreshToken = jwtUtil.generateRefreshToken(user);

            // 6. 构建响应
            return buildLoginResult(user, isNewUser);
        } finally {
            redisTemplate.execute(compareAndDeleteLockScript, List.of(lockKey), lockToken);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> wxLogin(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("微信 code 不能为空");
        }

        Map<String, Object> session = exchangeCodeForSession(code.trim());
        String openid = stringValue(session.get("openid"));
        String unionid = stringValue(session.get("unionid"));

        if (openid == null || openid.isBlank()) {
            throw new UnauthorizedException("微信登录失败，请重试");
        }

        UserWechatBinding binding = userWechatBindingMapper.findBoundByAppidAndOpenid(wechatProperties.getAppid(), openid);
        if (binding != null) {
            User user = userMapper.findById(binding.getUserId());
            if (user == null) {
                throw new UnauthorizedException("微信绑定账号不存在，请重新绑定");
            }
            userWechatBindingMapper.updateLastLoginAt(binding.getId());
            userMapper.updateLastActiveAt(user.getId());
            return buildLoginResult(user, false, statusFor(user));
        }

        String bindToken = UUID.randomUUID().toString().replace("-", "");
        String payload = String.join("|",
                safe(wechatProperties.getAppid()),
                safe(openid),
                safe(unionid));
        redisTemplate.opsForValue().set(WX_BIND_PREFIX + bindToken, payload, WX_BIND_EXPIRE_MINUTES, TimeUnit.MINUTES);

        Map<String, Object> result = new HashMap<>();
        result.put("status", "WECHAT_UNBOUND");
        result.put("bindToken", bindToken);
        result.put("expiresIn", WX_BIND_EXPIRE_MINUTES * 60);
        result.put("nextAction", "BIND_EMAIL");
        result.put("requiresEmailBind", true);
        result.put("emailBound", false);
        result.put("isNewUser", true);
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> bindEmail(BindEmailRequest request) {
        String email = normalizeEmail(request.email());
        String bindToken = request.bindToken();

        String bindPayload = redisTemplate.opsForValue().get(WX_BIND_PREFIX + bindToken);
        if (bindPayload == null || bindPayload.isBlank()) {
            throw new UnauthorizedException("微信登录已过期，请重新登录");
        }

        String[] parts = bindPayload.split("\\|", -1);
        String wechatAppid = parts.length > 0 ? emptyToNull(parts[0]) : null;
        String openid = parts.length > 1 ? emptyToNull(parts[1]) : null;
        String unionid = parts.length > 2 ? emptyToNull(parts[2]) : null;

        if (wechatAppid == null || openid == null) {
            throw new UnauthorizedException("微信登录已过期，请重新登录");
        }

        Long verified = redisTemplate.execute(verifyAndDelScript, List.of(CODE_PREFIX + email), request.code());
        if (verified == null || verified == -1) {
            throw new UnauthorizedException("验证码已过期，请重新获取");
        }
        if (verified == 0) {
            throw new UnauthorizedException("验证码错误");
        }

        UserWechatBinding existingOpenidBinding = userWechatBindingMapper.findBoundByAppidAndOpenid(wechatAppid, openid);
        if (existingOpenidBinding != null) {
            throw new BusinessException(409, "该微信已绑定其他账号");
        }

        User user = userMapper.findByEmail(email);
        boolean isNewUser = false;

        if (user == null) {
            String tempUsername = email.substring(0, email.indexOf('@'));
            user = userProfileManageService.createUser(email, tempUsername);
            isNewUser = true;
        } else {
            UserWechatBinding existingUserBinding = userWechatBindingMapper.findBoundByUserIdAndAppid(user.getId(), wechatAppid);
            if (existingUserBinding != null) {
                throw new BusinessException(409, "该邮箱已绑定其他微信账号");
            }
        }

        if (user.getUsername() == null || user.getUsername().isBlank()) {
            user.setUsername(email.substring(0, email.indexOf('@')));
            userMapper.update(user);
        }

        UserWechatBinding newBinding = UserWechatBinding.builder()
                .userId(user.getId())
                .wechatAppid(wechatAppid)
                .openid(openid)
                .unionid(unionid)
                .bindStatus("bound")
                .boundAt(LocalDateTime.now())
                .lastLoginAt(LocalDateTime.now())
                .build();
        userWechatBindingMapper.insert(newBinding);
        userMapper.updateLastActiveAt(user.getId());

        redisTemplate.delete(WX_BIND_PREFIX + bindToken);

        log.debug("微信会话已绑定成功: userId={}, openid={}", user.getId(), openid);

        return buildLoginResult(user, isNewUser, statusFor(user));
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> syncEmailAccount(String userId) {
        User user = userMapper.findById(userId);
        if (user == null) {
            throw new UnauthorizedException("请先登录");
        }

        userMapper.updateLastActiveAt(userId);
        User freshUser = userMapper.findById(userId);
        if (freshUser == null) {
            throw new UnauthorizedException("请先登录");
        }

        Map<String, Object> result = buildLoginResult(freshUser, false, statusFor(freshUser));
        result.put("fullProfile", userProfileService.getProfile(userId));
        result.put("syncedAt", LocalDateTime.now());
        result.put("linkedEmail", freshUser.getEmail());
        UserWechatBinding binding = userWechatBindingMapper.findBoundByUserIdAndAppid(userId, wechatProperties.getAppid());
        if (binding != null) {
            result.put("wxBoundAt", binding.getBoundAt());
        }
        return result;
    }
    
    /**
     * 刷新 token
     */
    public Map<String, Object> refreshToken(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new IllegalArgumentException("refreshToken 不能为空");
        }

        String tokenHash = HashUtil.sha256Hex(refreshToken);
        String blacklistKey = TOKEN_BLACKLIST_PREFIX + tokenHash;

        // Atomic check-and-blacklist to prevent TOCTOU race condition
        Long blacklisted = redisTemplate.execute(atomicBlacklistScript,
                List.of(blacklistKey), "1", String.valueOf(getRefreshTokenExpireSeconds()));
        if (blacklisted == null || blacklisted == 0) {
            throw new UnauthorizedException("请重新登录");
        }

        String userId = jwtUtil.parseRefreshToken(refreshToken);
        if (userId == null) {
            throw new UnauthorizedException("请重新登录");
        }

        User user = userMapper.findById(userId);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在");
        }

        String newAccessToken = jwtUtil.generateAccessToken(user);
        String newRefreshToken = jwtUtil.generateRefreshToken(user);

        return of("accessToken", newAccessToken, "refreshToken", newRefreshToken);
    }

    public void logout(String userId, String refreshToken, String accessToken) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            String blacklistKey = TOKEN_BLACKLIST_PREFIX + HashUtil.sha256Hex(refreshToken);
            redisTemplate.opsForValue().set(blacklistKey, "1", getRefreshTokenExpireSeconds(), TimeUnit.SECONDS);
        }
        if (accessToken != null && !accessToken.isBlank()) {
            String blacklistKey = TOKEN_BLACKLIST_PREFIX + HashUtil.sha256Hex(accessToken);
            long remainingMs = jwtUtil.getAccessTokenRemainingTime(accessToken);
            if (remainingMs > 0) {
                redisTemplate.opsForValue().set(blacklistKey, "1", remainingMs, TimeUnit.MILLISECONDS);
            }
        }
        log.info("用户登出: userId={}", userId);
    }

    private long getRefreshTokenExpireSeconds() {
        return authProperties.getRefreshTokenExpiration() / 1000 + 60; // +60s buffer
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

    private Map<String, Object> buildLoginResult(User user, boolean isNewUser) {
        return buildLoginResult(user, isNewUser, statusFor(user));
    }

    private Map<String, Object> buildLoginResult(User user, boolean isNewUser, String status) {
        String accessToken = jwtUtil.generateAccessToken(user);
        String refreshToken = jwtUtil.generateRefreshToken(user);
        Map<String, Object> result = new HashMap<>();
        result.put("status", status);
        result.put("accessToken", accessToken);
        result.put("refreshToken", refreshToken);
        result.put("user", userProfileManageService.buildUserInfo(user));
        result.put("requiresProfileComplete", user.requiresProfileComplete());
        result.put("isNewUser", isNewUser);
        result.put("emailBound", true);
        result.put("emailExists", !isNewUser);
        result.put("accountType", isNewUser ? "new" : "existing");
        return result;
    }

    private static String statusFor(User user) {
        return user.requiresProfileComplete() ? "PROFILE_INCOMPLETE" : "LOGGED_IN";
    }

    private Map<String, Object> exchangeCodeForSession(String code) {
        String url = UriComponentsBuilder
                .fromHttpUrl(wechatProperties.getCodeToSessionUrl())
                .queryParam("appid", wechatProperties.getAppid())
                .queryParam("secret", wechatProperties.getSecret())
                .queryParam("js_code", code)
                .queryParam("grant_type", "authorization_code")
                .toUriString();

        Map<String, Object> response = restClient.get()
                .uri(url)
                .retrieve()
                .body(Map.class);

        if (response == null) {
            throw new UnauthorizedException("微信登录失败，请重试");
        }

        Object errcode = response.get("errcode");
        if (errcode instanceof Number number && number.intValue() != 0) {
            throw new UnauthorizedException("微信登录失败，请重试");
        }
        return response;
    }

    private static String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("邮箱不能为空");
        }
        return email.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
