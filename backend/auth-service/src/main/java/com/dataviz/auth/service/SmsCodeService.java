package com.dataviz.auth.service;

import com.dataviz.auth.client.UserServiceClient;
import com.dataviz.auth.client.dto.AuthUserDTO;
import com.dataviz.auth.vo.SmsSendVO;
import com.dataviz.common.core.exception.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * 短信验证码的下发与校验。<strong>这套状态机是真的，只有"把码送出去"那一步是假的。</strong>
 * <p>
 * 项目没有接入短信渠道，所以码值固定取配置 {@code auth.sms.mock-code}（默认 {@code 123123}）。
 * 除码值之外，这里跑的状态机与生产形态一致：有效期、重发闸门、每日上限、错误尝试封顶、验证通过即作废。
 * 接真渠道时只改两处——码值换成随机、加一次真实的发送调用——状态机一行不用动。
 * </p>
 * <p>
 * ⚠️ 固定码等于"知道 123123 的人可以登成任意已绑定手机号"，这是演示环境的取舍，
 * 上真环境必须先换掉 {@code auth.sms.mock-code}；同时"手机号未绑定"这条明确报错也在为枚举账号开门，
 * 真渠道接入后应改成统一成功响应。两处都记在进度表的风险里。
 * </p>
 * <p>
 * <strong>键的粒度按"哪一面"分开，不是整套统一加终端。</strong>
 * 发码（{@code sms:code}）和重发闸门（{@code sms:limit}）按 <em>手机号 + 终端</em> 隔离——
 * 同一个人先登设计端再登管理端，是两个互不相干的登录动作，让它们互相撞 60 秒闸门、
 * 以及让 A 端用掉的码把 B 端判成"已过期"，只是把一次正常操作误诊成攻击。
 * 而错误尝试封顶（{@code sms:fail}）和每日发送上限（{@code sms:day}）<em>只按手机号</em>：
 * 这两条保护的是账号本身，加进终端就等于给攻击者"多开一个端多份配额"，
 * 六个端能把每日 10 条变成 60 条、把 5 次猜码变成 30 次。
 * </p>
 */
@Slf4j
@Service
public class SmsCodeService {

    /** 这两条按 <em>手机号 + 终端</em> 存：发码与重发闸门是各端独立的登录动作 */
    private static final String CODE_PREFIX = "sms:code:";
    private static final String LIMIT_PREFIX = "sms:limit:";
    /** 这两条只按 <em>手机号</em> 存：封顶与配额保护的是账号，不随终端稀释 */
    private static final String FAIL_PREFIX = "sms:fail:";
    private static final String DAY_PREFIX = "sms:day:";

    private static final Duration CODE_TTL = Duration.ofMinutes(5);
    private static final Duration RESEND_INTERVAL = Duration.ofSeconds(60);
    private static final int MAX_WRONG_ATTEMPTS = 5;
    private static final int DAILY_SEND_LIMIT = 10;

    private final StringRedisTemplate redisTemplate;
    private final UserServiceClient userServiceClient;
    private final String mockCode;

    public SmsCodeService(StringRedisTemplate redisTemplate,
                          UserServiceClient userServiceClient,
                          @Value("${auth.sms.mock-code:123123}") String mockCode) {
        this.redisTemplate = redisTemplate;
        this.userServiceClient = userServiceClient;
        this.mockCode = mockCode;
    }

    /**
     * 下发验证码。先定位账号再落码：让"这个号能不能登"在发送这一步就有反馈，
     * 而不是等用户输完六位数字才告诉他手机号没绑过。
     *
     * @param terminal 登录发起端，已由 DTO 按白名单校验，可直接做键的一段
     */
    public SmsSendVO send(String phone, String terminal) {
        AuthUserDTO user = userServiceClient.findByPhone(phone);
        if (user == null) {
            throw new BizException("该手机号未绑定任何账号");
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BizException("账号已停用，请联系管理员");
        }

        String limitKey = terminalKey(LIMIT_PREFIX, terminal, phone);
        // SETNX + TTL 就是重发闸门：抢不到说明上一发还在窗口里，不需要额外读一次再判断
        if (!Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(limitKey, "1", RESEND_INTERVAL))) {
            throw new BizException("验证码发送过于频繁，请 " + remainingSeconds(limitKey) + " 秒后再试");
        }

        String dayKey = DAY_PREFIX + phone;
        Long sentToday = redisTemplate.opsForValue().increment(dayKey);
        if (sentToday != null && sentToday == 1L) {
            redisTemplate.expire(dayKey, Duration.ofSeconds(secondsUntilTomorrow()));
        }
        if (sentToday != null && sentToday > DAILY_SEND_LIMIT) {
            throw new BizException("今日验证码发送次数已达上限，请明天再试");
        }

        redisTemplate.opsForValue().set(terminalKey(CODE_PREFIX, terminal, phone), mockCode, CODE_TTL);
        redisTemplate.delete(FAIL_PREFIX + phone);
        log.info("短信验证码已下发: phone={}, terminal={}, 有效期 {}s（未接入短信渠道，码值取 auth.sms.mock-code）", mask(phone), terminal, CODE_TTL.getSeconds());
        return new SmsSendVO((int) CODE_TTL.getSeconds(), (int) RESEND_INTERVAL.getSeconds());
    }

    /**
     * 校验验证码，<strong>不抛异常即视为通过</strong>；通过后本轮码立即作废（防重放）。
     * <p>
     * 终端不参与"猜了几次"和"今天发了几条"的计数，只决定取哪一份码——换端来的码读不到，
     * 于是得到"验证码已过期"，这正是要的语义。
     * </p>
     */
    public void verify(String phone, String terminal, String code) {
        String codeKey = terminalKey(CODE_PREFIX, terminal, phone);
        String stored = redisTemplate.opsForValue().get(codeKey);
        if (stored == null) {
            throw new BizException("验证码已过期，请重新获取");
        }
        String failKey = FAIL_PREFIX + phone;
        Long wrongCount = redisTemplate.opsForValue().increment(failKey);
        if (wrongCount != null && wrongCount == 1L) {
            redisTemplate.expire(failKey, CODE_TTL);
        }
        if (wrongCount != null && wrongCount > MAX_WRONG_ATTEMPTS) {
            // 封顶之后作废本轮码：想继续试只能重新发送，而那里有 60 秒闸门 + 每日 10 条上限
            redisTemplate.delete(codeKey);
            throw new BizException("验证码错误次数过多，请重新获取");
        }
        if (!stored.equals(code.trim())) {
            throw new BizException("验证码错误");
        }
        redisTemplate.delete(codeKey);
        redisTemplate.delete(failKey);
    }

    /** 体验面的键带终端段；terminal 已过白名单，不会拼出调用方自定的命名空间。 */
    private static String terminalKey(String prefix, String terminal, String phone) {
        return prefix + terminal + ":" + phone;
    }

    private long remainingSeconds(String key) {
        Long expire = redisTemplate.getExpire(key);
        return expire == null || expire < 0 ? RESEND_INTERVAL.getSeconds() : expire;
    }

    private long secondsUntilTomorrow() {
        LocalDateTime now = LocalDateTime.now();
        return now.until(LocalDate.now().plusDays(1).atStartOfDay(), ChronoUnit.SECONDS);
    }

    private static String mask(String phone) {
        if (phone == null || phone.length() < 7) {
            return "***";
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }
}
