package com.dataviz.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.dataviz.admin.entity.SysLicense;
import com.dataviz.admin.mapper.LicenseMapper;
import com.dataviz.admin.service.LicenseService;
import com.dataviz.admin.support.LicenseCodec;
import com.dataviz.admin.vo.LicenseVO;
import com.dataviz.common.core.exception.BizException;
import com.dataviz.common.core.result.ErrorCode;
import com.dataviz.common.security.context.SecurityContextHolder;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 许可证的验签与状态查询。
 * <p>能做到哪、不能做到哪，这里说清楚：</p>
 * <ul>
 *   <li><b>能</b>：挡住"随便贴一串字符就当许可证"和"改动一位数字把 100 用户改成 10 万用户" —— 签名不过就激活不了。</li>
 *   <li><b>不能</b>：挡不住拿到 jar 的人改代码绕过这里。客户端/本地校验的许可证本质上是可绕的，
 *       真要限死容量得由签发方在线核对，那需要另一套东西；这里不假装有。</li>
 * </ul>
 * <p>许可证<b>不</b>参与业务拦截：目前没有任何地方因为 {@code valid=false} 而拒绝请求。
 * 这张页面因此是"状态展示 + 换钥匙"，不是开关。要不要让过期许可证真的停用功能，是产品决定，不在代码里替他做。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LicenseServiceImpl implements LicenseService {

    private final LicenseMapper licenseMapper;

    /** 签发密钥只从外部配置进来，代码里没有默认值；为空时激活直接失败而不是用一把万能钥匙 */
    @Value("${platform.license.secret:}")
    private String licenseSecret;

    @Override
    public LicenseVO current() {
        SysLicense license = activeLicense();
        if (license == null) {
            return LicenseVO.empty("尚未激活许可证");
        }
        return toVO(license, LocalDate.now());
    }

    @Override
    @Transactional
    public LicenseVO activate(String licenseKey) {
        String raw = licenseKey == null ? "" : licenseKey.trim();
        if (raw.isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "许可证密钥不能为空");
        }
        LicenseCodec.Outcome outcome = LicenseCodec.verify(licenseSecret, raw);
        if (!outcome.isValid()) {
            log.warn("许可证激活失败: fingerprint={}, reason={}", LicenseCodec.fingerprint(raw), outcome.error);
            throw new BizException(ErrorCode.BAD_REQUEST, "许可证校验未通过：" + outcome.error);
        }
        LocalDate today = LocalDate.now();
        LicenseCodec.Payload payload = outcome.payload;
        if (payload.expiresAt.isBefore(today)) {
            throw new BizException(ErrorCode.BAD_REQUEST,
                    "许可证已于 " + payload.expiresAt + " 到期，无法激活");
        }

        // 历史保留、只留一张生效：把旧的抬成 0 再插新的。同一次事务里做，避免两张同时 status=1
        licenseMapper.update(null, new LambdaUpdateWrapper<SysLicense>()
                .eq(SysLicense::getStatus, 1)
                .set(SysLicense::getStatus, 0));

        SysLicense license = new SysLicense();
        license.setKeyFingerprint(LicenseCodec.fingerprint(raw));
        license.setEdition(payload.edition);
        license.setIssuedTo(payload.issuedTo);
        license.setIssuedAt(payload.issuedAt);
        license.setExpiresAt(payload.expiresAt);
        license.setMaxUsers(payload.maxUsers);
        license.setMaxNodes(payload.maxNodes);
        license.setStatus(1);
        license.setActivateTime(LocalDateTime.now());
        license.setActivatedBy(SecurityContextHolder.getUsername());
        licenseMapper.insert(license);

        log.info("许可证已激活: fingerprint={}, edition={}, issuedTo={}, expiresAt={}",
                license.getKeyFingerprint(), payload.edition, payload.issuedTo, payload.expiresAt);
        return toVO(license, today);
    }

    private SysLicense activeLicense() {
        List<SysLicense> active = licenseMapper.selectList(new LambdaQueryWrapper<SysLicense>()
                .eq(SysLicense::getStatus, 1)
                .orderByDesc(SysLicense::getId));
        if (active.isEmpty()) {
            return null;
        }
        if (active.size() > 1) {
            // 两张同时生效说明激活事务被打断过；取最新的一张，但把这件事留在日志里而不是静默
            log.warn("存在 {} 张生效中的许可证，按 id 最大的一张展示", active.size());
        }
        return active.get(0);
    }

    private LicenseVO toVO(SysLicense license, LocalDate today) {
        LicenseVO vo = new LicenseVO();
        vo.setActivated(true);
        vo.setType(license.getEdition());
        vo.setIssuedTo(license.getIssuedTo());
        vo.setIssuedAt(LicenseVO.day(license.getIssuedAt()));
        vo.setExpiresAt(LicenseVO.day(license.getExpiresAt()));
        vo.setMaxUsers(license.getMaxUsers());
        vo.setMaxNodes(license.getMaxNodes());
        vo.setFingerprint(license.getKeyFingerprint());
        vo.setActivateTime(LicenseVO.time(license.getActivateTime()));
        vo.setActivatedBy(license.getActivatedBy());
        long daysLeft = license.getExpiresAt() == null ? 0L : ChronoUnit.DAYS.between(today, license.getExpiresAt());
        vo.setDaysLeft(daysLeft);
        if (daysLeft < 0) {
            vo.setValid(false);
            vo.setMessage("许可证已于 " + license.getExpiresAt() + " 到期，已过期 " + Math.abs(daysLeft) + " 天");
        } else if (daysLeft <= 30) {
            vo.setValid(true);
            vo.setMessage("许可证有效，还有 " + daysLeft + " 天到期");
        } else {
            vo.setValid(true);
            vo.setMessage("许可证有效");
        }
        return vo;
    }
}
