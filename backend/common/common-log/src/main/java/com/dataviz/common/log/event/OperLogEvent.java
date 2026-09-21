package com.dataviz.common.log.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 操作日志事件
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OperLogEvent implements Serializable {

    
    private static final long serialVersionUID = 1L;

    /**
     * 日志主键
     */
    private Long id;

    /**
     * 操作模块
     */
    private String module;

    /**
     * 操作类型
     */
    private String operType;

    /**
     * 操作描述
     */
    private String description;

    /**
     * 请求方法
     */
    private String method;

    /**
     * 请求URL
     */
    private String requestUrl;

    /**
     * 请求方式 (GET, POST, PUT, DELETE)
     */
    private String httpMethod;

    /**
     * 请求参数
     */
    private String requestParams;

    /**
     * 响应结果
     */
    private String responseResult;

    /**
     * 操作状态（0=成功, 1=失败）
     */
    private Integer status;

    /**
     * 错误信息
     */
    private String errorMsg;

    /**
     * 操作人ID
     */
    private Long operUserId;

    /**
     * 操作人用户名
     */
    private String operUsername;

    /**
     * 操作人昵称
     */
    private String operNickname;

    /**
     * 操作人IP
     */
    private String operIp;

    /**
     * 操作人所在地
     */
    private String operLocation;

    /**
     * 操作时间
     */
    private LocalDateTime operTime;

    /**
     * 耗时（毫秒）
     */
    private Long costTime;

    /**
     * 租户ID
     */
    private String tenantId;
}
