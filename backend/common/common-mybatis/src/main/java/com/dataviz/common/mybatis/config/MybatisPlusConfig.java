package com.dataviz.common.mybatis.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.dataviz.common.security.context.SecurityContextHolder;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.StringValue;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * MyBatis-Plus配置类
 */
@Slf4j
@Configuration
public class MybatisPlusConfig {

    /**
     * 不做租户过滤的表 —— 判据只有一条：**这张表没有 tenant_id 列**。
     *
     * <p>租户条件是"先有会话才注入"的（见下面 {@code ignoreTable} 的第一句），所以列表不完整时炸的是
     * **登录态**那条路：免登与内部调用一切正常，用户一登录就打 {@code SQLSyntaxErrorException:
     * Unknown column 'tenant_id' in 'where clause'}。这类症状最容易被当成业务代码写错。
     *
     * <p>这份清单来自对 {@code deploy/sql/init/*.sql} 与 {@code deploy/sql/patch/*.sql} 的列审计
     * （51 张表 / 21 张无 tenant_id）。其中 {@code demo_*} 三张是纯业务数据表，只被 JdbcTemplate
     * 与外部数据源查询命中，不在这套 SqlSessionFactory 的映射范围内，故不列。
     *
     * <p>选"黑名单（列出无列的表）"而不是"白名单（列出有列的表）"是有意的：新建一张带 tenant_id 的表时，
     * 黑名单会**默认过滤**（漏了就静默跨租户），白名单会**默认不过滤**。宁可能见地漏一次，不要静默漏。
     *
     * <p>关联表 {@code sys_user_role} / {@code sys_role_permission} 没有租户列，它们的租户归属由**父表**
     * 决定（{@code requireRoleInTenant} / {@code requireUserInTenant} 已经在这两个方法入口挡过）。
     */
    private static final Set<String> IGNORE_TENANT_TABLES = new HashSet<String>(Arrays.asList(
            // —— 平台与全局表 ——
            "sys_tenant",
            // —— RBAC：码表全局共享，关联表靠父表定租户 ——
            "sys_permission", "sys_user_role", "sys_role_permission", "sys_oauth_client",
            // —— 元数据 / 子表 / 日志表（只存明细，不带租户列）——
            "datasource_metadata", "dashboard_widget", "screen_component",
            "etl_task_log", "schedule_job_log", "alert_notify_log", "openapi_log",
            "ai_message", "few_shot_example",
            // —— 监控（monitor-service 自采自看）——
            "service_instance", "service_health", "monitor_metric", "monitor_alert"
    ));

    /**
     * MyBatis-Plus插件配置
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        // 多租户插件
        interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(new TenantLineHandler() {
            @Override
            public Expression getTenantId() {
                String currentTenantId = SecurityContextHolder.getTenantId();
                return currentTenantId != null ? new StringValue(currentTenantId) : null;
            }

            @Override
            public String getTenantIdColumn() {
                return "tenant_id";
            }

            @Override
            public boolean ignoreTable(String tableName) {
                if (SecurityContextHolder.getTenantId() == null) {
                    return true;
                }
                return IGNORE_TENANT_TABLES.contains(tableName.toLowerCase());
            }
        }));

        // 分页插件
        PaginationInnerInterceptor paginationInterceptor = new PaginationInnerInterceptor(DbType.MYSQL);
        paginationInterceptor.setMaxLimit(500L);
        interceptor.addInnerInterceptor(paginationInterceptor);

        return interceptor;
    }

    /**
     * 自动填充处理器
     */
    @Bean
    public MetaObjectHandler metaObjectHandler() {
        return new MetaObjectHandler() {
            @Override
            public void insertFill(MetaObject metaObject) {
                LocalDateTime now = LocalDateTime.now();
                String currentUser = SecurityContextHolder.getUsername();
                String tenantId = SecurityContextHolder.getTenantId();

                this.strictInsertFill(metaObject, "createTime", LocalDateTime.class, now);
                this.strictInsertFill(metaObject, "updateTime", LocalDateTime.class, now);
                this.strictInsertFill(metaObject, "createBy", String.class, currentUser != null ? currentUser : "system");
                this.strictInsertFill(metaObject, "updateBy", String.class, currentUser != null ? currentUser : "system");
                this.strictInsertFill(metaObject, "deleted", Integer.class, 0);
                if (tenantId != null && metaObject.hasSetter("tenantId")) {
                    this.strictInsertFill(metaObject, "tenantId", String.class, tenantId);
                }
            }

            @Override
            public void updateFill(MetaObject metaObject) {
                this.strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
                String currentUser = SecurityContextHolder.getUsername();
                this.strictUpdateFill(metaObject, "updateBy", String.class, currentUser != null ? currentUser : "system");
            }
        };
    }
}
