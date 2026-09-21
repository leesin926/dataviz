package com.dataviz.file.dto;

import com.dataviz.common.core.result.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 文件查询DTO
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class FileQueryDTO extends PageQuery {

    /**
     * 业务类型
     */
    private String bizType;

    /**
     * 业务ID
     */
    private Long bizId;

    /**
     * 原始文件名关键字
     */
    private String keyword;

    /**
     * 上传者ID
     */
    private Long uploaderId;
}
