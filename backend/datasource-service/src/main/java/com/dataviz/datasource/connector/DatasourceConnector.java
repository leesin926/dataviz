package com.dataviz.datasource.connector;

import com.dataviz.common.core.client.DatasourceQueryResult;
import com.dataviz.datasource.entity.DatasourceType;

import java.util.List;

/**
 * 非 JDBC 数据源连接器：把"能不能测连接 / 能不能列表 / 能不能查询"从
 * {@link DatasourceType#isJdbcSupported()} 的二分法里解耦出来。
 * <p>
 * 路由约定（见 {@code DatasourceServiceImpl}）：某类型只要有连接器注册，就<b>优先走连接器</b>，
 * JDBC 路径保留给 mysql/postgresql/oracle/sqlserver（行为不变）；clickhouse 虽然枚举里仍声明
 * JDBC 驱动，但离线构建产物中没有 clickhouse-jdbc jar，HTTP 连接器是它当前唯一可用的通道。
 * <p>
 * 实现必须遵守三条底线：
 * <ol>
 *   <li>凭据只走请求头（Authorization/ApiKey），绝不拼进 URL、请求体或异常文案；</li>
 *   <li>结果复用 {@link DatasourceQueryResult}（即 /api/datasource/internal/query 的既有响应形状），
 *       不另造平行 DTO；</li>
 *   <li>做不到的事要抛出说明真实原因的业务异常，绝不谎报成功（参照 alert-service 的
 *       SmsNotifier.deliverable()=false 处理）。</li>
 * </ol>
 */
public interface DatasourceConnector {

    /** 本连接器是否负责该类型（路由的第一判据）。 */
    boolean supports(DatasourceType type);

    /** 能力声明：连接测试 / 列表 / 查询（以及连接器接口未覆盖的列信息、批量写入）。 */
    ConnectorCapability capability();

    /**
     * 连通性测试。实现要么返回真实探测结果，要么抛 BizException 说明具体失败原因；
     * 返回值不代表凭据已验证（见 {@link MongoConnector} 的 TCP-only 语义）。
     */
    boolean testConnection(String configJson);

    /** 表/集合/索引名列表。能力缺失时必须抛说明真实原因的业务异常。 */
    List<String> listTables(String configJson);

    /**
     * 只读查询。
     *
     * @param maxRows        行数上限（超出即截断并置 truncated=true，与 JDBC 路径同语义）
     * @param timeoutSeconds 服务端/传输超时（秒），连接器各自决定如何透传
     */
    DatasourceQueryResult query(String configJson, String sql, int maxRows, int timeoutSeconds);
}
