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
@TableName("openapi_log")
public class OpenApiLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long clientId;

    private String appName;

    private String apiPath;

    /**
     * HTTP method: GET, POST, PUT, DELETE
     */
    private String method;

    /**
     * JSON string of request parameters
     */
    private String requestParams;

    private Integer responseCode;

    /**
     * Execution time in milliseconds
     */
    private Long executionTime;

    private String ip;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
