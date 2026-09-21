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
import java.util.List;

/**
 * MyBatis-Plus配置类
 */
@Slf4j
@Configuration
public class MybatisPlusConfig {

    /**
     * 不需要租户过滤的表
     */
    private static final List<String> IGNORE_TENANT_TABLES = Arrays.asList(
            "sys_tenant", "sys_menu", "sys_dict_type", "sys_dict_data"
    );

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
