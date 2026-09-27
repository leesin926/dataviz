package com.dataviz.auth.service.impl;

import com.dataviz.auth.client.UserServiceClient;
import com.dataviz.auth.client.dto.AuthUserDTO;
import com.dataviz.auth.dto.LoginDTO;
import com.dataviz.auth.dto.RefreshTokenDTO;
import com.dataviz.auth.dto.SmsLoginDTO;
import com.dataviz.auth.entity.SysLoginLog;
import com.dataviz.auth.mapper.LoginLogMapper;
import com.dataviz.auth.service.AuthService;
import com.dataviz.auth.service.SmsCodeService;
import com.dataviz.auth.vo.CaptchaVO;
import com.dataviz.auth.vo.LoginVO;
import com.dataviz.auth.vo.SmsSendVO;
import com.dataviz.auth.vo.TokenVO;
import com.dataviz.common.core.exception.BizException;
import com.dataviz.common.redis.util.CacheHelper;
import com.dataviz.common.security.model.LoginUser;
import com.dataviz.common.security.util.JwtHelper;
import com.dataviz.common.security.util.LoginSessionCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.imageio.ImageIO;
import javax.servlet.http.HttpServletRequest;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Authentication service implementation.
 * <p>
 * Handles the full login lifecycle: captcha verification, password check,
 * JWT generation, permission loading, login log recording, and token management.
 * 用户/角色/权限数据不在本服务库里，一律经 {@link UserServiceClient} 向 user-service 取（见 D39）。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserServiceClient userServiceClient;
    private final LoginLogMapper loginLogMapper;
    private final PasswordEncoder passwordEncoder;
    private final StringRedisTemplate redisTemplate;
    private final CacheHelper cacheHelper;
    private final SmsCodeService smsCodeService;

    private static final String CAPTCHA_PREFIX = "captcha:";
    /** 与 {@code sys_login_log.login_type} 的注释同词表：1-密码 2-短信 3-SSO */
    private static final int LOGIN_TYPE_PASSWORD = 1;
    private static final int LOGIN_TYPE_SMS = 2;
    private static final int LOGIN_TYPE_SSO = 3;
    private static final long DEFAULT_TENANT_ID = 1L;
    private static final String TOKEN_BLACKLIST_PREFIX = "token:blacklist:";
    private static final String USER_TOKEN_PREFIX = "user:token:";
    private static final Duration CAPTCHA_TTL = Duration.ofMinutes(5);
    private static final Duration REFRESH_TOKEN_TTL = Duration.ofDays(7);

    @Override
    public LoginVO login(LoginDTO loginDTO) {
        // 1. Verify captcha
        verifyCaptcha(loginDTO.getCaptchaKey(), loginDTO.getCaptchaCode());

        // 2. Load user by username
        AuthUserDTO user = userServiceClient.findByUsername(loginDTO.getUsername());
        if (user == null) {
            recordLoginLog(null, null, loginDTO.getUsername(), LOGIN_TYPE_PASSWORD, false, "User not found");
            throw new BizException("Invalid username or password");
        }

        // 3. Check user status
        if (user.getStatus() != null && user.getStatus() == 0) {
            recordLoginLog(user.getId(), user.getTenantId(), loginDTO.getUsername(), LOGIN_TYPE_PASSWORD, false, "Account disabled");
            throw new BizException("Account is disabled");
        }

        // 4. Verify password
        // 空哈希必须当场拒绝：不能依赖 matches() 对非法格式返回 false 的巧合，
        // 否则一条被清空口令的记录就成了任何人都进不去/或意外进得去的灰区。
        if (!StringUtils.hasText(user.getPassword())) {
            recordLoginLog(user.getId(), user.getTenantId(), loginDTO.getUsername(), LOGIN_TYPE_PASSWORD, false, "Password not set");
            log.warn("用户未设置登录口令，已拒绝登录: {}", loginDTO.getUsername());
            throw new BizException("Invalid username or password");
        }
        if (!passwordEncoder.matches(loginDTO.getPassword(), user.getPassword())) {
            recordLoginLog(user.getId(), user.getTenantId(), loginDTO.getUsername(), LOGIN_TYPE_PASSWORD, false, "Wrong password");
            throw new BizException("Invalid username or password");
        }

        // 5. 发会话——与短信登录共用同一条路，见 issueSession
        return issueSession(user, LOGIN_TYPE_PASSWORD);
    }

    @Override
    public SmsSendVO sendSmsCode(String phone, String terminal) {
        return smsCodeService.send(phone, terminal);
    }

    /**
     * 短信登录。<strong>口令这条凭证被验证码整体替代</strong>，所以除"不查口令"之外，
     * 后面发 token、写会话快照、记登录日志与密码登录一字不差（同一个 {@link #issueSession}）。
     */
    @Override
    public LoginVO smsLogin(SmsLoginDTO smsLoginDTO) {
        String phone = smsLoginDTO.getPhone();
        AuthUserDTO user = userServiceClient.findByPhone(phone);
        if (user == null) {
            // 发码时查得到、登录时查不到 = 中间被改库或删号，按"未绑定"处理并留一条日志
            recordLoginLog(null, null, phone, LOGIN_TYPE_SMS, false, "Phone not bound");
            throw new BizException("该手机号未绑定任何账号");
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            recordLoginLog(user.getId(), user.getTenantId(), user.getUsername(), LOGIN_TYPE_SMS, false, "Account disabled");
            throw new BizException("Account is disabled");
        }
        try {
            smsCodeService.verify(phone, smsLoginDTO.getTerminal(), smsLoginDTO.getCode());
        } catch (BizException e) {
            recordLoginLog(user.getId(), user.getTenantId(), user.getUsername(), LOGIN_TYPE_SMS, false, e.getMessage());
            throw e;
        }
        return issueSession(user, LOGIN_TYPE_SMS);
    }

    @Override
    public void logout(LoginUser currentUser, String authorization) {
        String token = bearerOf(authorization);
        if (token != null) {
            // Blacklist the token
            redisTemplate.opsForValue().set(
                    TOKEN_BLACKLIST_PREFIX + token, "1", Duration.ofHours(2));
        }
        if (currentUser == null) {
            // 没有主体就不猜 userId —— 猜的来源只能是调用方给的头，那正是本次要关掉的口子
            log.warn("登出请求缺少登录主体，已跳过会话清理");
            return;
        }
        redisTemplate.delete(USER_TOKEN_PREFIX + currentUser.getUserId());
        LoginSessionCache.evict(cacheHelper, currentUser.getUsername());
        log.info("User [{}] logged out", currentUser.getUserId());
    }

    @Override
    public TokenVO refreshToken(RefreshTokenDTO dto) {
        String refreshToken = dto.getRefreshToken();

        // Validate refresh token from Redis
        String userId = redisTemplate.opsForValue().get("refresh:" + refreshToken);
        if (userId == null) {
            throw new BizException("Refresh token is invalid or expired");
        }

        // Load user to get latest info
        AuthUserDTO user = userServiceClient.findById(Long.valueOf(userId));
        if (user == null || (user.getStatus() != null && user.getStatus() == 0)) {
            throw new BizException("User account is invalid");
        }

        // Generate new tokens
        List<String> roles = userServiceClient.findRoleCodes(user.getId());
        List<String> permissions = userServiceClient.findPermissionCodes(user.getId());

        LoginUser loginUser = new LoginUser();
        loginUser.setUserId(user.getId());
        loginUser.setUsername(user.getUsername());
        loginUser.setNickname(user.getNickname());
        loginUser.setTenantId(user.getTenantId() != null ? user.getTenantId().toString() : null);
        loginUser.setDeptId(user.getDeptId());
        loginUser.setRoles(new HashSet<>(roles));
        loginUser.setPermissions(new HashSet<>(permissions));

        // 必须回写快照：各服务拦截器判"登录态是否存在 + 有哪些权限"读的是这份 Redis 数据而不是 JWT。
        // 不写就有两个后果——快照一旦到期，换新 token 也永远 401（刷新这条自救路是死的）；
        // 以及授权变更后，会话会一直沿用登录那一刻的旧权限。
        cacheHelper.setSeconds(LoginSessionCache.key(user.getUsername()), loginUser, LoginSessionCache.TTL_SECONDS);

        String newAccessToken = JwtHelper.createToken(loginUser);
        String newRefreshToken = generateRefreshToken(user.getId().toString());

        // Invalidate old refresh token
        redisTemplate.delete("refresh:" + refreshToken);

        TokenVO tokenVO = new TokenVO();
        tokenVO.setAccessToken(newAccessToken);
        tokenVO.setRefreshToken(newRefreshToken);
        return tokenVO;
    }

    @Override
    public CaptchaVO generateCaptcha() {
        String captchaKey = UUID.randomUUID().toString().replace("-", "");
        // Generate simple math captcha code
        Random random = new Random();
        int a = random.nextInt(10) + 1;
        int b = random.nextInt(10) + 1;
        String answer = String.valueOf(a + b);
        String captchaText = a + " + " + b + " = ?";

        // Store answer in Redis with TTL
        redisTemplate.opsForValue().set(CAPTCHA_PREFIX + captchaKey, answer, CAPTCHA_TTL);

        // Generate base64 image for captcha
        String base64Image = generateCaptchaImage(captchaText);

        CaptchaVO captchaVO = new CaptchaVO();
        captchaVO.setCaptchaKey(captchaKey);
        captchaVO.setCaptchaImage(base64Image);
        captchaVO.setExpireSeconds((int) CAPTCHA_TTL.getSeconds());
        return captchaVO;
    }

    @Override
    public LoginVO ssoLogin(String ssoToken, String provider) {
        // Delegate to SSO provider for token validation
        String username = validateSsoToken(ssoToken, provider);
        AuthUserDTO user = userServiceClient.findByUsername(username);
        if (user == null) {
            throw new BizException("SSO user not found in system: " + username);
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BizException("Account is disabled");
        }
        return issueSession(user, LOGIN_TYPE_SSO);
    }

    @Override
    public LoginVO ssoCallback(String code, String state) {
        // SSO OAuth callback - exchange code for user info
        String username = exchangeCodeForUser(code, state);
        LoginDTO loginDTO = new LoginDTO();
        loginDTO.setUsername(username);
        loginDTO.setPassword(""); // SSO bypasses password
        return ssoLogin(code, "oauth");
    }

    @Override
    public TokenVO validateAndGetTokenInfo(String token) {
        if (!JwtHelper.validateToken(token)) {
            throw new BizException("Token is invalid or expired");
        }
        TokenVO tokenVO = new TokenVO();
        tokenVO.setAccessToken(token);
        tokenVO.setUserId(String.valueOf(JwtHelper.getUserId(token)));
        tokenVO.setUsername(JwtHelper.getUsername(token));
        return tokenVO;
    }

    @Override
    public boolean isTokenValid(String token) {
        // Check blacklist first
        Boolean isBlacklisted = redisTemplate.hasKey(TOKEN_BLACKLIST_PREFIX + token);
        if (Boolean.TRUE.equals(isBlacklisted)) {
            return false;
        }
        return JwtHelper.validateToken(token);
    }

    @Override
    public void revokeUserTokens(String userId) {
        redisTemplate.delete(USER_TOKEN_PREFIX + userId);
        log.info("All tokens revoked for user: {}", userId);
    }

    // ==================== Private helpers ====================

    /**
     * 一次已认证通过的登录 → 会话。<strong>密码登录与短信登录共用这一份</strong>，
     * 因为"怎么发 token"和"用什么凭证换的 token"是两件事，后者已经在调用方判完了。
     * <p>
     * 这里每一行都不能省：{@code login:user:{username}} 快照和各服务读的是同一份，
     * 不写它，签出来的 JWT 会在第一个业务请求上就被 AuthInterceptor 判 401；
     * {@code user:token:{userId}} 少了，登出就拉不黑映射。
     * </p>
     */
    private LoginVO issueSession(AuthUserDTO user, int loginType) {
        // 1. Load roles and permissions
        List<String> roles = userServiceClient.findRoleCodes(user.getId());
        List<String> permissions = userServiceClient.findPermissionCodes(user.getId());

        // 2. Generate JWT tokens
        LoginUser loginUser = new LoginUser();
        loginUser.setUserId(user.getId());
        loginUser.setUsername(user.getUsername());
        loginUser.setNickname(user.getNickname());
        loginUser.setTenantId(user.getTenantId() != null ? user.getTenantId().toString() : null);
        loginUser.setDeptId(user.getDeptId());
        loginUser.setRoles(new HashSet<>(roles));
        loginUser.setPermissions(new HashSet<>(permissions));

        String accessToken = JwtHelper.createToken(loginUser);
        String refreshToken = generateRefreshToken(user.getId().toString());

        // 3. Update login info —— 统计字段，回写失败不该把一次已认证的登录判为失败
        try {
            userServiceClient.recordLoginInfo(user.getId());
        } catch (Exception e) {
            log.warn("登录时间回写失败: userId={}, {}", user.getId(), e.getMessage());
        }

        // 4. Cache user token mapping for logout
        redisTemplate.opsForValue().set(
                USER_TOKEN_PREFIX + user.getId(), accessToken, Duration.ofHours(2));

        // Cache LoginUser for the service-layer AuthInterceptor
        cacheHelper.setSeconds(LoginSessionCache.key(user.getUsername()), loginUser, LoginSessionCache.TTL_SECONDS);

        // 5. Record login log
        recordLoginLog(user.getId(), user.getTenantId(), user.getUsername(), loginType, true, "Login success");

        log.info("User [{}] logged in successfully (loginType={})", user.getUsername(), loginType);

        // 6. Build response
        LoginVO loginVO = new LoginVO();
        loginVO.setAccessToken(accessToken);
        loginVO.setRefreshToken(refreshToken);
        loginVO.setUserId(user.getId());
        loginVO.setUsername(user.getUsername());
        loginVO.setRoles(roles);
        loginVO.setPermissions(permissions);
        return loginVO;
    }

    private void verifyCaptcha(String captchaKey, String captchaCode) {
        if (captchaKey == null || captchaCode == null) {
            throw new BizException("Captcha key and code are required");
        }
        String storedAnswer = redisTemplate.opsForValue().get(CAPTCHA_PREFIX + captchaKey);
        if (storedAnswer == null) {
            throw new BizException("Captcha has expired");
        }
        if (!storedAnswer.equalsIgnoreCase(captchaCode.trim())) {
            throw new BizException("Captcha code is incorrect");
        }
        // Delete used captcha
        redisTemplate.delete(CAPTCHA_PREFIX + captchaKey);
    }

    private String generateRefreshToken(String userId) {
        String refreshToken = UUID.randomUUID().toString().replace("-", "");
        redisTemplate.opsForValue().set("refresh:" + refreshToken, userId, REFRESH_TOKEN_TTL);
        return refreshToken;
    }

    private void recordLoginLog(Long userId, Long tenantId, String username, int loginType, boolean success, String message) {
        SysLoginLog loginLog = new SysLoginLog();
        loginLog.setTenantId(tenantId != null ? tenantId : DEFAULT_TENANT_ID);
        loginLog.setUserId(userId);
        loginLog.setUsername(username);
        loginLog.setLoginType(loginType);
        loginLog.setStatus(success);
        loginLog.setIp(getClientIp());
        loginLog.setUserAgent(truncate(getUserAgent(), 512));
        loginLog.setMessage(truncate(message, 256));
        loginLog.setLoginTime(LocalDateTime.now());
        loginLogMapper.insert(loginLog);
    }

    private String getClientIp() {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return "";
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isEmpty() && !"unknown".equalsIgnoreCase(forwarded)) {
            int comma = forwarded.indexOf(',');
            return comma > 0 ? forwarded.substring(0, comma).trim() : forwarded.trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isEmpty() && !"unknown".equalsIgnoreCase(realIp)) {
            return realIp;
        }
        String remoteAddr = request.getRemoteAddr();
        return remoteAddr != null ? remoteAddr : "";
    }

    private String getUserAgent() {
        HttpServletRequest request = currentRequest();
        return request != null ? request.getHeader("User-Agent") : "";
    }

    private HttpServletRequest currentRequest() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes != null ? attributes.getRequest() : null;
    }

    private static String bearerOf(String authorization) {
        if (authorization == null) {
            return null;
        }
        String value = authorization.startsWith("Bearer ") ? authorization.substring(7) : authorization;
        return value.trim().isEmpty() ? null : value;
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }

    private static final int CAPTCHA_WIDTH = 150;
    private static final int CAPTCHA_HEIGHT = 44;

    private String generateCaptchaImage(String text) {
        BufferedImage image = new BufferedImage(CAPTCHA_WIDTH, CAPTCHA_HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        Random random = new Random();

        g.setColor(new Color(240, 244, 250));
        g.fillRect(0, 0, CAPTCHA_WIDTH, CAPTCHA_HEIGHT);

        for (int i = 0; i < 8; i++) {
            g.setColor(new Color(random.nextInt(180) + 40, random.nextInt(180) + 40, random.nextInt(180) + 40));
            g.drawLine(random.nextInt(CAPTCHA_WIDTH), random.nextInt(CAPTCHA_HEIGHT),
                    random.nextInt(CAPTCHA_WIDTH), random.nextInt(CAPTCHA_HEIGHT));
        }

        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 26));
        for (int i = 0; i < text.length(); i++) {
            g.setColor(new Color(random.nextInt(100), random.nextInt(100), 120 + random.nextInt(100)));
            double angle = (random.nextDouble() - 0.5) * 0.35;
            int x = 8 + i * 15;
            int y = 30 + random.nextInt(6);
            g.rotate(angle, x, y);
            g.drawString(String.valueOf(text.charAt(i)), x, y);
            g.rotate(-angle, x, y);
        }
        g.dispose();

        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ImageIO.write(image, "png", bos);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(bos.toByteArray());
        } catch (IOException e) {
            log.error("Failed to render captcha image", e);
            throw new BizException("Failed to generate captcha image");
        }
    }

    private String validateSsoToken(String ssoToken, String provider) {
        // Placeholder for SSO token validation logic
        throw new BizException("SSO provider not configured: " + provider);
    }

    private String exchangeCodeForUser(String code, String state) {
        // Placeholder for OAuth code exchange logic
        throw new BizException("OAuth callback not configured");
    }
}
