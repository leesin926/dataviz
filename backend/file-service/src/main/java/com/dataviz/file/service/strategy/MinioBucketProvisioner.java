package com.dataviz.file.service.strategy;

import com.dataviz.common.minio.service.MinioService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

/**
 * 写侧选了 minio 时，启动即验证对象存储真的能用：先探一次 {@code bucketExists}（这一步同时验证了网络可达与
 * 凭据被接受），桶不存在就当场建出来。
 *
 * <p>为什么要单独立一个类而不是塞进 {@link StorageStrategyRouter}：路由器只判断"配置有没有配齐"，
 * 配齐不等于连得上。原来越过这一关的服务是"起来了，但第一次上传才炸"——那已是线上事故。
 * {@code MinioConfig} 无条件建 client，而建 client 不发任何请求，所以光有 bean 也证明不了连通性。</p>
 *
 * <p>用 {@code @ConditionalOnProperty} 而不是在方法里 if：{@code file.storage.type=local}（默认）的部署
 * 根本不会实例化这个 bean，因此没跑 MinIO 的环境照常启动，不会因为这里多了一道连通性检查而起不来。</p>
 *
 * <p>失败信息只报 endpoint 与桶名，且 endpoint 里若写了 {@code user:pass@} 会先抹掉——凭据值绝不进日志与异常文本。</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "file.storage.type", havingValue = "minio")
public class MinioBucketProvisioner {

    private final MinioService minioService;
    private final String endpoint;
    private final String bucket;

    public MinioBucketProvisioner(MinioService minioService,
                                 @Value("${minio.endpoint:}") String endpoint,
                                 @Value("${minio.bucket:dataviz}") String bucket) {
        this.minioService = minioService;
        this.endpoint = endpoint;
        this.bucket = bucket;
    }

    /** 启动即验证；不通/建不了桶则让容器初始化失败，而不是等第一次上传才报错 */
    @PostConstruct
    public void verifyReachableAndEnsureBucket() {
        String target = bucket == null ? "" : bucket.trim();
        if (target.isEmpty()) {
            throw new IllegalStateException("file.storage.type=minio 但 minio.bucket 为空：不知道要往哪个桶写，启动即拒绝");
        }
        String where = "endpoint=" + safeEndpoint() + ", bucket=" + target;
        boolean exists;
        try {
            exists = minioService.bucketExists(target);
        } catch (Exception e) {
            throw new IllegalStateException("MinIO 探测失败（" + where + "）：" + reasonOf(e)
                    + "——请确认容器在跑、endpoint 可访问，以及 minio.access-key/secret-key 正确（凭据值不在此回显）", e);
        }
        if (exists) {
            log.info("MinIO 启动验证通过，桶已存在: {}", where);
            return;
        }
        try {
            minioService.createBucket(target);
        } catch (Exception e) {
            throw new IllegalStateException("MinIO 桶不存在且创建失败（" + where + "）：" + reasonOf(e)
                    + "——该凭据可能没有建桶权限，请手工建桶或换用有权限的 access-key", e);
        }
        log.info("MinIO 启动验证通过，桶已自动创建: {}", where);
    }

    /** 只用于诊断文本：root cause 的 message，取不到就退回类名（MinioService 把原异常包成 RuntimeException，包两层很常见） */
    private static String reasonOf(Exception e) {
        Throwable cursor = e;
        while (cursor.getCause() != null && cursor.getCause() != cursor) {
            cursor = cursor.getCause();
        }
        String message = cursor.getMessage();
        return message == null || message.trim().isEmpty() ? cursor.getClass().getSimpleName() : message.trim();
    }

    /** endpoint 里可能被人写成 http://user:pass@host:9000——那是凭据，不能进日志 */
    private String safeEndpoint() {
        String raw = endpoint == null ? "" : endpoint.trim();
        int schemeEnd = raw.indexOf("://");
        int searchFrom = schemeEnd < 0 ? 0 : schemeEnd + 3;
        int at = raw.indexOf('@', searchFrom);
        if (at < 0) {
            return raw;
        }
        return raw.substring(0, searchFrom) + "***@" + raw.substring(at + 1);
    }
}
