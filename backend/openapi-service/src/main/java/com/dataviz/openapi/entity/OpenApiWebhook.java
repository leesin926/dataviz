package com.dataviz.openapi.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("openapi_webhook")
public class OpenApiWebhook {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    private Long appId;

    private String eventType;

    private String url;

    private String secret;

    private Integer status;
}
