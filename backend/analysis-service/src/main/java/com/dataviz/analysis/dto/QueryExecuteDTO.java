package com.dataviz.analysis.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import lombok.Data;

/**
 * 查询执行DTO
 */
@Data
public class QueryExecuteDTO {

    @NotNull(message = "数据源ID不能为空")
    private Long datasourceId;

    @NotBlank(message = "SQL不能为空")
    private String sql;

    /**
     * 查询超时时间(秒), 默认30秒
     */
    private Integer timeout = 30;

    /**
     * 最大返回行数, 默认1000
     */
    private Integer maxRows = 1000;
}
