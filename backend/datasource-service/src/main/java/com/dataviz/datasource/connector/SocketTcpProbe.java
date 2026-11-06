package com.dataviz.datasource.connector;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * {@link TcpProbe} 的生产实现：JDK {@link Socket} 直连，超时即失败，不抛异常。
 */
@Component
public class SocketTcpProbe implements TcpProbe {

    @Override
    public boolean reachable(String host, int port, int timeoutMillis) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMillis);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
