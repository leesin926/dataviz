package com.dataviz.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dataviz.auth.client.UserServiceClient;
import com.dataviz.auth.client.dto.AuthUserDTO;
import com.dataviz.auth.vo.SmsSendVO;
import com.dataviz.common.core.exception.BizException;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

/**
 * SmsCodeService 的状态机：AuthService.sendSmsCode 只是薄委托，真正的决策（重发闸门、每日配额、
 * 错误封顶、验证即作废）都在这里。<strong>键的形状就是契约</strong>：
 * 发码与重发闸门按 <em>手机号 + 终端</em> 隔离（sms:code:&lt;terminal&gt;:&lt;phone&gt; / sms:limit:...），
 * 而错误封顶与每日配额<em>只按手机号</em>（sms:fail:&lt;phone&gt; / sms:day:&lt;phone&gt;）——
 * 若把终端也塞进后两者，多开一个端就多份配额，六个端能把每日 10 条变成 60 条。
 * 这些用例把"哪几条键带终端、哪几条不带"钉死。码值走真跑（固定 mockCode "123123"），不 mock 静态。
 */
class SmsCodeServiceTest {

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOps;
    private UserServiceClient userServiceClient;
    private SmsCodeService smsCodeService;

    private static final String PHONE = "13800000000";
    private static final String TERMINAL = "admin";

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        userServiceClient = mock(UserServiceClient.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        smsCodeService = new SmsCodeService(redisTemplate, userServiceClient, "123123");
    }

    private AuthUserDTO activeUser() {
        AuthUserDTO u = new AuthUserDTO();
        u.setId(1L);
        u.setTenantId(5L);
        u.setUsername("alice");
        u.setStatus(1);
        return u;
    }

    // ==================== send ====================

    @Test
    @DisplayName("send：手机号未绑定账号直接拒绝，不落任何键")
    void should_throwWhenNotBound_when_send() {
        when(userServiceClient.findByPhone(PHONE)).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> smsCodeService.send(PHONE, TERMINAL));
        assertTrue(ex.getMessage().contains("该手机号未绑定任何账号"));
        verify(valueOps, never()).setIfAbsent(any(), any(), any(Duration.class));
    }

    @Test
    @DisplayName("send：抢不到重发闸门时拒绝，并回报剩余秒数；闸门键带终端")
    void should_holdResendGate_when_sendTooFrequent() {
        when(userServiceClient.findByPhone(PHONE)).thenReturn(activeUser());
        String limitKey = "sms:limit:" + TERMINAL + ":" + PHONE;
        when(valueOps.setIfAbsent(eq(limitKey), eq("1"), eq(Duration.ofSeconds(60)))).thenReturn(false);
        when(redisTemplate.getExpire(limitKey)).thenReturn(30L);

        BizException ex = assertThrows(BizException.class, () -> smsCodeService.send(PHONE, TERMINAL));
        assertTrue(ex.getMessage().contains("验证码发送过于频繁，请 30 秒后再试"), ex.getMessage());
        // 闸门没过就不该继续占配额、落码
        verify(valueOps, never()).increment(any());
        verify(valueOps, never()).set(eq("sms:code:" + TERMINAL + ":" + PHONE), any(), any(Duration.class));
    }

    @Test
    @DisplayName("send：当日配额用尽拒绝；配额键只按手机号、不带终端")
    void should_rejectDailyQuota_when_exceedLimit() {
        when(userServiceClient.findByPhone(PHONE)).thenReturn(activeUser());
        String limitKey = "sms:limit:" + TERMINAL + ":" + PHONE;
        when(valueOps.setIfAbsent(eq(limitKey), eq("1"), eq(Duration.ofSeconds(60)))).thenReturn(true);
        when(valueOps.increment(any())).thenReturn(11L); // 当天第 11 条 > 上限 10

        BizException ex = assertThrows(BizException.class, () -> smsCodeService.send(PHONE, TERMINAL));
        assertTrue(ex.getMessage().contains("今日验证码发送次数已达上限"), ex.getMessage());
        // 真正钉住生产代码用的配额键：只按手机号，不含终端段
        ArgumentCaptor<String> dayKeyCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOps).increment(dayKeyCaptor.capture());
        assertEquals("sms:day:" + PHONE, dayKeyCaptor.getValue());
        assertTrue(!dayKeyCaptor.getValue().contains(TERMINAL), "每日配额键必须只按手机号: " + dayKeyCaptor.getValue());
        verify(valueOps, never()).set(eq("sms:code:" + TERMINAL + ":" + PHONE), any(), any(Duration.class));
    }

    @Test
    @DisplayName("send 成功：码键带终端、值取固定 mockCode，封顶键按手机号清除，配额首次落码时设到期")
    void should_writeTerminalScopedCode_when_sendWithinLimits() {
        when(userServiceClient.findByPhone(PHONE)).thenReturn(activeUser());
        when(valueOps.setIfAbsent(eq("sms:limit:" + TERMINAL + ":" + PHONE), eq("1"), eq(Duration.ofSeconds(60))))
                .thenReturn(true);
        String dayKey = "sms:day:" + PHONE;
        when(valueOps.increment(dayKey)).thenReturn(1L); // 当天第一条 → 设到期
        String codeKey = "sms:code:" + TERMINAL + ":" + PHONE;

        SmsSendVO vo = smsCodeService.send(PHONE, TERMINAL);

        assertEquals(300, vo.getExpireSeconds());
        assertEquals(60, vo.getResendAfterSeconds());
        // 码：带终端，值=mockCode，TTL 5 分钟
        verify(valueOps).set(eq(codeKey), eq("123123"), eq(Duration.ofMinutes(5)));
        // 配额：只按手机号，首条设到期（TTL 到明天）
        verify(redisTemplate).expire(eq(dayKey), any(Duration.class));
        // 新发一轮清空错误封顶：fail 键只按手机号
        verify(redisTemplate).delete("sms:fail:" + PHONE);
    }

    // ==================== verify ====================

    @Test
    @DisplayName("verify：取不到码（含换终端读不到）判过期，要求重新获取")
    void should_reportExpired_when_verifyCodeMissing() {
        String codeKey = "sms:code:" + TERMINAL + ":" + PHONE;
        when(valueOps.get(codeKey)).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> smsCodeService.verify(PHONE, TERMINAL, "123123"));
        assertTrue(ex.getMessage().contains("验证码已过期，请重新获取"), ex.getMessage());
    }

    @Test
    @DisplayName("verify：码不对但未超尝试封顶时只报验证码错误，不作废本轮码")
    void should_reportWrongCode_when_verifyMismatchUnderCeiling() {
        String codeKey = "sms:code:" + TERMINAL + ":" + PHONE;
        String failKey = "sms:fail:" + PHONE;
        when(valueOps.get(codeKey)).thenReturn("123123");
        when(valueOps.increment(failKey)).thenReturn(2L); // 第 2 次错误，未过 5 封顶

        BizException ex = assertThrows(BizException.class, () -> smsCodeService.verify(PHONE, TERMINAL, "000000"));
        assertTrue(ex.getMessage().contains("验证码错误"), ex.getMessage());
        // 未过封顶：本轮码留着（只有超封顶才删 codeKey）
        verify(redisTemplate, never()).delete(codeKey);
    }

    @Test
    @DisplayName("verify：码对则一次性消费——删码键并删封顶键")
    void should_consumeCodeOnSuccess_when_verifyOk() {
        String codeKey = "sms:code:" + TERMINAL + ":" + PHONE;
        String failKey = "sms:fail:" + PHONE;
        when(valueOps.get(codeKey)).thenReturn("123123");
        when(valueOps.increment(failKey)).thenReturn(1L);

        smsCodeService.verify(PHONE, TERMINAL, "123123");

        verify(redisTemplate).delete(codeKey);
        verify(redisTemplate).delete(failKey);
    }
}
