package com.dataviz.openapi.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.dataviz.common.core.exception.BizException;
import com.dataviz.openapi.entity.OpenApiClient;
import com.dataviz.openapi.mapper.OpenApiClientMapper;
import com.dataviz.openapi.test.MybatisPlusTableInfoHelper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 口令鉴权那条老路（{@code /api/openapi/proxy}）。
 *
 * <p>它和回调验签链是两套判据：这里比的是明文 appSecret、限流按 appKey 计数、
 * 白名单也是逗号清单。所以用例盯的是"六条拒绝口径各归各位"，
 * 以及两处和库里真实列型有关的坑：{@code rate_limit} 可空、{@code allowed_ips} 带空格。</p>
 */
@ExtendWith(MockitoExtension.class)
class ApiAuthServiceImplTest {

    @Mock
    private OpenApiClientMapper clientMapper;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private ApiAuthServiceImpl apiAuthService;

    @BeforeAll
    static void initTableInfo() {
        MybatisPlusTableInfoHelper.initFor(OpenApiClient.class);
    }

    @Test
    @DisplayName("appKey 查不到 → Invalid app key，不碰 Redis")
    void should_reject_when_appKeyUnknown() {
        when(clientMapper.selectOne(anyWrapper())).thenReturn(null);

        BizException e = assertThrows(BizException.class,
                () -> apiAuthService.authenticate("missing", "s", "10.0.0.1"));
        assertEquals("Invalid app key", e.getMessage());
        verify(redisTemplate, never()).opsForValue();
    }

    @Test
    @DisplayName("口令不符先于状态判定：停用账号也不会泄露「它只是被停用」")
    void should_reject_when_secretMismatch() {
        when(clientMapper.selectOne(anyWrapper())).thenReturn(client("ACTIVE", 60, null, null));

        BizException e = assertThrows(BizException.class,
                () -> apiAuthService.authenticate("ak", "wrong-secret", "10.0.0.1"));
        assertEquals("Invalid app secret", e.getMessage());
    }

    @Test
    @DisplayName("非 ACTIVE 状态 → 停用")
    void should_reject_when_clientDisabled() {
        when(clientMapper.selectOne(anyWrapper())).thenReturn(client("DISABLED", 60, null, null));

        BizException e = assertThrows(BizException.class,
                () -> apiAuthService.authenticate("ak", "sk", "10.0.0.1"));
        assertEquals("API client is disabled", e.getMessage());
    }

    @Test
    @DisplayName("过期时间已过 → 拒；expire_time 为空视为不过期")
    void should_reject_when_expired() {
        OpenApiClient expired = client("ACTIVE", 60, null, LocalDateTime.now().minusSeconds(1));
        when(clientMapper.selectOne(anyWrapper())).thenReturn(expired);

        BizException e = assertThrows(BizException.class,
                () -> apiAuthService.authenticate("ak", "sk", "10.0.0.1"));
        assertEquals("API client has expired", e.getMessage());
    }

    @Test
    @DisplayName("来源 IP 不在清单 → 拒，且不消耗配额")
    void should_reject_when_ipNotAllowed() {
        when(clientMapper.selectOne(anyWrapper())).thenReturn(client("ACTIVE", 60, "10.0.0.15", null));

        BizException e = assertThrows(BizException.class,
                () -> apiAuthService.authenticate("ak", "sk", "10.0.0.1"));
        assertTrue(e.getMessage().contains("10.0.0.1"));
        verify(redisTemplate, never()).opsForValue();
    }

    @Test
    @DisplayName("配额用尽 → Rate limit exceeded")
    void should_reject_when_rateExceeded() {
        when(clientMapper.selectOne(anyWrapper())).thenReturn(client("ACTIVE", 2, null, null));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("openapi:rate:ak")).thenReturn("2");

        BizException e = assertThrows(BizException.class,
                () -> apiAuthService.authenticate("ak", "sk", "10.0.0.1"));
        assertEquals("Rate limit exceeded", e.getMessage());
        verify(valueOperations, never()).increment(anyString());
    }

    @Test
    @DisplayName("六项判据全过 → 返回客户端本身")
    void should_returnClient_when_allChecksPass() {
        OpenApiClient ok = client("ACTIVE", 60, "10.0.0.1", LocalDateTime.now().plusDays(1));
        when(clientMapper.selectOne(anyWrapper())).thenReturn(ok);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("openapi:rate:ak")).thenReturn("0");
        when(valueOperations.increment("openapi:rate:ak")).thenReturn(1L);

        OpenApiClient result = apiAuthService.authenticate("ak", "sk", "10.0.0.1");

        assertNotNull(result);
        assertEquals("ak", result.getAppKey());
        // 首次计数才铺 TTL，后续请求不能把窗口一直往后推
        verify(redisTemplate).expire("openapi:rate:ak", Duration.ofMinutes(1));
    }

    @Test
    @DisplayName("rate_limit 为 NULL（库里可空）→ 按 60 判，不自动拆箱成 500")
    void should_fallBackToDefaultQuota_when_rateLimitNull() {
        OpenApiClient nullLimit = client("ACTIVE", null, null, null);
        when(clientMapper.selectOne(anyWrapper())).thenReturn(nullLimit);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("openapi:rate:ak")).thenReturn("59");
        when(valueOperations.increment("openapi:rate:ak")).thenReturn(60L);

        assertNotNull(apiAuthService.authenticate("ak", "sk", "10.0.0.1"));
    }

    @Test
    @DisplayName("白名单逐项 trim：逗号后带空格也要能命中第二项")
    void should_matchWithSpaces_when_allowedIpsHaveBlanks() {
        assertTrue(apiAuthService.checkIpWhitelist(
                client("ACTIVE", 60, "10.0.0.1, 10.0.0.2", null), "10.0.0.2"));
    }

    @Test
    @DisplayName("白名单是精确清单，不是子串：10.0.0.15 不覆盖 10.0.0.1")
    void should_notMatchAsSubstring_when_ipIsPrefixOfEntry() {
        assertFalse(apiAuthService.checkIpWhitelist(
                client("ACTIVE", 60, "10.0.0.15", null), "10.0.0.1"));
        assertFalse(apiAuthService.checkIpWhitelist(
                client("ACTIVE", 60, "10.0.0.15", null), "10.0.0"));
    }

    @Test
    @DisplayName("空白清单=不限来源；含 * 的清单放行任意来源")
    void should_allowAll_when_whitelistEmptyOrWildcard() {
        assertTrue(apiAuthService.checkIpWhitelist(client("ACTIVE", 60, null, null), "6.6.6.6"));
        assertTrue(apiAuthService.checkIpWhitelist(client("ACTIVE", 60, "   ", null), "6.6.6.6"));
        assertTrue(apiAuthService.checkIpWhitelist(client("ACTIVE", 60, "10.0.0.1, *", null), "6.6.6.6"));
        assertFalse(apiAuthService.checkIpWhitelist(client("ACTIVE", 60, "10.0.0.1", null), "6.6.6.6"));
    }

    @Test
    @DisplayName("非首个请求不再设 TTL，窗口按第一次计数对齐")
    void should_notExtendTtl_when_countAlreadyStarted() {
        OpenApiClient c = client("ACTIVE", 60, null, null);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("openapi:rate:ak")).thenReturn("5");
        when(valueOperations.increment("openapi:rate:ak")).thenReturn(6L);

        assertTrue(apiAuthService.checkRateLimit(c));
        verify(redisTemplate, never()).expire(eq("openapi:rate:ak"), eq(Duration.ofMinutes(1)));
    }

    private static OpenApiClient client(String status, Integer rateLimit, String allowedIps,
                                        LocalDateTime expireTime) {
        return OpenApiClient.builder()
                .id(1L).appKey("ak").appSecret("sk").status(status)
                .rateLimit(rateLimit).allowedIps(allowedIps).expireTime(expireTime).build();
    }

    @SuppressWarnings("unchecked")
    private static Wrapper<OpenApiClient> anyWrapper() {
        return org.mockito.ArgumentMatchers.<Wrapper<OpenApiClient>>any();
    }
}
