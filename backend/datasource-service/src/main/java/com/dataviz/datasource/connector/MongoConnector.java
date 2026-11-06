package com.dataviz.datasource.connector;

import com.dataviz.common.core.client.DatasourceQueryResult;
import com.dataviz.common.core.exception.BizException;
import com.dataviz.common.core.result.ErrorCode;
import com.dataviz.datasource.entity.DatasourceType;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * MongoDB 连接器 —— <b>只做可达性，且只声称可达性</b>。
 * <p>
 * MongoDB 是 wire protocol（27017），没有 HTTP 接口；要讲这个协议必须有驱动。当前 Maven 离线仓库里
 * 没有任何 {@code mongodb-driver-*} jar（只有元数据，{@code mvn dependency:get} 也拉不到），
 * 因此平台<b>说不出</b> {@code isMaster}/OP_MSG，列表与取数在物理上不可行。
 * <p>
 * 诚实边界（对齐 alert-service 里 SmsNotifier.deliverable()=false 的处理）：
 * <ul>
 *   <li>{@link #testConnection} 是一次带超时的 TCP connect：返回 true 仅说明端口有人监听，
 *       <b>不</b>说明 admin/admin123456 能通过认证 —— 认证本身需要 wire protocol，我们讲不出。
 *       日志也只记 host:port，绝不记凭据。</li>
 *   <li>{@link #listTables}/{@link #query} 一律抛 {@code BizException(BAD_REQUEST)} 并写明真实原因：
 *       缺 {@code org.mongodb:mongodb-driver-sync}（连带 mongodb-driver-core、bson）jar；
 *       而不是谎报成功或返回空集 —— 空集会被上层读成"没有数据"，那是漏报，比报错更难发现。</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MongoConnector implements DatasourceConnector {

    static final int DEFAULT_PORT = 27017;
    static final int CONNECT_TIMEOUT_MILLIS = 2_000;

    /** 拒绝文案的唯一来源：单测与线上异常共用，避免两处漂移。 */
    static final String NO_DRIVER_REASON =
            "当前构建离线且 classpath 上没有任何 MongoDB 驱动 jar（本地 .m2 只有 pom 无 jar，"
                    + "mvn dependency:get 不可用），平台无法讲 MongoDB wire protocol（OP_MSG/isMaster），"
                    + "需要引入 org.mongodb:mongodb-driver-sync（连带 mongodb-driver-core、bson）才能支持"
                    + "集合列表与取数；在此之前本类型仅支持 TCP 连通性测试（且不代表凭据已验证）";

    private final ObjectMapper objectMapper;
    private final TcpProbe tcpProbe;

    @Override
    public boolean supports(DatasourceType type) {
        return type == DatasourceType.MONGODB;
    }

    @Override
    public ConnectorCapability capability() {
        return ConnectorCapability.reachabilityOnly();
    }

    @Override
    public boolean testConnection(String configJson) {
        ConnectorConfig config = ConnectorConfig.parse(objectMapper, configJson, DEFAULT_PORT, "MongoDB");
        // 仅 TCP connect：不发送任何 wire protocol 报文，因此绝不声称认证已通过
        boolean reachable = tcpProbe.reachable(config.getHost(), config.getPort(), CONNECT_TIMEOUT_MILLIS);
        log.info("MongoDB 可达性探测（仅 TCP，未验证凭据）: {}:{} -> {}",
                config.getHost(), config.getPort(), reachable ? "端口可达" : "端口不可达");
        return reachable;
    }

    @Override
    public List<String> listTables(String configJson) {
        throw new BizException(ErrorCode.BAD_REQUEST, "MongoDB 不支持获取集合列表：" + NO_DRIVER_REASON);
    }

    @Override
    public DatasourceQueryResult query(String configJson, String sql, int maxRows, int timeoutSeconds) {
        throw new BizException(ErrorCode.BAD_REQUEST, "MongoDB 不支持查询：" + NO_DRIVER_REASON);
    }
}
