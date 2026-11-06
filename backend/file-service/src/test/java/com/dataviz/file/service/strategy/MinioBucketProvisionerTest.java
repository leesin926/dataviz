package com.dataviz.file.service.strategy;

import com.dataviz.common.minio.service.MinioService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * "起得来"必须等于"MinIO 真能用"（BH1）。
 * <p>路由器只校验配置项是否齐全，这里补上连通性与权限这一关：探测失败/建桶失败都要让容器起不来，
 * 而不是等第一次上传才炸。全部用 mock 的 MinioService，不依赖容器是否在跑。</p>
 */
class MinioBucketProvisionerTest {

    private static final String SECRET = "s3cr3t-DO-NOT-LEAK";
    private static final String ENDPOINT = "http://minio.internal:9000";

    private final MinioService minioService = mock(MinioService.class);

    private MinioBucketProvisioner provisioner(String endpoint, String bucket) {
        return new MinioBucketProvisioner(minioService, endpoint, bucket);
    }

    @Test
    @DisplayName("桶已存在 - 验证通过，不重复建桶")
    void should_passWithoutCreating_when_bucketAlreadyExists() {
        when(minioService.bucketExists("dataviz")).thenReturn(true);

        assertThatCode(provisioner(ENDPOINT, "dataviz")::verifyReachableAndEnsureBucket).doesNotThrowAnyException();

        verify(minioService).bucketExists("dataviz");
        verify(minioService, never()).createBucket(anyString());
    }

    @Test
    @DisplayName("桶不存在 - 当场建桶，第一次上传不必再等别人手工建")
    void should_createBucket_when_bucketMissing() {
        when(minioService.bucketExists("dataviz")).thenReturn(false);

        assertThatCode(provisioner(ENDPOINT, "dataviz")::verifyReachableAndEnsureBucket).doesNotThrowAnyException();

        verify(minioService).createBucket("dataviz");
    }

    @Test
    @DisplayName("MinIO 不可达 - 启动即失败并说明去查容器与凭据")
    void should_failStartup_when_minioUnreachable() {
        when(minioService.bucketExists("dataviz")).thenThrow(new RuntimeException("MinIO bucket check failed",
                new java.net.ConnectException("Connection refused (Connection refused)")));

        assertThatThrownBy(provisioner(ENDPOINT, "dataviz")::verifyReachableAndEnsureBucket)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MinIO 探测失败")
                .hasMessageContaining(ENDPOINT)
                .hasMessageContaining("Connection refused");
    }

    @Test
    @DisplayName("凭据无建桶权限 - 失败信息给出手工建桶/换 key 的下一步")
    void should_tellNextStep_when_createBucketDenied() {
        when(minioService.bucketExists("dataviz")).thenReturn(false);
        doThrow(new RuntimeException("MinIO create bucket failed",
                new IllegalStateException("Access Denied (403)")))
                .when(minioService).createBucket("dataviz");

        assertThatThrownBy(provisioner(ENDPOINT, "dataviz")::verifyReachableAndEnsureBucket)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("创建失败")
                .hasMessageContaining("Access Denied");
    }

    @Test
    @DisplayName("minio.bucket 空白 - 拒绝启动，不猜一个桶名")
    void should_refuseStartup_when_bucketBlank() {
        assertThatThrownBy(provisioner(ENDPOINT, "   ")::verifyReachableAndEnsureBucket)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("minio.bucket");

        assertThatThrownBy(provisioner(ENDPOINT, null)::verifyReachableAndEnsureBucket)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("minio.bucket");
    }

    @Test
    @DisplayName("endpoint 写成带凭据的 URL - 失败文本只留 ***，凭据值不外泄")
    void should_notLeakCredentials_when_endpointCarriesUserInfo() {
        when(minioService.bucketExists("dataviz")).thenThrow(new RuntimeException("Connection reset"));

        assertThatThrownBy(provisioner("http://minioadmin:" + SECRET + "@minio.internal:9000", "dataviz")
                ::verifyReachableAndEnsureBucket)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("http://***@minio.internal:9000")
                .hasMessageNotContaining(SECRET)
                .hasMessageNotContaining("minioadmin");
    }

    @Test
    @DisplayName("异常没有 message - 退回类名，不留一句空原因")
    void should_nameExceptionClass_when_rootCauseHasNoMessage() {
        when(minioService.bucketExists("dataviz")).thenThrow(new RuntimeException(new NullPointerException()));

        assertThatThrownBy(provisioner(ENDPOINT, "dataviz")::verifyReachableAndEnsureBucket)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("NullPointerException");
    }
}
