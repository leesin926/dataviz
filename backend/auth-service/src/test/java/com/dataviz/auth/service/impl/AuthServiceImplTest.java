package com.dataviz.auth.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dataviz.auth.client.UserServiceClient;
import com.dataviz.auth.client.dto.AuthUserDTO;
import com.dataviz.auth.dto.LoginDTO;
import com.dataviz.auth.dto.SmsLoginDTO;
import com.dataviz.auth.entity.SysLoginLog;
import com.dataviz.auth.mapper.LoginLogMapper;
import com.dataviz.auth.service.SmsCodeService;
import com.dataviz.auth.vo.CaptchaVO;
import com.dataviz.auth.vo.LoginVO;
import com.dataviz.common.core.exception.BizException;
import com.dataviz.common.redis.util.CacheHelper;
import com.dataviz.common.security.model.LoginUser;
import com.dataviz.common.security.util.JwtHelper;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * AuthServiceImpl 的决策路径。用 mock 承接 Redis/客户端，但 <strong>静态工具真跑</strong>：
 * JwtHelper 用硬编码静态密钥，所以成功登录直接生成真 JWT 并断言 claims 往返（不 mock 静态）；
 * LoginSessionCache 的键（login:user:*）也是真拼出来再对 mock CacheHelper 断言。
 * 重点：每条失败分支都必须落一条登录日志（含 userId 缺省、匿名失败回落默认租户），
 * 以及验证码答案只进 Redis、绝不进返回体。
 */
class AuthServiceImplTest {

    private UserServiceClient userServiceClient;
    private LoginLogMapper loginLogMapper;
    private PasswordEncoder passwordEncoder;
    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOps;
    private CacheHelper cacheHelper;
    private SmsCodeService smsCodeService;
    private AuthServiceImpl authService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        userServiceClient = mock(UserServiceClient.class);
        loginLogMapper = mock(LoginLogMapper.class);
        passwordEncoder = mock(PasswordEncoder.class);
        redisTemplate = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        cacheHelper = mock(CacheHelper.class);
        smsCodeService = mock(SmsCodeService.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        authService = new AuthServiceImpl(userServiceClient, loginLogMapper, passwordEncoder,
                redisTemplate, cacheHelper, smsCodeService);
    }

    private LoginDTO loginDto() {
        LoginDTO dto = new LoginDTO();
        dto.setUsername("alice");
        dto.setPassword("plain");
        dto.setCaptchaKey("K1");
        dto.setCaptchaCode("30");
        return dto;
    }

    private AuthUserDTO user(Long id, Long tenantId, String username, String pw, Integer status) {
        AuthUserDTO u = new AuthUserDTO();
        u.setId(id);
        u.setTenantId(tenantId);
        u.setUsername(username);
        u.setNickname("Alice");
        u.setDeptId(8L);
        u.setPassword(pw);
        u.setStatus(status);
        return u;
    }

    /** 让 verifyCaptcha 通过：Redis 存的 "captcha:K1" 等于用户输入的 "30"。 */
    private void passCaptcha() {
        when(valueOps.get("captcha:K1")).thenReturn("30");
    }

    // ==================== login — 验证码闸门 ====================

    @Test
    @DisplayName("login：缺少验证码 key/code 当场拒绝，根本不查用户")
    void should_requireCaptcha_when_captchaKeyOrCodeMissing() {
        LoginDTO dto = loginDto();
        dto.setCaptchaKey(null);

        BizException ex = assertThrows(BizException.class, () -> authService.login(dto));
        assertTrue(ex.getMessage().contains("Captcha key and code are required"));
        verify(userServiceClient, never()).findByUsername(anyString());
    }

    @Test
    @DisplayName("login：验证码已过期（Redis 取不到）拒绝")
    void should_rejectExpiredCaptcha_when_storedAnswerMissing() {
        when(valueOps.get("captcha:K1")).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> authService.login(loginDto()));
        assertTrue(ex.getMessage().contains("Captcha has expired"));
        verify(userServiceClient, never()).findByUsername(anyString());
    }

    @Test
    @DisplayName("login：验证码不匹配拒绝，并区分大小写地按 trim 比较")
    void should_rejectWrongCaptcha_when_codeMismatch() {
        when(valueOps.get("captcha:K1")).thenReturn("30");
        LoginDTO dto = loginDto();
        dto.setCaptchaCode(" 31 ");

        BizException ex = assertThrows(BizException.class, () -> authService.login(dto));
        assertTrue(ex.getMessage().contains("Captcha code is incorrect"));
    }

    // ==================== login — 用户/口令失败分支（每条都落库） ====================

    @Test
    @DisplayName("login：用户不存在——报统一口令错误并落一条匿名失败日志（租户回落默认 1）")
    void should_recordFailedLogin_when_userNotFound() {
        passCaptcha();
        when(userServiceClient.findByUsername("alice")).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> authService.login(loginDto()));
        assertEquals("Invalid username or password", ex.getMessage());

        SysLoginLog log = captureInsertedLog();
        assertNull(log.getUserId(), "查不到人时不应猜 userId");
        assertEquals("alice", log.getUsername());
        assertEquals(1, log.getLoginType());
        assertFalse(log.getStatus());
        assertEquals("User not found", log.getMessage());
        assertEquals(1L, log.getTenantId(), "匿名失败回落默认租户 1");
    }

    @Test
    @DisplayName("login：账号停用——报停用并落失败日志（带 userId/租户）")
    void should_recordFailedLogin_when_accountDisabled() {
        passCaptcha();
        when(userServiceClient.findByUsername("alice")).thenReturn(user(1L, 5L, "alice", "$2a$h", 0));

        BizException ex = assertThrows(BizException.class, () -> authService.login(loginDto()));
        assertEquals("Account is disabled", ex.getMessage());

        SysLoginLog log = captureInsertedLog();
        assertEquals(1L, log.getUserId());
        assertEquals(5L, log.getTenantId());
        assertEquals("Account disabled", log.getMessage());
        assertFalse(log.getStatus());
    }

    @Test
    @DisplayName("login：口令为空哈希——不靠 matches 的巧合，当场拒绝并记 Password not set")
    void should_recordFailedLogin_when_passwordBlank() {
        passCaptcha();
        when(userServiceClient.findByUsername("alice")).thenReturn(user(1L, 5L, "alice", "  ", 1));

        BizException ex = assertThrows(BizException.class, () -> authService.login(loginDto()));
        assertEquals("Invalid username or password", ex.getMessage());
        verify(passwordEncoder, never()).matches(anyString(), anyString());

        assertEquals("Password not set", captureInsertedLog().getMessage());
    }

    @Test
    @DisplayName("login：口令不匹配——统一口径拒绝并记 Wrong password")
    void should_recordFailedLogin_when_passwordMismatch() {
        passCaptcha();
        when(userServiceClient.findByUsername("alice")).thenReturn(user(1L, 5L, "alice", "$2a$h", 1));
        when(passwordEncoder.matches("plain", "$2a$h")).thenReturn(false);

        BizException ex = assertThrows(BizException.class, () -> authService.login(loginDto()));
        assertEquals("Invalid username or password", ex.getMessage());
        assertEquals("Wrong password", captureInsertedLog().getMessage());
    }

    @Test
    @DisplayName("login 成功：发会话——写 login:user 快照 + user:token 映射 + 成功日志，真 JWT claims 往返")
    void should_issueSessionAndRecordSuccess_when_passwordLoginOk() {
        passCaptcha();
        when(userServiceClient.findByUsername("alice")).thenReturn(user(1L, 5L, "alice", "$2a$h", 1));
        when(passwordEncoder.matches("plain", "$2a$h")).thenReturn(true);
        when(userServiceClient.findRoleCodes(1L)).thenReturn(Collections.singletonList("admin"));
        when(userServiceClient.findPermissionCodes(1L)).thenReturn(Collections.singletonList("user:read"));

        LoginVO vo = authService.login(loginDto());

        // LoginVO 内容
        assertEquals(1L, vo.getUserId());
        assertEquals("alice", vo.getUsername());
        assertEquals(Arrays.asList("admin"), vo.getRoles());
        assertEquals(Arrays.asList("user:read"), vo.getPermissions());
        // 真跑 JwtHelper：token 能解回原用户（不 mock 静态）
        assertEquals("alice", JwtHelper.getUsername(vo.getAccessToken()));
        assertEquals(1L, JwtHelper.getUserId(vo.getAccessToken()));
        assertEquals("5", JwtHelper.getTenantId(vo.getAccessToken()));
        // 会话快照键（LoginSessionCache.key 真拼）
        verify(cacheHelper).setSeconds(eq("login:user:alice"), any(LoginUser.class), eq(7200L));
        // user:token 映射用于登出
        verify(valueOps).set(eq("user:token:1"), eq(vo.getAccessToken()), eq(Duration.ofHours(2)));
        // refresh token 落 Redis，键 refresh:<rt>，值 userId，TTL 7 天
        assertNotNull(vo.getRefreshToken());
        verify(valueOps).set(eq("refresh:" + vo.getRefreshToken()), eq("1"), eq(Duration.ofDays(7)));
        // 成功日志
        SysLoginLog log = captureInsertedLog();
        assertTrue(log.getStatus());
        assertEquals("Login success", log.getMessage());
        assertEquals(1, log.getLoginType());
    }

    // ==================== smsLogin — 验证码分支矩阵 ====================

    @Test
    @DisplayName("smsLogin：手机号未绑定任何账号——报统一文案并落 Phone not bound 日志")
    void should_throwWhenPhoneNotBound_when_smsLogin() {
        when(userServiceClient.findByPhone("13800000000")).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> authService.smsLogin(smsDto()));
        assertEquals("该手机号未绑定任何账号", ex.getMessage());
        SysLoginLog log = captureInsertedLog();
        assertEquals("Phone not bound", log.getMessage());
        assertEquals(2, log.getLoginType());
    }

    @Test
    @DisplayName("smsLogin：账号停用拒绝，且不进入验证码校验")
    void should_throwWhenDisabled_when_smsLogin() {
        when(userServiceClient.findByPhone("13800000000")).thenReturn(user(2L, 5L, "bob", "$2a$h", 0));

        BizException ex = assertThrows(BizException.class, () -> authService.smsLogin(smsDto()));
        assertEquals("Account is disabled", ex.getMessage());
        verify(smsCodeService, never()).verify(anyString(), anyString(), anyString());
        assertEquals(2, captureInsertedLog().getLoginType());
    }

    @Test
    @DisplayName("smsLogin：验证码校验失败——按 verify 抛出的原文落日志并原样上抛")
    void should_mapVerifyFailureToLoginLog_when_smsLoginWrongCode() {
        when(userServiceClient.findByPhone("13800000000")).thenReturn(user(2L, 5L, "bob", "$2a$h", 1));
        org.mockito.Mockito.doThrow(new BizException("验证码错误"))
                .when(smsCodeService).verify("13800000000", "admin", "000000");

        BizException ex = assertThrows(BizException.class, () -> authService.smsLogin(smsDto()));
        assertEquals("验证码错误", ex.getMessage());
        assertEquals("验证码错误", captureInsertedLog().getMessage());
    }

    @Test
    @DisplayName("smsLogin 成功：终端原样透传给 verify（换端应失败），并发短信类型会话")
    void should_issueSessionForwardingTerminal_when_smsLoginOk() {
        when(userServiceClient.findByPhone("13800000000")).thenReturn(user(2L, 5L, "bob", "$2a$h", 1));
        when(userServiceClient.findRoleCodes(2L)).thenReturn(Collections.<String>emptyList());
        when(userServiceClient.findPermissionCodes(2L)).thenReturn(Collections.<String>emptyList());

        LoginVO vo = authService.smsLogin(smsDto());

        // 终端维度：verify 收到 (phone, terminal, code)
        verify(smsCodeService).verify("13800000000", "admin", "000000");
        assertEquals("bob", vo.getUsername());
        assertEquals("bob", JwtHelper.getUsername(vo.getAccessToken()));
        verify(cacheHelper).setSeconds(eq("login:user:bob"), any(LoginUser.class), eq(7200L));
        assertEquals(2, captureInsertedLog().getLoginType(), "短信登录日志 loginType=2");
    }

    // ==================== logout / revoke ====================

    @Test
    @DisplayName("logout：有主体时黑 token、删 user:token 并驱逐 login:user 快照")
    void should_blacklistAndEvict_when_logoutWithSubject() {
        LoginUser currentUser = LoginUser.builder().userId(1L).username("alice").build();

        authService.logout(currentUser, "Bearer abc.def.ghi");

        verify(valueOps).set(eq("token:blacklist:abc.def.ghi"), eq("1"), eq(Duration.ofHours(2)));
        verify(redisTemplate).delete("user:token:1");
        // LoginSessionCache.evict 真跑 → 对 mock CacheHelper 断言键
        verify(cacheHelper).delete("login:user:alice");
    }

    @Test
    @DisplayName("logout：缺主体时只黑 token，不猜 userId、不清会话（堵住从请求头反推 userId）")
    void should_skipSessionCleanup_when_logoutWithoutSubject() {
        authService.logout(null, "Bearer xyz");

        verify(valueOps).set(eq("token:blacklist:xyz"), eq("1"), eq(Duration.ofHours(2)));
        verify(redisTemplate, never()).delete(anyString());
        verify(cacheHelper, never()).delete(anyString());
    }

    @Test
    @DisplayName("revokeUserTokens：按 user:token:<userId> 精确删除")
    void should_deleteUserTokenKey_when_revokeUserTokens() {
        authService.revokeUserTokens("42");

        verify(redisTemplate).delete("user:token:42");
    }

    // ==================== generateCaptcha ====================

    @Test
    @DisplayName("generateCaptcha：返回 key+提示图，答案只写进 Redis 不外泄")
    void should_returnKeyAndPromptWithoutLeakingAnswer_when_generateCaptcha() {
        CaptchaVO vo = authService.generateCaptcha();

        assertNotNull(vo.getCaptchaKey());
        assertEquals(32, vo.getCaptchaKey().length(), "去横线的 UUID");
        assertFalse(vo.getCaptchaKey().contains("-"));
        assertEquals(300, vo.getExpireSeconds());
        assertTrue(vo.getCaptchaImage().startsWith("data:image/png;base64,"));
        // 答案存 Redis：captcha:<key> → 纯数字（两加数之和）
        ArgumentCaptor<String> answerCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOps).set(eq("captcha:" + vo.getCaptchaKey()), answerCaptor.capture(), eq(Duration.ofMinutes(5)));
        assertTrue(answerCaptor.getValue().matches("\\d+"), "Redis 里应是数字答案: " + answerCaptor.getValue());
        // 返回体只有 key 与图片，绝不含答案字段（CaptchaVO 无 answer 字段）—— 图片渲染的是提示 "a + b = ?"
        String rendered = vo.getCaptchaKey() + vo.getCaptchaImage();
        assertFalse(rendered.contains(answerCaptor.getValue()), "答案不得出现在返回体");
    }

    private SmsLoginDTO smsDto() {
        SmsLoginDTO dto = new SmsLoginDTO();
        dto.setPhone("13800000000");
        dto.setTerminal("admin");
        dto.setCode("000000");
        return dto;
    }

    private SysLoginLog captureInsertedLog() {
        ArgumentCaptor<SysLoginLog> captor = ArgumentCaptor.forClass(SysLoginLog.class);
        verify(loginLogMapper).insert(captor.capture());
        return captor.getValue();
    }
}
