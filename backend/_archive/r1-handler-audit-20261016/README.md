# r1-handler-audit · 2026-10-16（阶段 AS6 / 报告 19.28.7）

`count-handlers.cjs` 是 R1 收口时用来点 handler 账的脚本，跑一次即可复现台账数字：

```bash
node backend/_archive/r1-handler-audit-20261016/count-handlers.cjs
```

口径（与 §6.8 / 报告 19.3 一致，换了口径数字就不可比）：

- handler = 方法上的 `@GetMapping` / `@PostMapping` / `@PutMapping` / `@DeleteMapping` / `@PatchMapping`；
- 类级 `@RequestMapping`（写在 `class` 声明之前的那一行）**不计**为 handler；
- covered = 该方法自己有 `@RequiresPermission`，或所在类有类级 `@RequiresPermission`；
- 扫描范围 = `backend/**/...Controller.java`，跳过 `target/`。

2026-10-16 读数：总数 **279** / covered **253** / uncovered **26** / 零注解 controller **7**。
uncovered 的 26 条是刻意不套的三类：登录态自务 **12**（`AuthController` 9 + `TokenController` 3）+ 内部面 **9**（`InternalUserController` 6 / `InternalDatasourceController` 2 / `InternalModelController` 1，已有 X-Internal-Token + 网关 DENY_LIST）+ 公开或外部自证面 **5**（`FileController.view` 1 / `CallbackController` 3 / `OpenApiController` 1），逐个点名见报告 **19.28.7**。

脚本留在归档里而不是进 `scripts/`：它依赖"控制器文件名以 Controller.java 结尾"这类仓库形状，形状一变数字就失真，不适合当常驻门禁。i18n 那条才是常驻门禁（`frontend/scripts/i18n-parity.mjs`）。
