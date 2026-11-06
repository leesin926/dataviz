package com.dataviz.datasource.connector;

import com.dataviz.common.core.client.DatasourceQueryResult;
import com.dataviz.common.core.exception.BizException;
import com.dataviz.common.core.result.ErrorCode;
import com.dataviz.datasource.entity.DatasourceType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * MongoDB 连接器的诚实边界：只有 TCP 可达性，列表/查询必须抛「缺驱动 jar」的真实原因。
 * <p>
 * 判据参照 alert-service 的 SmsNotifier.deliverable()=false —— 能力缺失要明说缺什么、
 * 要补什么（org.mongodb:mongodb-driver-sync），而不是谎报成功或回空集。空集会被上层读成
 * "没有数据"，那是漏报，比报错难发现得多。
 */
class MongoConnectorTest {

    private static final String CONFIG =
            "{\"host\":\"mongo.internal\",\"port\":27017,\"username\":\"admin\",\"password\":\"admin123456\"}";

    private final TcpProbe tcpProbe = mock(TcpProbe.class);
    private MongoConnector connector;

    @BeforeEach
    void setUp() {
        connector = new MongoConnector(new ObjectMapper(), tcpProbe);
    }

    @Test
    @DisplayName("supports - 只认领 MONGODB，且能力位如实标为『仅可达性』")
    void should_supportOnlyMongoDb_when_typeChecked() {
        assertThat(connector.supports(DatasourceType.MONGODB)).isTrue();
        assertThat(connector.supports(DatasourceType.CLICKHOUSE)).isFalse();
        assertThat(connector.supports(DatasourceType.ELASTICSEARCH)).isFalse();

        ConnectorCapability capability = connector.capability();
        assertThat(capability.isTestConnection()).isTrue();
        assertThat(capability.isListTables()).isFalse();
        assertThat(capability.isQuery()).isFalse();
    }

    @Test
    @DisplayName("测连接 - 端口可达即返回 true（语义仅 TCP，不含认证验证）")
    void should_returnTrue_when_tcpPortReachable() {
        // Given
        when(tcpProbe.reachable(eq("mongo.internal"), eq(27017), eq(2000))).thenReturn(true);

        // When / Then
        assertThat(connector.testConnection(CONFIG)).isTrue();
        verify(tcpProbe).reachable("mongo.internal", 27017, 2000);
    }

    @Test
    @DisplayName("测连接 - 端口不可达返回 false：绝不把探测失败粉饰成成功")
    void should_returnFalse_when_tcpPortUnreachable() {
        // Given
        when(tcpProbe.reachable(anyString(), eq(27017), eq(2000))).thenReturn(false);

        // When / Then
        assertThat(connector.testConnection(CONFIG)).isFalse();
    }

    @Test
    @DisplayName("测连接 - 未配 port 时用 27017 缺省端口探测")
    void should_useDefaultPort_when_configOmitsPort() {
        // Given
        when(tcpProbe.reachable(eq("mongo.internal"), eq(27017), eq(2000))).thenReturn(true);

        // When
        boolean ok = connector.testConnection("{\"host\":\"mongo.internal\"}");

        // Then
        assertThat(ok).isTrue();
        verify(tcpProbe).reachable("mongo.internal", 27017, 2000);
    }

    @Test
    @DisplayName("测连接 - host 缺失是点名 host 的 400，而不是拿 null 去连（API-124 同类）")
    void should_throwBadRequestNamingHostKey_when_configHostMissing() {
        assertThatThrownBy(() -> connector.testConnection("{\"port\":27017}"))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.BAD_REQUEST.getCode())
                .hasMessageContaining("缺少 host");

        verifyNoInteractions(tcpProbe);
    }

    @Test
    @DisplayName("查询 - 拒绝并写明真实原因：离线缺 mongodb-driver-sync jar，讲不出 wire protocol")
    void should_refuseQueryWithDriverReason_when_queryRequested() {
        BizException ex = org.junit.jupiter.api.Assertions.assertThrows(BizException.class,
                () -> connector.query(CONFIG, "SELECT 1", 1000, 30));

        assertThat(ex.getCode()).isEqualTo(ErrorCode.BAD_REQUEST.getCode());
        assertThat(ex.getMessage())
                .contains("mongodb-driver-sync")
                .contains("wire protocol")
                .doesNotContain("admin123456");

        // 没发过任何协议报文：探测也没被顺手假装成"查询成功"
        verifyNoInteractions(tcpProbe);
    }

    @Test
    @DisplayName("列表 - 同样拒绝并点名要补的依赖，不返回空集冒充『没有集合』")
    void should_refuseListTablesWithDriverReason_when_listRequested() {
        assertThatThrownBy(() -> connector.listTables(CONFIG))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("MongoDB 不支持获取集合列表")
                .hasMessageContaining("mongodb-driver-sync");

        verifyNoInteractions(tcpProbe);
    }

    @Test
    @DisplayName("结果形状 - 连接器接口复用内部查询端点 DTO，不另造平行类型")
    void should_reuseInternalQueryResult_when_declaringContract() {
        // Given/When/Then：签名级锁定 —— DatasourceConnector.query 的返回类型就是
        // DatasourceQueryResult（/api/datasource/internal/query 的既有响应形状），防止后来者造平行 DTO
        Class<?> queryReturn;
        try {
            queryReturn = DatasourceConnector.class
                    .getMethod("query", String.class, String.class, int.class, int.class)
                    .getReturnType();
        } catch (NoSuchMethodException e) {
            throw new AssertionError("连接器 query 签名漂移: " + e.getMessage(), e);
        }
        assertThat(queryReturn).isEqualTo(DatasourceQueryResult.class);
    }
}
