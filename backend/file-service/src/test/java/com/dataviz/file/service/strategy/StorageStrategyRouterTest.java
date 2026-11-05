package com.dataviz.file.service.strategy;

import com.dataviz.common.core.exception.BizException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * 存储策略选择与路由的判据（BE3）。
 * <p>守的是三件事：写侧按配置选策略（默认 local）、选 minio 但配置缺失时点名拒绝、
 * 读侧按行自己记录的 storage_type 走而不是按当前配置走。
 * 全部用 mock 的策略 bean 直接构造路由器，<b>不依赖 MinIO 是否在跑</b>。</p>
 */
class StorageStrategyRouterTest {

    private static final String SECRET = "s3cr3t-DO-NOT-LEAK";
    private static final String COMPLETE_ENDPOINT = "http://minio.internal:9000";

    private final StorageStrategy local = mock(StorageStrategy.class);
    private final StorageStrategy minio = mock(StorageStrategy.class);

    /** 真实容器里 Spring 就是按 bean 名注入这张 Map：local→localStorageStrategy，minio→minioStorageStrategy */
    private Map<String, StorageStrategy> strategies() {
        Map<String, StorageStrategy> map = new LinkedHashMap<>();
        map.put("localStorageStrategy", local);
        map.put("minioStorageStrategy", minio);
        return map;
    }

    private StorageStrategyRouter router(String type, String endpoint, String bucket, String accessKey, String secretKey) {
        return new StorageStrategyRouter(strategies(), type, endpoint, bucket, accessKey, secretKey);
    }

    // ---------- 写侧选择 ----------

    @Test
    @DisplayName("配置 local - 走本地策略，落库 code 记 local")
    void should_selectLocal_when_typeIsLocal() {
        StorageStrategyRouter r = router("local", "", "", "", "");

        StorageStrategyRouter.WriteRoute route = r.routeForWrite();

        assertThat(route.getStrategy()).isSameAs(local);
        assertThat(route.getStorageType()).isEqualTo("local");
        // 选 local 时 minio 没配也不该拦（validateOnStartup 只在 minio 下校验凭据）
        assertThatCode(r::validateOnStartup).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("配置 minio 且必填项齐全 - 走 minio 策略，落库 code 记 minio")
    void should_selectMinio_when_typeIsMinioWithCompleteConfig() {
        StorageStrategyRouter r = router("minio", COMPLETE_ENDPOINT, "dataviz", "minioadmin", SECRET);

        StorageStrategyRouter.WriteRoute route = r.routeForWrite();

        assertThat(route.getStrategy()).isSameAs(minio);
        assertThat(route.getStorageType()).isEqualTo("minio");
        assertThatCode(r::validateOnStartup).doesNotThrowAnyException();
    }

    // ---------- 配置缺失：fail-fast，点名 key，不泄漏值 ----------

    @Test
    @DisplayName("minio 缺 access-key - 启动与写路由都点名 minio.access-key")
    void should_nameMissingAccessKey_when_minioSelectedButAccessKeyBlank() {
        StorageStrategyRouter r = router("minio", COMPLETE_ENDPOINT, "dataviz", "", SECRET);

        assertThatThrownBy(r::validateOnStartup)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("minio.access-key");
        assertThatThrownBy(r::routeForWrite)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("minio.access-key");
    }

    @Test
    @DisplayName("minio 必填项全缺 - 四个 key 一次性列全")
    void should_listEveryMissingKey_when_minioConfigAllBlank() {
        StorageStrategyRouter r = router("minio", "", "", "", "");

        assertThatThrownBy(r::routeForWrite)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("minio.endpoint")
                .hasMessageContaining("minio.bucket")
                .hasMessageContaining("minio.access-key")
                .hasMessageContaining("minio.secret-key");
    }

    @Test
    @DisplayName("报缺失只说 key 名 - 绝不把 endpoint/凭据值带进异常文本")
    void should_notLeakCredentialValues_when_minioConfigMissing() {
        StorageStrategyRouter r = router("minio", COMPLETE_ENDPOINT, "", "minioadmin", SECRET);

        assertThatThrownBy(r::routeForWrite)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("minio.bucket")
                .hasMessageNotContaining(SECRET)
                .hasMessageNotContaining(COMPLETE_ENDPOINT);
    }

    // ---------- 不支持/空白类型：明确报错，不静默回退 local ----------

    @Test
    @DisplayName("file.storage.type 取不存在的值 - 构造即报错点名该值，不回退 local")
    void should_throwClearError_when_typeUnsupported() {
        assertThatThrownBy(() -> router("s3", "", "", "", ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("s3");
    }

    @Test
    @DisplayName("file.storage.type 留空白 - 报错要求显式配置，不悄悄当 local")
    void should_throwClearError_when_typeBlank() {
        assertThatThrownBy(() -> router("   ", "", "", "", ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("file.storage.type");
    }

    // ---------- 读侧按行路由 ----------

    @Test
    @DisplayName("行记 minio - 即便当前配置是 local，也按行路由到 minio（切配置老行不说谎）")
    void should_routeReadByRecordedType_when_rowSaysMinioButConfigIsLocal() {
        StorageStrategyRouter r = router("local", "", "", "", "");

        assertThat(r.routeForRead("minio")).isSameAs(minio);
        assertThat(r.routeForRead("MINIO ")).isSameAs(minio); // 大小写/空白归一
    }

    @Test
    @DisplayName("行 storage_type 为空 - 拒绝读取并说明，不回退 local")
    void should_refuseRead_when_recordedTypeBlank() {
        StorageStrategyRouter r = router("local", "", "", "", "");

        assertThatThrownBy(() -> r.routeForRead(null))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("storage_type");
        assertThatThrownBy(() -> r.routeForRead("  "))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("storage_type");
    }

    @Test
    @DisplayName("行 storage_type 是不认识的值 - 拒绝并点名该值")
    void should_refuseRead_when_recordedTypeUnsupported() {
        StorageStrategyRouter r = router("local", "", "", "", "");

        assertThatThrownBy(() -> r.routeForRead("sftp"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("sftp");
    }

    @Test
    @DisplayName("行 storage_type=oss（枚举有、策略 bean 没有）- 拒绝读取，不假装能读")
    void should_refuseRead_when_declaredTypeHasNoStrategyBean() {
        StorageStrategyRouter r = router("local", "", "", "", "");

        assertThatThrownBy(() -> r.routeForRead("oss"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("ossStorageStrategy");
    }
}
