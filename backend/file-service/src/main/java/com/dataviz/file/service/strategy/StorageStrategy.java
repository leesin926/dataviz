package com.dataviz.file.service.strategy;

import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

/**
 * 存储策略接口
 */
public interface StorageStrategy {

    /**
     * 上传文件
     *
     * @param file        上传的文件
     * @param bucketName  存储桶/目录（可选）
     * @param objectName  对象名称
     * @return 存储路径或对象ID
     */
    String upload(MultipartFile file, String bucketName, String objectName);

    /**
     * 下载文件
     *
     * @param bucketName  存储桶/目录（可选）
     * @param objectName  对象名称
     * @return 文件输入流
     */
    InputStream download(String bucketName, String objectName);

    /**
     * 删除文件
     *
     * @param bucketName  存储桶/目录（可选）
     * @param objectName  对象名称
     */
    void delete(String bucketName, String objectName);

    /**
     * 获取文件访问URL
     *
     * @param bucketName  存储桶/目录（可选）
     * @param objectName  对象名称
     * @return 文件访问URL
     */
    String getFileUrl(String bucketName, String objectName);
}
