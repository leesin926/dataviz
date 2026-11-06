package com.dataviz.file.service.strategy;

import com.dataviz.common.core.exception.BizException;
import com.dataviz.common.core.result.ErrorCode;
import com.dataviz.common.minio.service.MinioService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

/**
 * MinIO存储策略
 */
@Slf4j
@Component("minioStorageStrategy")
@RequiredArgsConstructor
public class MinioStorageStrategy implements StorageStrategy {

    private final MinioService minioService;

    @Override
    public String upload(MultipartFile file, String bucketName, String objectName) {
        try {
            String bucket = (bucketName != null && !bucketName.isEmpty()) ? bucketName : "dataviz";
            // 用带 size 的重载：MultipartFile 本身就知道字节数，报给 SDK 才能走单分片上传，
            // 而不是让 common-minio 按"长度未知"退成分片模式（FileUploadResult 里的 size 也会是 -1）。
            minioService.upload(bucket, objectName, file.getInputStream(), file.getSize(), file.getContentType());
            log.info("MinIO文件上传成功: bucket={}, object={}", bucket, objectName);
            return objectName;
        } catch (Exception e) {
            log.error("MinIO文件上传失败", e);
            throw new BizException(ErrorCode.FILE_UPLOAD_FAILED);
        }
    }

    @Override
    public InputStream download(String bucketName, String objectName) {
        String bucket = (bucketName != null && !bucketName.isEmpty()) ? bucketName : "dataviz";
        return minioService.download(bucket, objectName);
    }

    @Override
    public void delete(String bucketName, String objectName) {
        String bucket = (bucketName != null && !bucketName.isEmpty()) ? bucketName : "dataviz";
        minioService.delete(bucket, objectName);
        log.info("MinIO文件删除成功: bucket={}, object={}", bucket, objectName);
    }

    @Override
    public String getFileUrl(String bucketName, String objectName) {
        String bucket = (bucketName != null && !bucketName.isEmpty()) ? bucketName : "dataviz";
        return minioService.getPresignedUrl(bucket, objectName, 7, java.util.concurrent.TimeUnit.DAYS);
    }
}
