package com.dataviz.datasource.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import lombok.Data;

/**
 * 测试连接DTO
 */
@Data
public class TestConnectionDTO {

    @NotNull(message = "数据源类型不能为空")
    private String type;

    @NotBlank(message = "连接配置不能为空")
    private String config;
}
