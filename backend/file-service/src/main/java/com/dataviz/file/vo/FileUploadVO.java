package com.dataviz.file.vo;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileUploadVO {

    private Long id;

    private String fileName;

    private String filePath;

    private String url;
}
