package com.dataviz.file.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 文件上传结果VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileUploadResultVO {

    /**
     * 文件记录ID
     */
    private Long id;

    /**
     * 原始文件名
     */
    private String originalName;

    /**
     * 存储文件名
     */
    private String storedName;

    /**
     * 文件访问URL
     */
    private String fileUrl;

    /**
     * 文件大小（字节）
     */
    private Long fileSize;

    /**
     * 文件内容类型
     */
    private String contentType;

    /**
     * 存储类型
     */
    private String storageType;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}
