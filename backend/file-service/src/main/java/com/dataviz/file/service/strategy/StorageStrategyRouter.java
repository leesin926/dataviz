package com.dataviz.file.service.strategy;

import com.dataviz.common.core.exception.BizException;
import com.dataviz.common.core.result.ErrorCode;
import com.dataviz.file.enums.StorageType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 存储策略路由器：把"用哪个存储"从写死在 {@code FileServiceImpl} 构造器里的
 * {@code @Qualifier("localStorageStrategy")} 改成配置驱动（{@code file.storage.type}，默认 local）。
 *
 * <p>做法沿用本仓库既有的"按配置在若干候选里选一个 bean"的路子：注入 Spring 的
 * {@code Map<String, StorageStrategy>}（key 即 bean 名），再用 {@link StorageType#strategyBeanName()}
 * 的命名约定把配置值映射到 bean 名。相比再写一个 if/else 工厂，这是 Java 8 + Boot 2.7 里最不出意外的写法，
 * 新增一种存储只需再注册一个 {@code xxxStorageStrategy} bean。</p>
 *
 * <p>三条底线（对齐 {@code SmsNotifier}／{@code LlmClient} 的"没配置就明说，不粉饰成功"）：</p>
 * <ol>
 *   <li><b>写侧启动即校验</b>：类型/策略 bean 在构造阶段解析（空白或不支持的 {@code file.storage.type} 当场抛
 *       {@link IllegalStateException}，不做静默默认）；选了 minio 则 endpoint/bucket/凭据在 {@code @PostConstruct}
 *       与每次 {@code routeForWrite()} 各复核一遍，缺项点名缺哪个 key 后拒绝——起不来 / 上传前就拒，不等落库才炸。</li>
 *   <li><b>读侧按行落库的 storage_type 路由</b>，而不是按"当前配置"。这样日后从 local 切到 minio，
 *       老的 local 文件仍走 local 读，不会被当前配置误导成"去 minio 找一个根本不在那的对象"。
 *       若行里的类型是空/不认识/认识但没接 bean（如 oss）→ {@link BizException} 明确拒绝，
 *       <b>不回退 local</b>（静默回退等于让老数据说谎）。</li>
 *   <li><b>任何日志与异常都不带出密钥值</b>，只报 key 名。</li>
 * </ol>
 */
@Slf4j
@Component
public class StorageStrategyRouter {

    /** 脚手架占位值：等于没配（对齐 LlmClient 对 your-api-key-here 的处理） */
    private static final Set<String> MINIO_PLACEHOLDERS = new LinkedHashSet<>();

    static {
        MINIO_PLACEHOLDERS.add("change-me");
        MINIO_PLACEHOLDERS.add("changeme");
    }

    private final Map<String, StorageStrategy> strategiesByName;

    private final StorageType writeType;
    private final StorageStrategy writeStrategy;
    private final String writeStorageCode;

    private final String minioEndpoint;
    private final String minioBucket;
    private final String minioAccessKey;
    private final String minioSecretKey;

    public StorageStrategyRouter(Map<String, StorageStrategy> strategiesByName,
                                 @Value("${file.storage.type:local}") String configuredType,
                                 @Value("${minio.endpoint:}") String minioEndpoint,
                                 @Value("${minio.bucket:}") String minioBucket,
                                 @Value("${minio.access-key:}") String minioAccessKey,
                                 @Value("${minio.secret-key:}") String minioSecretKey) {
        this.strategiesByName = strategiesByName;
        this.minioEndpoint = minioEndpoint;
        this.minioBucket = minioBucket;
        this.minioAccessKey = minioAccessKey;
        this.minioSecretKey = minioSecretKey;
        this.writeType = resolveWriteType(configuredType);
        this.writeStorageCode = this.writeType.getCode();
        this.writeStrategy = requireStrategyBean(this.writeType);
    }

    /**
     * 启动即校验：选了 minio 就把必填项查一遍，缺任何一项当场让容器初始化失败（fail fast）。
     * <p>没接上对象存储却把服务起起来，等于让第一次上传才炸——那已是线上事故而不是启动噪声。
     * {@link #routeForWrite()} 每次也复核，好让非 Spring 场景（单测）走同一条判据。</p>
     */
    @PostConstruct
    public void validateOnStartup() {
        if (writeType == StorageType.MINIO) {
            requireMinioConfig();
        }
        log.info("文件存储写侧策略已选定: type={}, bean={}", writeStorageCode, writeType.strategyBeanName());
    }

    /** 写侧：本次上传落哪个策略、以及要落库的 storage_type 值；minio 在此复核配置，缺失即拒（先于落库，不产生脏行） */
    public WriteRoute routeForWrite() {
        if (writeType == StorageType.MINIO) {
            requireMinioConfig();
        }
        return new WriteRoute(writeStrategy, writeStorageCode);
    }

    /**
     * 读侧：按行自己记录的 storage_type 定位策略。
     * <p>空值/未知值/未接入的策略一律拒绝，不猜、不回退 local——让老行说真话，比"看起来能用"重要。</p>
     */
    public StorageStrategy routeForRead(String recordedStorageType) {
        if (isBlank(recordedStorageType)) {
            throw new BizException(ErrorCode.SERVICE_UNAVAILABLE,
                    "文件记录缺少 storage_type，无法判定它当初存于何处，拒绝猜测读取（请先修复该行或重新上传）");
        }
        StorageType type = StorageType.fromCode(recordedStorageType);
        if (type == null) {
            throw new BizException(ErrorCode.SERVICE_UNAVAILABLE,
                    "文件记录不支持的存储类型: " + recordedStorageType.trim().toLowerCase()
                            + "（可选: " + StorageType.supportedCodes() + "），拒绝读取");
        }
        StorageStrategy strategy = strategiesByName.get(type.strategyBeanName());
        if (strategy == null) {
            throw new BizException(ErrorCode.SERVICE_UNAVAILABLE,
                    "存储类型 " + type.getCode() + " 已声明但未接入（缺 bean " + type.strategyBeanName() + "），拒绝读取");
        }
        return strategy;
    }

    private StorageType resolveWriteType(String configuredType) {
        if (isBlank(configuredType)) {
            throw new IllegalStateException("file.storage.type 未配置：显式取值应为 " + StorageType.supportedCodes()
                    + "，留空不做静默默认，请设置 file.storage.type=local|minio");
        }
        StorageType type = StorageType.fromCode(configuredType);
        if (type == null) {
            throw new IllegalStateException("file.storage.type 取值不被支持: " + configuredType.trim().toLowerCase()
                    + "（可选: " + StorageType.supportedCodes() + "）");
        }
        return type;
    }

    private StorageStrategy requireStrategyBean(StorageType type) {
        StorageStrategy strategy = strategiesByName.get(type.strategyBeanName());
        if (strategy == null) {
            throw new IllegalStateException("file.storage.type=" + type.getCode()
                    + " 指向的策略未注册：容器中找不到 bean " + type.strategyBeanName());
        }
        return strategy;
    }

    /** 点名缺失的 key，绝不带出凭据值；endpoint/bucket/access-key/secret-key 全缺时一次性列全（对齐 LlmClient.listEveryMissingItem） */
    private void requireMinioConfig() {
        List<String> missing = new ArrayList<>();
        if (isBlank(minioEndpoint) || MINIO_PLACEHOLDERS.contains(trimmedLower(minioEndpoint))) {
            missing.add("minio.endpoint");
        }
        if (isBlank(minioBucket) || MINIO_PLACEHOLDERS.contains(trimmedLower(minioBucket))) {
            missing.add("minio.bucket");
        }
        if (isBlank(minioAccessKey) || MINIO_PLACEHOLDERS.contains(trimmedLower(minioAccessKey))) {
            missing.add("minio.access-key");
        }
        if (isBlank(minioSecretKey) || MINIO_PLACEHOLDERS.contains(trimmedLower(minioSecretKey))) {
            missing.add("minio.secret-key");
        }
        if (!missing.isEmpty()) {
            // 只报 key 名：任何日志与异常文本都不带出 endpoint/凭据值，避免把凭据回显给调用方/日志（对齐 LlmClient 口径）
            throw new IllegalStateException("file.storage.type=minio 但 MinIO 配置不完整，缺失: "
                    + String.join(", ", missing) + "（未接入对象存储前拒绝写入，避免上传静默失败）");
        }
    }

    private static String trimmedLower(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    /** 写侧解析结果：策略实例 + 要落库的 code */
    public static final class WriteRoute {
        private final StorageStrategy strategy;
        private final String storageType;

        WriteRoute(StorageStrategy strategy, String storageType) {
            this.strategy = strategy;
            this.storageType = storageType;
        }

        public StorageStrategy getStrategy() {
            return strategy;
        }

        public String getStorageType() {
            return storageType;
        }
    }
}
