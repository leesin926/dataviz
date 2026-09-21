package com.dataviz.auth.service.impl;

import com.dataviz.auth.client.UserServiceClient;
import com.dataviz.auth.client.dto.AuthUserDTO;
import com.dataviz.auth.dto.LoginDTO;
import com.dataviz.auth.dto.RefreshTokenDTO;
import com.dataviz.auth.entity.SysLoginLog;
import com.dataviz.auth.mapper.LoginLogMapper;
import com.dataviz.auth.service.AuthService;
import com.dataviz.auth.vo.CaptchaVO;
import com.dataviz.auth.vo.LoginVO;
import com.dataviz.auth.vo.TokenVO;
import com.dataviz.common.core.exception.BizException;
import com.dataviz.common.redis.util.CacheHelper;
import com.dataviz.common.security.context.SecurityContextHolder;
import com.dataviz.common.security.model.LoginUser;
import com.dataviz.common.security.util.JwtHelper;
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

    private static final String CAPTCHA_PREFIX = "captcha:";
    private static final String USER_CACHE_PREFIX = "login:user:";
    private static final int LOGIN_TYPE_PASSWORD = 1;
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
            recordLoginLog(null, null, loginDTO.getUsername(), false, "User not found");
            throw new BizException("Invalid username or password");
        }

        // 3. Check user status
        if (user.getStatus() != null && user.getStatus() == 0) {
            recordLoginLog(user.getId(), user.getTenantId(), loginDTO.getUsername(), false, "Account disabled");
            throw new BizException("Account is disabled");
        }

        // 4. Verify password
        // 空哈希必须当场拒绝：不能依赖 matches() 对非法格式返回 false 的巧合，
        // 否则一条被清空口令的记录就成了任何人都进不去/或意外进得去的灰区。
        if (!StringUtils.hasText(user.getPassword())) {
            recordLoginLog(user.getId(), user.getTenantId(), loginDTO.getUsername(), false, "Password not set");
            log.warn("用户未设置登录口令，已拒绝登录: {}", loginDTO.getUsername());
            throw new BizException("Invalid username or password");
        }
        if (!passwordEncoder.matches(loginDTO.getPassword(), user.getPassword())) {
            recordLoginLog(user.getId(), user.getTenantId(), loginDTO.getUsername(), false, "Wrong password");
            throw new BizException("Invalid username or password");
        }

        // 5. Load roles and permissions
        List<String> roles = userServiceClient.findRoleCodes(user.getId());
        List<String> permissions = userServiceClient.findPermissionCodes(user.getId());

        // 6. Generate JWT tokens
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

        // 7. Update login info —— 统计字段，回写失败不该把一次已认证的登录判为失败
        try {
            userServiceClient.recordLoginInfo(user.getId());
        } catch (Exception e) {
            log.warn("登录时间回写失败: userId={}, {}", user.getId(), e.getMessage());
        }

        // 8. Cache user token mapping for logout
        redisTemplate.opsForValue().set(
                USER_TOKEN_PREFIX + user.getId(), accessToken, Duration.ofHours(2));

        // Cache LoginUser for the service-layer AuthInterceptor
        cacheHelper.setSeconds(USER_CACHE_PREFIX + user.getUsername(), loginUser, 7200);

        // 9. Record login log
        recordLoginLog(user.getId(), user.getTenantId(), user.getUsername(), true, "Login success");

        log.info("User [{}] logged in successfully", user.getUsername());

        // 10. Build response
        LoginVO loginVO = new LoginVO();
        loginVO.setAccessToken(accessToken);
        loginVO.setRefreshToken(refreshToken);
        loginVO.setUserId(user.getId());
        loginVO.setUsername(user.getUsername());
        loginVO.setRoles(roles);
        loginVO.setPermissions(permissions);
        return loginVO;
    }

    @Override
    public void logout(String userId, String token) {
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        if (token != null && !token.trim().isEmpty()) {
            // Blacklist the token
            redisTemplate.opsForValue().set(
                    TOKEN_BLACKLIST_PREFIX + token, "1", Duration.ofHours(2));
        }
        if (userId != null) {
            redisTemplate.delete(USER_TOKEN_PREFIX + userId);
        }
        LoginUser current = SecurityContextHolder.getLoginUser();
        if (current != null) {
            cacheHelper.delete(USER_CACHE_PREFIX + current.getUsername());
        }
        log.info("User [{}] logged out", userId);
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
        LoginUser loginUser = new LoginUser();
        loginUser.setUserId(user.getId());
        loginUser.setUsername(user.getUsername());
        loginUser.setNickname(user.getNickname());
        loginUser.setTenantId(user.getTenantId() != null ? user.getTenantId().toString() : null);
        loginUser.setDeptId(user.getDeptId());

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

        String accessToken = JwtHelper.createToken(loginUser);
        String refreshToken = generateRefreshToken(user.getId().toString());

        LoginVO loginVO = new LoginVO();
        loginVO.setAccessToken(accessToken);
        loginVO.setRefreshToken(refreshToken);
        loginVO.setUserId(user.getId());
        loginVO.setUsername(user.getUsername());
        loginVO.setRoles(roles);
        loginVO.setPermissions(permissions);
        return loginVO;
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

    private void recordLoginLog(Long userId, Long tenantId, String username, boolean success, String message) {
        SysLoginLog loginLog = new SysLoginLog();
        loginLog.setTenantId(tenantId != null ? tenantId : DEFAULT_TENANT_ID);
        loginLog.setUserId(userId);
        loginLog.setUsername(username);
        loginLog.setLoginType(LOGIN_TYPE_PASSWORD);
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

    private static String truncate(String value, int maxLength) {
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
