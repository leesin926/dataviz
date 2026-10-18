package com.dataviz.admin.vo;

import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 许可证状态。字段名沿用前端已有的 {@code type/issuedTo/...} 口径，避免为一个页面改两套。
 * <p>日期一律给成字符串：这张页面的职责是"看一眼许可证"，让前端去猜后端有没有关掉
 * {@code WRITE_DATES_AS_TIMESTAMPS}（关掉是 {@code "2026-10-16"}，没关是 {@code [2026,10,16]}）
 * 不值得 —— 渲染出来的就是一行日期文本。</p>
 */
@Data
public class LicenseVO {

    /** 库里是否有一张已激活的许可证 */
    private boolean activated;

    /** 许可证是否可用（已激活 且 未过期） */
    private boolean valid;

    /** 版本，Enterprise / Standard ... */
    private String type;

    private String issuedTo;

    /** yyyy-MM-dd */
    private String issuedAt;

    /** yyyy-MM-dd */
    private String expiresAt;

    private Integer maxUsers;

    private Integer maxNodes;

    /** 距离到期还有多少天；已过期为负数，未激活为 null */
    private Long daysLeft;

    /** 给管理员看的一句话结论：为什么可用 / 为什么不可用 */
    private String message;

    /** 密钥指纹（不是密钥本身） */
    private String fingerprint;

    /** yyyy-MM-dd HH:mm:ss */
    private String activateTime;

    private String activatedBy;

    public static LicenseVO empty(String message) {
        LicenseVO vo = new LicenseVO();
        vo.setActivated(false);
        vo.setValid(false);
        vo.setMessage(message);
        return vo;
    }

    public static String day(LocalDate date) {
        return date == null ? null : date.toString();
    }

    public static String time(LocalDateTime dateTime) {
        return dateTime == null ? null : dateTime.toString().replace('T', ' ').substring(0, 19);
    }
}
