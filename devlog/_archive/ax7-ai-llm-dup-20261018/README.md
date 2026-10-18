# AX7：ai-service 里那份从未被调用的 LlmClient

归档时间：2026-10-18。原路径：`backend/ai-service/src/main/java/com/dataviz/ai/client/LlmClient.java`（`git log` 追到 init 提交，即项目初始就在）。

## 为什么换掉

它是**死 bean**：全模块 grep 只有它自己的类声明命中，没有任何注入点（`ChatServiceImpl` / `Nl2SqlServiceImpl` / `InsightServiceImpl` 都不引用它）。所以"AI 接口返回占位文案"的根因不是"没有客户端"，而是**有客户端、没人调用**。台账原先若写成"没有大模型链路"就是过度声明，本轮按这条更正。

保留它会让新链路继承四个真实缺陷：

| 缺陷 | 后果 |
|------|------|
| 失败时 `throw new RuntimeException` | 调用方无法区分"没配置"和"配了但调不通"，只能整体 500 |
| 无超时设置（WebClient 裸建） | 大模型端点是十秒量级，挂住即占线程 |
| 不校验 `api-key` 是否为占位值 | 会把 `Bearer your-api-key-here` 真发到公网端点 |
| 只支持单轮 system+user | 助手会话要带历史，接不上 |

替代品：`com.dataviz.ai.llm.LlmClient`（返回 `LlmReply{available,text,reason}`，不抛异常）+ `LlmReply` + `SqlGuard` + `schema/DatasourceSchemaReader`。

## 回滚

`cp LlmClient.java ../backend/ai-service/src/main/java/com/dataviz/ai/client/`（需同时重建 `client` 目录）。或直接 `git checkout -- backend/ai-service/src/main/java/com/dataviz/ai/client/LlmClient.java`。
