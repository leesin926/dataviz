package com.dataviz.alert.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 通知渠道出参。
 * <p>
 * 关键点是 {@code config} 里<b>口令字段已被脱敏成 {@code "***"}</b>：这张表存的是服务端将来要拿去 POST
 * 的地址与密钥，原来控制器直接把实体回给调用方，等于任何持有 {@code alert:read} 的账号都能读到
 * 群机器人 token 和 SMTP 口令。
 */
@Data
@Builder
public class NotifyChannelVO {

    private Long id;
    private String name;
    private String type;
    private Boolean enabled;
    private Map<String, Object> config;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    /**
     * 渠道类型本身是否已经接通（短信缺服务商凭据 ⇒ false）。
     * 和"这条配置对不对"是两件事，界面据此决定提示"通道未接入"还是"配置有误"。
     */
    private boolean deliverable;

    /**
     * 同类型是否存在别的多条<b>启用</b>记录。派发侧每种类型只取 id 最小的那条 ⇒ 多配的那条会被静默忽略，
     * 这个事实必须由界面说出来，不能让用户配了半天等不到通知。
     */
    private Boolean shadowedByOtherRow;

    /** 被哪条记录遮住（{@code shadowedByOtherRow} 为 true 时有值） */
    private Long shadowId;
}
