package com.dataviz.alert.dto;

import lombok.Data;

import java.util.Map;

/**
 * 通知渠道配置入参。
 * <p>
 * {@code config} 是<b>对象</b>不是 JSON 字符串：配置页按 {@code /channel/schema} 渲染出的表单本身就是键值对，
 * 让前端自己拼 JSON 字符串只会把引号转义这类事故推到界面上。序列化在服务层做，
 * 顺手完成两件前端不该被要求知道的事：口令字段沿用库里那份、按渠道声明校验必填。
 */
@Data
public class NotifyChannelDTO {

    private Long id;

    private String name;

    /** EMAIL / SMS / WEBHOOK / DINGTALK / WECHAT / FEISHU，大小写不敏感，入库统一大写 */
    private String type;

    private Boolean enabled;

    private Map<String, Object> config;
}
