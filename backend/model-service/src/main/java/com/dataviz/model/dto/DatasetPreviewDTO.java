package com.dataviz.model.dto;

import javax.validation.constraints.NotNull;
import lombok.Data;

/**
 * 数据集预览DTO
 */
@Data
public class DatasetPreviewDTO {

    @NotNull(message = "数据集ID不能为空")
    private Long datasetId;

    /**
     * 预览行数, 默认100
     */
    private Integer limit = 100;
}
