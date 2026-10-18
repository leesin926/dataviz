package com.dataviz.datasource.dto;

import java.util.List;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

import lombok.Data;

/**
 * 结构化批量写入入参（ETL 目标端落库走这里）。
 * <p>
 * 刻意<b>不接收 SQL 文本</b>：内部端点一旦允许调用方给任意 DML，配 DAG 的人就拿到了一条比查询本身
 * 更大的通道（他能写到这个数据源授权的任何表，而写坏的代价是不可回滚的数据）。表名与列名过标识符
 * 白名单，值一律走 {@code PreparedStatement} 绑定 —— 与行权限规则同一个判据（见 2026-10-16 补丁 DDL 注释②）。
 */
@Data
public class WriteBatchDTO {

    @NotNull(message = "数据源ID不能为空")
    private Long datasourceId;

    @NotBlank(message = "目标表名不能为空")
    private String table;

    /** 写入列，顺序即 rows 里每行的取值顺序 */
    @NotEmpty(message = "写入列不能为空")
    private List<String> columns;

    /**
     * 数据行。每行长度必须等于 columns 长度；值只接受 JSON 标量（字符串/数值/布尔）与 null ——
     * 嵌套对象/数组在这里没有合理的落库形状，收下只会写出一列 {@code {a=1}} 这样的脏文本。
     */
    private List<List<Object>> rows;
}
