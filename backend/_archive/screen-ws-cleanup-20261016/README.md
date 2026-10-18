# screen-ws-cleanup · 2026-10-16（阶段 AS3 大屏 WS 推送）

移走的是两个"看起来在做实时推送、实际什么都没做"的类。替代品在
`screen-service/src/main/java/com/dataviz/screen/websocket/`：
`ScreenSharePushHandler` + `ScreenSharePushConfig` + `ScreenChangeBroadcaster`。

## ScreenWebSocketEndpoint.java

`@ServerEndpoint("/ws/screen/{screenId}")`，但：

1. **它从来没被注册过**。JSR-356 的 `@ServerEndpoint` 在嵌入式 Tomcat 里需要一个
   `ServerEndpointExporter` bean 才会生效，全后端没有这个 bean（`grep -rn ServerEndpointExporter backend --include=*.java`
   只命中它自己）。类上的 `@Component` 只让它成为一个没人用的 Spring bean。
   真正接住 `/ws/**` 握手的是 `common-websocket` 的 `WebSocketConfig` 里那个 `BaseWebSocketHandler`
   （screen-service 侧的实现是 `ScreenWebSocketHandler`，它仍然保留 —— 删了它 `WebSocketConfig` 会因为缺 bean 起不来）。
2. **`broadcastToScreen(screenId, …)` 无视 screenId**：会话表是 `sessionId -> Session` 的平面 Map，
   任何客户端发一条消息，就会被回显给**所有大屏的所有连接**。挂上去那天就是跨屏串消息 + 一个免费的放大通道。
3. `onMessage` 无条件回显客户端原文，等于给匿名访客一个向其他访客发任意文本的通道。

保留的 `ScreenWebSocketHandler`（`/ws/**`，按 userId 建索引）是协同编辑那条路的占位，与本补丁无关。

## ScreenDataPusher.java

```java
@Scheduled(fixedDelay = 5000)
public void refreshAndPushData() {
    log.debug("Refreshing screen data and pushing via WebSocket");
    // TODO: Fetch latest data from data sources
    // TODO: Push updated data to connected WebSocket clients
}
```

每 5 秒打一条 debug 日志，取数和推送两行都是 TODO。它给读代码的人（和读日志的人）传达的是
"大屏数据在定时刷新并推送"，而事实是 screen-service 里没有任何取数链路 ——
组件的实时数据由各渲染器按 `refreshInterval` 自己在浏览器侧轮询
（`frontend/packages/screen-engine/src/components/props.ts`）。

本补丁不冒充它：新通道只推"这张大屏的配置变了/发布了/下架了/删了"这一件事，
数据刷新留在客户端既有那条路上。要真做服务端数据推送，需要先有 screen-service → datasource 的取数依赖，
那是另一个功能，不该由一个空循环占着名字。

## 回退

把这两个文件放回原位即可（`target/classes` 下的 .class 一并放回去，否则下次增量编译前
IDEA 里仍能看到旧类）。注意回退后 `/api/screen/ws/share/*` 这条新通道不受影响 —— 两套互不引用。
