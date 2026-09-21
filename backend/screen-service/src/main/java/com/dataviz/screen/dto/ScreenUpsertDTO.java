package com.dataviz.screen.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 大屏创建/更新入参，字段与前端 shared-types Screen 对齐。
 * null 字段表示不修改（create 时空配置落默认值）。
 */
@Data
public class ScreenUpsertDTO {

    private String name;

    private String description;

    private String cover;

    private Integer width;

    private Integer height;

    /** 画布配置（ScreenConfig），width/height/layers 会一并存入 config_json */
    private Map<String, Object> config;

    private List<Object> components;

    private List<Object> layers;

    /** 三端变体 {"mobile":{...},"tablet":{...},"pc":{...}} */
    private Map<String, Object> variants;

    private String adaptMode;
}
