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

    private Long createBy;

    private LocalDateTime createTime;
}
