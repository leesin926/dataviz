package com.dataviz.openapi.entity;

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
@TableName("openapi_client")
public class OpenApiClient {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String appName;

    private String appKey;

    private String appSecret;

    /**
     * ACTIVE / DISABLED
     */
    private String status;

    /**
     * Requests per minute
     */
    private Integer rateLimit;

    /**
     * Comma-separated allowed IPs, empty means all
     */
    private String allowedIps;

    private LocalDateTime expireTime;

    private Long tenantId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
