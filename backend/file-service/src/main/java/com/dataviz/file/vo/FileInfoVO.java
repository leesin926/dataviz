package com.dataviz.file.vo;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileInfoVO {

    private Long id;

    private String fileName;

    private String filePath;

    private Long fileSize;

    private String fileType;

    private String bucket;

    /** 落库时记录的实际存储策略 code（local/minio/...），供前端与排障定位文件真实所在 */
    private String storageType;

    private Long createBy;

    private LocalDateTime createTime;
}
