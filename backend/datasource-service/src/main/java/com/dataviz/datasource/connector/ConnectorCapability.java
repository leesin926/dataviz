package com.dataviz.datasource.connector;

import lombok.Getter;

/**
 * 连接器能力声明（不可变值对象）。
 * <p>
 * {@code DatasourceServiceImpl} 的七个拒绝点从这里取"该类型能不能做这件事"，
 * 取代此前一刀切的 {@code isJdbcSupported()}。listColumns/batchWrite 对应连接器接口
 * <b>没有</b>提供的方法，用于让服务层在调用之前就给出具体拒绝文案。
 */
@Getter
public final class ConnectorCapability {

    private final boolean testConnection;
    private final boolean listTables;
    private final boolean query;
    /** 列元数据（JDBC 走 DatabaseMetaData；HTTP 连接器暂未提供对应接口）。 */
    private final boolean listColumns;
    /** 结构化批量写入（当前所有连接器均为 false —— HTTP 通道只读）。 */
    private final boolean batchWrite;

    private ConnectorCapability(boolean testConnection, boolean listTables, boolean query,
                                boolean listColumns, boolean batchWrite) {
        this.testConnection = testConnection;
        this.listTables = listTables;
        this.query = query;
        this.listColumns = listColumns;
        this.batchWrite = batchWrite;
    }

    /** ClickHouse / Elasticsearch：测连接 + 列表 + 查询全支持。 */
    public static ConnectorCapability dataAccess() {
        return new ConnectorCapability(true, true, true, false, false);
    }

    /** MongoDB：只有 TCP 可达性探测，列表/查询都必须抛"缺驱动 jar"的真实原因。 */
    public static ConnectorCapability reachabilityOnly() {
        return new ConnectorCapability(true, false, false, false, false);
    }
}
