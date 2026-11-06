package com.dataviz.datasource.connector;

/**
 * TCP 可达性探测接缝：单测 mock 本接口即可验证 MongoConnector 的行为，无需真实端口。
 */
public interface TcpProbe {

    /** 在 timeoutMillis 内能建立 TCP 连接返回 true；任何 IO 失败（拒绝/超时/未知主机）返回 false。 */
    boolean reachable(String host, int port, int timeoutMillis);
}
