package com.dataviz.file.dto;

import lombok.Data;

/**
 * 文件上传请求DTO
 */
@Data
public class FileUploadDTO {

    /**
     * 业务类型
     */
    private String bizType;

    /**
     * 业务ID
     */
    private Long bizId;

    /**
     * 存储类型（可选，默认由系统决定）
     */
    private String storageType;

    /**
     * 存储桶名称（可选）
     */
    private String bucketName;
}
