package com.dataviz.common.minio.service;

import com.dataviz.common.minio.model.FileUploadResult;
import com.dataviz.common.minio.properties.MinioProperties;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.ObjectWriteResponse;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.http.Method;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 发给 MinIO SDK 的**参数形状**（BH1 的 API-152 就藏在这里）。
 * <p>真容器闭环测试要 {@code -Dminio.live=true} 才跑，CI 上默认跳过 ⇒ 门禁里必须留一层不依赖 Docker 的判据，
 * 否则这条修好的坑可以被人原样改回去而全量单测仍然绿。这里钉的是 SDK 的硬约束：
 * objectSize 与 partSize <b>不能同时为 -1</b>（长度未知时必须给一个 ≥5 MiB 的分片），
 * 而 {@code PutObjectArgs} 在建好之前就会抛 {@code IllegalArgumentException}，一个请求都发不出去。</p>
 */
class MinioServiceUploadArgsTest {

    private static final String BUCKET = "dataviz";
    private static final String OBJECT = "bh-test/a.txt";

    private MinioClient minioClient;
    private MinioProperties properties;
    private MinioService service;

    @BeforeEach
    void setUp() {
        minioClient = mock(MinioClient.class);
        properties = new MinioProperties();
        properties.setEndpoint("http://minio.test:9000");
        properties.setAccessKey("minioadmin");
        properties.setSecretKey("s3cr3t-DO-NOT-LEAK");
        properties.setBucketName(BUCKET);
        service = new MinioService(minioClient, properties);
    }

    private void stubPutOk() throws Exception {
        ObjectWriteResponse response = mock(ObjectWriteResponse.class);
        when(response.etag()).thenReturn("etag-abc");
        when(minioClient.putObject(any(PutObjectArgs.class))).thenReturn(response);
    }

    private ByteArrayInputStream body() {
        return new ByteArrayInputStream("hello-minio".getBytes(StandardCharsets.UTF_8));
    }

    // ---------- 长度未知：必须带合法分片 ----------

    @Test
    @DisplayName("长度未知的上传 - objectSize=-1 时 partSize 必须是正数（API-152 的回归闸门）")
    void should_sendPositivePartSize_when_objectSizeUnknown() throws Exception {
        stubPutOk();
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);

        service.upload(BUCKET, OBJECT, body(), "text/plain");

        ArgumentCaptor<PutObjectArgs> captor = ArgumentCaptor.forClass(PutObjectArgs.class);
        verify(minioClient).putObject(captor.capture());
        PutObjectArgs args = captor.getValue();
        assertThat(args.objectSize()).isEqualTo(-1L);
        // 修复前这里是 -1：SDK 直接 IllegalArgumentException，任何走这个重载的上传 100% 失败
        assertThat(args.partSize()).isGreaterThan(0L);
        assertThat(args.partSize()).isGreaterThanOrEqualTo(5L * 1024 * 1024);
    }

    @Test
    @DisplayName("长度未知的上传 - 结果里 size 诚实记 -1，不假装知道字节数")
    void should_reportUnknownSize_when_objectSizeUnknown() throws Exception {
        stubPutOk();
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);

        FileUploadResult result = service.upload(BUCKET, OBJECT, body(), "text/plain");

        assertThat(result.getSize()).isEqualTo(-1L);
        assertThat(result.getEtag()).isEqualTo("etag-abc");
        assertThat(result.getObjectName()).isEqualTo(OBJECT);
        // URL 由 endpoint + bucket + object 拼出，凭据不进 URL（规约）
        assertThat(result.getFileUrl()).isEqualTo("http://minio.test:9000/" + BUCKET + "/" + OBJECT)
                .doesNotContain("s3cr3t-DO-NOT-LEAK").doesNotContain("minioadmin");
    }

    // ---------- 长度已知：交给 SDK 自己推分片 ----------

    @Test
    @DisplayName("长度已知的上传 - 报真实 objectSize，partSize 留 -1 让 SDK 按长度推")
    void should_sendKnownObjectSize_when_sizeProvided() throws Exception {
        stubPutOk();
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);

        FileUploadResult result = service.upload(BUCKET, OBJECT, body(), 4096L, "text/plain");

        ArgumentCaptor<PutObjectArgs> captor = ArgumentCaptor.forClass(PutObjectArgs.class);
        verify(minioClient).putObject(captor.capture());
        assertThat(captor.getValue().objectSize()).isEqualTo(4096L);
        // 传 -1 不等于读回 -1：SDK 在建 args 时按已知长度把分片算出来了（这里就是 4096）。
        // 所以判据只能是"分片落在合法区间且不超过对象本身"，钉 -1 是我一开始猜错的实现。
        assertThat(captor.getValue().partSize()).isBetween(1L, 4096L);
        assertThat(result.getSize()).isEqualTo(4096L);
    }

    @Test
    @DisplayName("显式分片上传 - partSize 原样透传，不改写调用方给的窗口")
    void should_passThroughPartSize_when_multipartExplicit() throws Exception {
        stubPutOk();
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);

        service.uploadMultipart(BUCKET, OBJECT, body(), 20L * 1024 * 1024, "application/octet-stream", 10 * 1024 * 1024);

        ArgumentCaptor<PutObjectArgs> captor = ArgumentCaptor.forClass(PutObjectArgs.class);
        verify(minioClient).putObject(captor.capture());
        assertThat(captor.getValue().partSize()).isEqualTo(10L * 1024 * 1024);
    }

    // ---------- 桶：存在就别重复建，不存在就建 ----------

    @Test
    @DisplayName("上传前探桶 - 桶已存在时不再 makeBucket（避免每次都撞一次建桶请求）")
    void should_notMakeBucket_when_bucketAlreadyExists() throws Exception {
        stubPutOk();
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);

        service.upload(BUCKET, OBJECT, body(), "text/plain");

        verify(minioClient).bucketExists(any(BucketExistsArgs.class));
        verify(minioClient, never()).makeBucket(any(MakeBucketArgs.class));
    }

    @Test
    @DisplayName("上传前探桶 - 桶不存在时先建再传，且建的是参数里那个桶")
    void should_makeBucket_when_bucketMissing() throws Exception {
        stubPutOk();
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(false);

        service.upload("other-bucket", OBJECT, body(), "text/plain");

        ArgumentCaptor<MakeBucketArgs> makeCaptor = ArgumentCaptor.forClass(MakeBucketArgs.class);
        verify(minioClient).makeBucket(makeCaptor.capture());
        assertThat(makeCaptor.getValue().bucket()).isEqualTo("other-bucket");
    }

    // ---------- 失败语义：包一层就够，根因必须留着 ----------

    @Test
    @DisplayName("SDK 报错 - 只包一层 RuntimeException，根因原样带着（别把 IllegalArgumentException 埋成第二层）")
    void should_preserveRootCause_when_putFails() throws Exception {
        IllegalStateException root = new IllegalStateException("Access Denied");
        when(minioClient.putObject(any(PutObjectArgs.class))).thenThrow(root);
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);

        // 长度未知的重载现在委托分片路径 ⇒ 文案是 "multipart upload failed"（API-152 修完的连带行为变更，
        // 比原来"upload failed"更准确：那条路本来就是 multipart）。这里显式钉住，改动必须是有意的。
        assertThatThrownBy(() -> service.upload(BUCKET, OBJECT, body(), "text/plain"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("MinIO multipart upload failed")
                .matches(e -> e.getCause() == root, "根因就是 SDK 抛出的那个异常");

        assertThatThrownBy(() -> service.upload(BUCKET, OBJECT, body(), 11L, "text/plain"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("MinIO upload failed")
                .matches(e -> e.getCause() == root, "定长路径的文案不变");
    }

    @Test
    @DisplayName("下载/删除失败 - 各自的消息带得出来，运维不用猜是哪一步断了")
    void should_nameFailingStep_when_downloadOrDeleteFails() throws Exception {
        when(minioClient.getObject(any(GetObjectArgs.class)))
                .thenThrow(new IllegalStateException("object does not exist"));
        // removeObject 是 void，只能走 doThrow（when(...) 对 void 方法编译不过）
        org.mockito.Mockito.doThrow(new IllegalStateException("network down"))
                .when(minioClient).removeObject(any(RemoveObjectArgs.class));

        assertThatThrownBy(() -> service.download(BUCKET, OBJECT))
                .isInstanceOf(RuntimeException.class).hasMessageContaining("download failed");
        assertThatThrownBy(() -> service.delete(BUCKET, OBJECT))
                .isInstanceOf(RuntimeException.class).hasMessageContaining("delete failed");
    }

    // ---------- 预签名：浏览器不带凭据的那条路，窗口要按调用方给的单位换算 ----------

    @Test
    @DisplayName("预签名 URL - 按调用方单位换算成秒（file-service 用 7 天 = 604800 秒，SigV4 的上限）")
    void should_convertExpiryToSeconds_when_presign() throws Exception {
        when(minioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class))).thenReturn("http://minio.test/signed");

        assertThat(service.getPresignedUrl(BUCKET, OBJECT, 7, TimeUnit.DAYS)).isEqualTo("http://minio.test/signed");

        ArgumentCaptor<GetPresignedObjectUrlArgs> captor = ArgumentCaptor.forClass(GetPresignedObjectUrlArgs.class);
        verify(minioClient).getPresignedObjectUrl(captor.capture());
        assertThat(captor.getValue().expiry()).isEqualTo(604800);
        assertThat(captor.getValue().method()).isEqualTo(Method.GET);
    }

    @Test
    @DisplayName("预签名 URL 默认窗口 - 两参数版是 1 小时，不是悄悄改成 7 天")
    void should_useOneHourDefault_when_presignWithoutExpiry() throws Exception {
        when(minioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class))).thenReturn("http://minio.test/signed");

        service.getPresignedUrl(BUCKET, OBJECT);

        ArgumentCaptor<GetPresignedObjectUrlArgs> captor = ArgumentCaptor.forClass(GetPresignedObjectUrlArgs.class);
        verify(minioClient).getPresignedObjectUrl(captor.capture());
        assertThat(captor.getValue().expiry()).isEqualTo(3600);
    }
}
