package com.dataviz.openapi.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("openapi_app")
public class OpenApiApp {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    private String appName;

    private String appKey;

    private String appSecret;

    private String permissions;

    private Integer rateLimit;

    private String ipWhitelist;

    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
