package com.dataviz.file.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 存储类型枚举
 */
@Getter
@AllArgsConstructor
public enum StorageType {

    LOCAL("local", "本地存储"),
    MINIO("minio", "MinIO对象存储"),
    OSS("oss", "阿里云OSS");

    private final String code;
    private final String desc;
}
