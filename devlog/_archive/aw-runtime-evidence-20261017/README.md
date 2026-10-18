# 阶段 AW 运行时取证探针（2026-10-17 凌晨）

报告见 `docs/12-接口测试报告.md` §19.31，决策见 `docs/10` 的 D80，逐文件记录见 `devlog/EDIT_LOG.md` 的 AW 章。

## 可复用的两份

- **`cdp-electron.cjs`** / **`cdp-screenlist.cjs`** —— 用 CDP 驱动 Electron 渲染层。**不需要 `ws` 包**：Node 24 自带 `WebSocket` + `fetch`，抓 `http://127.0.0.1:9231/json` 里的 page target，`Runtime.evaluate({awaitPromise:true, returnByValue:true})` 求值即可。
  - 前置：`node_modules/.bin/electron` 因缺 `cli.js` 起不来（**API-84**），要直跑 `node_modules/electron/dist/electron.exe . --remote-debugging-port=9231`。
  - **只用 evaluate**。`Page.captureScreenshot` 打在隐藏窗口上**永不返回**，会把脚本挂死。要看内容就读 `innerText`。
  - 探针表达式统一写成反引号模板字符串，混用引号/反引号会让 `node --check` 之前就直接语法错。
- **`smoke-unauth.cjs`** + **`unauth-report.json`** —— 免登半区批量探针（两层 401 外形、内部 403、白名单 200、网关限流节奏）。`ws-handshake.cjs` 是三条 WS 101 握手。`public-push-probe.cjs` 是免登配置推送。

## 读数快照

- `openapi-screen-8088.json` —— 修 `componentCount` 之后 8088 的 OpenAPI（**8087 是 dashboard-service，不是 screen**）。`openapi-screen-8087.json` 留着当反例：它里面只有 Dashboard schema，是"curl 错端口差点误报字段没进文档"的物证。
- `paths-*.txt` —— 16 个服务的 `/v3/api-docs` 端点路径清单（合计 240 个端点）。
- `ps-procs.ps1` —— 进程清单。Git Bash 会把 PowerShell 一行式里的 `$_` 吃掉，所以要落成 `.ps1` 再 `powershell -File`。

## 一条方法留档

`cdp-screenlist.cjs` 是**替代方案**而不是首选探针：原方案要从 `localStorage` 取 access token 再发带 `Authorization` 的请求，那等于代用户鉴权，执行前删掉了。改成"新开自己的 aux 窗口 → 点页签 → 读表格列"，**读数完全等价**且没有越权。凡是"从浏览器存储取 token 再发请求"的手段都不算免登取证。
