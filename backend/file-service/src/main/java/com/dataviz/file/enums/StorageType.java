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

    /** 策略 bean 的注册名约定：{@code <code>StorageStrategy}（见 LocalStorageStrategy/MinioStorageStrategy 的 @Component 名） */
    public String strategyBeanName() {
        return code + "StorageStrategy";
    }

    /** 按配置值匹配枚举（不区分大小写、可空安全）；找不到返回 null，由调用方决定是拒绝还是回退 */
    public static StorageType fromCode(String code) {
        if (code == null) {
            return null;
        }
        String normalized = code.trim().toLowerCase();
        for (StorageType type : values()) {
            if (type.code.equals(normalized)) {
                return type;
            }
        }
        return null;
    }

    /** supported 列表用于报错时把可选值说全，免得运维去猜 */
    public static String supportedCodes() {
        StringBuilder sb = new StringBuilder();
        for (StorageType type : values()) {
            if (sb.length() > 0) {
                sb.append("/");
            }
            sb.append(type.code);
        }
        return sb.toString();
    }
}
