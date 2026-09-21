package com.dataviz.datasource.entity;

/**
 * 数据源类型枚举
 */
public enum DatasourceType {

    MYSQL("mysql", "com.mysql.cj.jdbc.Driver"),
    POSTGRESQL("postgresql", "org.postgresql.Driver"),
    ORACLE("oracle", "oracle.jdbc.OracleDriver"),
    SQLSERVER("sqlserver", "com.microsoft.sqlserver.jdbc.SQLServerDriver"),
    CLICKHOUSE("clickhouse", "com.clickhouse.jdbc.ClickHouseDriver"),
    ELASTICSEARCH("elasticsearch", null),
    MONGODB("mongodb", null),
    API("api", null),
    CSV("csv", null),
    EXCEL("excel", null);

    private final String code;
    private final String driverClass;

    DatasourceType(String code, String driverClass) {
        this.code = code;
        this.driverClass = driverClass;
    }

    public String getCode() {
        return code;
    }

    public String getDriverClass() {
        return driverClass;
    }

    public boolean isJdbcSupported() {
        return this.driverClass != null;
    }

    public static DatasourceType fromCode(String code) {
        for (DatasourceType type : values()) {
            if (type.code.equalsIgnoreCase(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown datasource type: " + code);
    }
}
