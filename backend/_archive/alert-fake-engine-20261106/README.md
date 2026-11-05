# AlertCheckEngine（假告警引擎）归档 —— 2026-11-06，任务 #191，缺陷 API-111

**为什么在这里而不是在 `src/main`**：这份 `AlertCheckEngine` 是脚手架时代的产物，`fetchCurrentValue()` 返回
`Math.random() * 100`（第 68 行），`extractThreshold()` 返回硬编码 `80.0`（第 73 行），注释自己写着
`// Simulate condition evaluation - in production this would query actual data source`。
它带 `@Scheduled(fixedDelay = 60000)` 且**不受任何属性门控**，所以在 AA3 把真取数路径
（`service/impl/AlertCheckServiceImpl`，默认 `alert.check.enabled:false`，见 `application.yml` 末段）
交付之后，**跑着的仍然是这一个**：每 60s 把所有 `enabled=1` 的规则用随机数判一遍，
`> 80` 就 `alertEventMapper.insert(...)` 写进 `db_alert.alert_event`，然后调
`NotifyDispatcher.dispatch(rule, event)` **把通知真发出去**（DINGTALK/WEBHOOK 是真实 HTTP 请求）。
症状是"告警莫名触发、值每次都不一样、阈值永远是 80"，而台账里 AA3 写的是"真取数"。

**退场判据**（不是"看着没人用"）：
- `grep -rn "AlertCheckEngine" backend --include=*.java` 除自身外 **0 命中** ⇒ 没有任何注入点，删除不改变任何调用面。
- 真路径已覆盖它的每一条语义：取数走 `AlertEvaluator` → `DatasourceQueryClient.queryScalar`；
  越界要连续够 `duration`（`alert:breach:<ruleId>`）；同规则还有 `PENDING` 事件时抑制重复；
  单条规则异常只记日志不影响其余。这三条假引擎都没有。

**留下来的东西**：`src/test/java/.../AlertCheckEngineRetirementTest.java` 用类路径扫描钉住
"alert-service 里带 `@Scheduled` 的方法只有 `AlertCheckServiceImpl.checkAllRules` 一个，且该类带
`alert.check.enabled` 门控"。以后谁再加一个不分开关的调度器，这条测试会红。

**没拿到的一条读数**：本轮开工时 Docker 已停（`dataviz-mysql` 不可达），所以
"`alert_event` 里 `message LIKE 'Alert: %exceeded threshold%'` 的行数与最后一条时间"
这条运行时正证**没有取到**，探针已写在 `devlog/_be/be1-events.sql`，栈起来后跑它即可；
本轮的结论建立在源码 + 零引用 + **一条已入账的运行时正证**：docs/12 §19.31（阶段 AW 运行时取证轮，该行原文）
在 alert-service 活体日志里抓到栈
`OutboundUrlGuard.reject(:89) → requireAllowed(:52) → WebhookNotifier.urlOf(:101) → NotifyDispatcher.dispatch(:71) → AlertCheckEngine.evaluateRule(:62)`
—— 也就是说**假引擎的 `evaluateRule` 当时真的在进程内调用派发链**（那次是被 SSRF 闸门拦下才没发出去）。
