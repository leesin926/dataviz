package com.dataviz.file.service.strategy;

import com.dataviz.common.minio.config.MinioConfig;
import com.dataviz.common.minio.properties.MinioProperties;
import com.dataviz.common.minio.service.MinioService;
import io.minio.MinioClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 真容器闭环冒烟（BH1）：上传 → 读回字节一致 → 预签名 URL 匿名可取 → 删除后读不到。
 *
 * <p>单元测试里的 mock 只能证明"代码按契约调用"，证明不了 MinIO 真的收下了对象。这一份直接打真容器，
 * 用的是生产类本身（{@link MinioStorageStrategy} + {@link MinioService}），不是另写一套等价逻辑。</p>
 *
 * <p>默认<b>不跑</b>：需要 {@code -Dminio.live=true} 且 MinIO 容器可达。CI 上没有容器时照常跳过，
 * 不会把全量单测门禁绑在 Docker 上。</p>
 */
@EnabledIfSystemProperty(named = "minio.live", matches = "true")
@DisplayName("MinIO 真容器闭环（需 -Dminio.live=true）")
class MinioLiveRoundTripTest {

    private static final String BUCKET = "dataviz";

    private final MinioService minioService = realService();
    private final MinioStorageStrategy strategy = new MinioStorageStrategy(minioService);

    /** 凭据取环境变量，默认值与 application.yml / docker-compose.yml 一致（minioadmin/minioadmin123） */
    private static MinioService realService() {
        MinioProperties properties = new MinioProperties();
        properties.setEndpoint(env("MINIO_ENDPOINT", "http://localhost:9000"));
        properties.setAccessKey(env("MINIO_ACCESS_KEY", "minioadmin"));
        properties.setSecretKey(env("MINIO_SECRET_KEY", "minioadmin123"));
        properties.setBucketName(BUCKET);
        MinioClient client = new MinioConfig().minioClient(properties);
        return new MinioService(client, properties);
    }

    private static String env(String key, String fallback) {
        String value = System.getenv(key);
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    @Test
    @DisplayName("上传-读回-预签名取回-删除：四步都对得上")
    void should_storeFetchAndRemove_when_minioContainerIsLive() throws Exception {
        String marker = "dataviz-live-" + UUID.randomUUID();
        byte[] payload = marker.getBytes(StandardCharsets.UTF_8);
        String object = "bh-live/" + UUID.randomUUID() + ".txt";

        strategy.upload(new MockMultipartFile("file", "live.txt", "text/plain", payload), BUCKET, object);
        assertThat(readAll(strategy.download(BUCKET, object))).isEqualTo(payload);

        String presigned = strategy.getFileUrl(BUCKET, object);
        assertThat(presigned).contains(BUCKET).contains("X-Amz-Signature");
        assertThat(fetchAnonymous(presigned)).isEqualTo(marker);

        strategy.delete(BUCKET, object);
        assertThatThrownBy(() -> strategy.download(BUCKET, object))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("download failed");
    }

    @Test
    @DisplayName("MinIO 不可达时 - 上传不静默成功，抛 FILE_UPLOAD_FAILED")
    void should_failUpload_when_endpointRejects() {
        MinioProperties broken = new MinioProperties();
        broken.setEndpoint("http://127.0.0.1:1");
        broken.setAccessKey("minioadmin");
        broken.setSecretKey("minioadmin123");
        broken.setBucketName(BUCKET);
        MinioStorageStrategy unreachable = new MinioStorageStrategy(
                new MinioService(new MinioConfig().minioClient(broken), broken));

        assertThatThrownBy(() -> unreachable.upload(
                new MockMultipartFile("file", "x.txt", "text/plain", "x".getBytes(StandardCharsets.UTF_8)),
                BUCKET, "bh-live/never.txt"))
                .isInstanceOf(com.dataviz.common.core.exception.BizException.class);
    }

    private static byte[] readAll(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        int read;
        while ((read = in.read(chunk)) > 0) {
            out.write(chunk, 0, read);
        }
        in.close();
        return out.toByteArray();
    }

    /** 预签名 URL 的意义是"浏览器不用带凭据也能取"，所以这里刻意用不带任何头的匿名请求取回内容 */
    private static String fetchAnonymous(String presigned) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(presigned).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(10000);
        try {
            assertThat(conn.getResponseCode()).isEqualTo(200);
            return new String(readAll(conn.getInputStream()), StandardCharsets.UTF_8);
        } finally {
            conn.disconnect();
        }
    }
}
