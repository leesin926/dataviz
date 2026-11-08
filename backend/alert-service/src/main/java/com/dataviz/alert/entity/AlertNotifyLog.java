package com.dataviz.alert.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("alert_notify_log")
public class AlertNotifyLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long eventId;

    /**
     * 这一条派发属于哪个租户。列是 {@code pi4-01} 补的<b>可空派生列</b>，而这张表在租户拦截器
     * 黑名单里（{@code MybatisPlusConfig.IGNORE_TENANT_TABLES}）—— 拦截器不会替我们注入，也不会替我们填值，
     * 所以打开分片之后它同时是<b>路由键</b>：必须由 {@code NotifyDispatcher} 从事件上带过来。
     * <p>
     * 与 PI4 那条 {@code OpenApiLog.tenantId} 的教训同族：派生列只补历史不补写入方，新写的每一行仍是 NULL，
     * 而 NULL 在 {@code route-by=TENANT} 下不是"落到某个默认实例"，是当场拒绝。
     */
    private Long tenantId;

    /**
     * EMAIL / SMS / WEBHOOK / DINGTALK
     */
    private String channel;

    private String recipient;

    /**
     * SUCCESS / FAILED
     */
    private String status;

    private String errorMessage;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
