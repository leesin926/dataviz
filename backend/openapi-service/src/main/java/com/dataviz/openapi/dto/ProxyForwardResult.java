package com.dataviz.openapi.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 代理转发的一次上游响应：状态码与响应体都取自上游真实返回，绝不含"合成成功"的字段。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProxyForwardResult {

    private int status;

    /** 上游响应体；能按 JSON 解析就是结构化对象，否则是原始字符串 */
    private Object body;

    public boolean is2xxSuccess() {
        return status >= 200 && status < 300;
    }
}
