package com.dataviz.screen.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 大屏出参，已解析 JSON 字符串，字段与前端 shared-types Screen 对齐。
 */
@Data
public class ScreenVO {

    private Long id;

    private Long tenantId;

    private String name;

    private String description;

    private String cover;

    private Integer width;

    private Integer height;

    /** draft|published|archived */
    private String status;

    private Map<String, Object> config;

    private List<Object> components;

    private List<Object> layers;

    /** 三端变体；按 platform 展平返回时为 null */
    private Map<String, Object> variants;

    private String adaptMode;

    private Long viewCount;

    private String shareToken;

    private Long createdBy;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
