package com.dataviz.file.service.strategy;

import com.dataviz.common.core.exception.BizException;
import com.dataviz.common.core.result.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * 本地存储策略
 */
@Slf4j
@Component("localStorageStrategy")
public class LocalStorageStrategy implements StorageStrategy {

    @Value("${file.storage.local.base-path:./uploads}")
    private String basePath;

    @Value("${file.storage.local.url-prefix:http://localhost:8094/files/}")
    private String urlPrefix;

    @Override
    public String upload(MultipartFile file, String bucketName, String objectName) {
        try {
            String dir = basePath;
            if (bucketName != null && !bucketName.isEmpty()) {
                dir = dir + "/" + bucketName;
            }
            Path dirPath = Paths.get(dir);
            if (!Files.exists(dirPath)) {
                Files.createDirectories(dirPath);
            }

            Path filePath = dirPath.resolve(objectName);
            // 确保父目录存在
            Files.createDirectories(filePath.getParent());

            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
            log.info("本地文件上传成功: {}", filePath);
            return filePath.toString();
        } catch (IOException e) {
            log.error("本地文件上传失败", e);
            throw new BizException(ErrorCode.FILE_UPLOAD_FAILED);
        }
    }

    @Override
    public InputStream download(String bucketName, String objectName) {
        try {
            String dir = basePath;
            if (bucketName != null && !bucketName.isEmpty()) {
                dir = dir + "/" + bucketName;
            }
            Path filePath = Paths.get(dir, objectName);
            if (!Files.exists(filePath)) {
                // 内部存储路径只进日志：download 被免登的 /api/file/view/{id} 调用，
                // 消息会原样返回给匿名请求方，带出 objectName 等于外泄存储目录结构与原始文件名。
                log.warn("存储对象不存在: bucket={}, object={}", bucketName, objectName);
                throw new BizException(ErrorCode.NOT_FOUND, "File content unavailable");
            }
            return new FileInputStream(filePath.toFile());
        } catch (BizException e) {
            throw e;
        } catch (FileNotFoundException e) {
            log.error("文件未找到: {}", objectName, e);
            throw new BizException(ErrorCode.NOT_FOUND, "File content unavailable");
        }
    }

    @Override
    public void delete(String bucketName, String objectName) {
        try {
            String dir = basePath;
            if (bucketName != null && !bucketName.isEmpty()) {
                dir = dir + "/" + bucketName;
            }
            Path filePath = Paths.get(dir, objectName);
            if (Files.exists(filePath)) {
                Files.delete(filePath);
                log.info("本地文件删除成功: {}", filePath);
            }
        } catch (IOException e) {
            log.error("本地文件删除失败: {}", objectName, e);
        }
    }

    @Override
    public String getFileUrl(String bucketName, String objectName) {
        String path = "";
        if (bucketName != null && !bucketName.isEmpty()) {
            path = bucketName + "/";
        }
        return urlPrefix + path + objectName;
    }
}
