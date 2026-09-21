# 开发修改记录（自动 hook 提示落盘）

> PostToolUse hook 要求：每次 Edit/Write 后按 `- [时间] `path:范围` — 摘要` 追加一行，必要时补整体逻辑说明。
> 与 `docs/10-移动端大屏重构进度表.md` 的分工：本文件是**逐文件的机械流水**（便于回查"这个文件被谁在哪一批动过"），进度表是**带决策编号的阶段性叙述**。两者都要写，不以其一代其二。
>
> ⚠️ 本文件创建于 2026-09-22（此前 hook 一直提醒但目录不存在）。**更早的批次没有逐文件流水**，其完整记录在 `docs/10` 第五章「变更记录」与 `docs/12-接口测试报告.md` 各章，按批次回查请用那两份。

---

## 2026-09-22 · 横切阶段 AA（告警真取数 + 真通知，任务 #67~#69）

- [2026-09-22] `backend/common/common-security/.../interceptor/InternalApiInterceptor.java` — **新建**（D42）：`X-Internal-Token` 校验 + `MessageDigest.isEqual` 定长比较；`internal.api.token` 未配置 ⇒ **fail-closed 全拒**；配置缺失的 error 日志只在首次真实调用打一次
- [2026-09-22] `backend/common/common-security/.../config/InternalApiWebConfig.java` — **新建**：把上面的拦截器注册到 `/**/internal/**`（约定路径，任何依赖 common-security 的服务自动继承）
- [2026-09-22] `backend/common/common-security/.../config/SecurityConfig.java` — EXCLUDE_PATHS 补 `/user/internal/**`（StripPrefix 后无 `/api`）与 `/api/datasource/internal/**`（D38 两套前缀口径）
- [2026-09-22] `backend/gateway-service/.../filter/AuthGlobalFilter.java` — `DENY_LIST` 由硬编码单条改为通配 `/**/internal/**`；**修编译错**：javadoc 里 `{@code /**/internal/**}` 的 `*/` 提前闭合块注释 ⇒ `<identifier> expected`（注释中不得出现该字面量）
- [2026-09-22] `backend/datasource-service/.../controller/InternalDatasourceController.java` — **新建** `POST /api/datasource/internal/query`：复用既有真执行器 `DatasourceServiceImpl.executeQuery`，服务端钳制 `maxRows≤5000` / `timeout≤30s`
- [2026-09-22] `backend/datasource-service/src/main/resources/application.yml` — 加 `internal.api.token`
- [2026-09-22] `backend/_archive/user-service-internal-interceptor-20260921/{src,target-classes}/` — user-service 本地那份拦截器 + WebConfig **及其 2 个 `.class`** 移入（D40，非 git 工作区不 `rm`）
- [2026-09-22] `backend/alert-service/src/main/resources/application.yml` — 加 `datasource-service.base-url`、`internal.api.token`、`alert.check.enabled`（默认 **false**）
- [2026-09-22] `backend/alert-service/.../config/RestClientConfig.java` — **新建**：查询模板 3s/35s + `@Bean("notifyRestTemplate")` 3s/10s（挂死的 webhook 不许拖整轮扫描）
- [2026-09-22] `backend/alert-service/.../client/DatasourceQueryClient.java` — **新建**：`queryScalar` 首行首列 → BigDecimal（`null`=无结果行≠未越界）；body `code!=200` 透传同码；403 点名"内部口令不被数据源服务接受"；其余 → 503。后补 `@Qualifier("restTemplate")` 消双 bean 歧义
- [2026-09-22] `backend/alert-service/.../engine/AlertEvaluator.java` — **新建**（D43）：只实现 `THRESHOLD`；未知 `condition` **抛错**（default false = 静默漏报）；DERIVATIVE/COMPOSITE 显式拒绝
- [2026-09-22] `backend/alert-service/.../engine/notifier/{AlertNotifier,NotifierSupport,WebhookNotifier,DingTalkNotifier,EmailNotifier,SmsNotifier}.java` — **新建/重写**（D44）：钉钉**解析 body errcode**（失败也返 HTTP 200）、留痕打码 `access_token`；Email/SMS 无依赖 ⇒ 抛 503 记 FAILED 不伪称已发送；模板**不含 `metric_expression`**
- [2026-09-22] `backend/alert-service/.../engine/NotifyDispatcher.java` — **重写**：旧版 `split(",")` 解析 JSON 数组字段且**从未被调用**（死代码）⇒ 新版按 JSON 数组、逐渠道查启用中的记录、每渠道独立 try/catch 并写 `alert_notify_log`
- [2026-09-22] `backend/alert-service/.../service/impl/AlertNotifyServiceImpl.java` — **重写**：委托 dispatcher + **真正持久化 `notified_at`**（旧版 set 完从不 save、收件人硬编码）
- [2026-09-22] `backend/alert-service/.../service/impl/AlertCheckServiceImpl.java` — **重写**：定时闸 → 判定 → 未达 `duration` 只记日志 → Redis `alert:breach:{id}` 持续判定 → `PENDING` 抑制 → 才建事件；每规则独立 try/catch
- [2026-09-22] `backend/alert-service/.../service/AlertRuleService.java:25` — `testRule` 返回类型 `boolean` → `AlertRuleTestVO`（补 import）
- [2026-09-22] `backend/alert-service/.../service/impl/AlertRuleServiceImpl.java` — 注入 `AlertEvaluator`；**`testRule` 从恒 `return true` 的假绿改为真试跑**（不建事件、不派通知；`duration>0` 时回填 note 说明"试跑不评估持续时长"）
- [2026-09-22] `backend/alert-service/.../vo/AlertRuleTestVO.java` — **新建**：`{value, triggered, note}`
- [2026-09-22] `backend/alert-service/.../controller/AlertRuleController.java:64` — `R<Boolean>` → `R<AlertRuleTestVO>`
- [2026-09-22] `frontend/packages/api-client/src/modules/alert.ts:99` — `testAlertRule` 契约同步为 `AlertRuleTestResult`（grep 确认**全仓库零调用方** ⇒ 属安全的契约收紧；`tsc --noEmit -p packages/api-client` 退出码 0）
- [2026-09-22] `deploy/sql/patch/2026-09-22-alert-live-demo.sql` — **新建（未执行）**：AA5 演示数据修正六步（数据源 host + 口令写进 **config JSON**、5 条裸指标规则停用、钉钉渠道停用、新增 WEBHOOK 911、插入 3 条真 SQL 规则 911/912/913、把演示 `PENDING` 事件改 ACKNOWLEDGED）+ 四段校验 + 回退段。**已在 `START TRANSACTION … ROLLBACK` 内 dry-run 通过**
- [2026-09-22] `deploy/dev-tools/alert-webhook-sink.mjs` — **新建**：本地 18099 收件口，验证通知派发零外部副作用
- [2026-09-22] `docs/10-移动端大屏重构进度表.md` — 新增决策 **D42/D43/D44**；新增**横切阶段 AA**（AA1~AA5 行）；已知风险 +3 行（口令默认值、SSRF 面、"AA 只有编译证据"）；变更记录 +1 行；关键文件 +1 条并修正 RBAC 条里已归档的 `InternalApiInterceptor` 路径；**6.3 alert 行改判为已修 + 追加"我先前说错"的更正段**；6.7 第 2/3 条同步更正
- [2026-09-22] `docs/12-接口测试报告.md` — 新增**第十五章**（15.1 归因更正 / 15.2 防护下沉 / 15.3 取数判定 / 15.4 通知留痕 / 15.5 演示数据三坑 / 15.6 重启后验收 6 步 / 15.7 新增缺陷 **API-18/19/20**）；第一章缺陷统计追加 3 条

**整体逻辑/目的**：把 alert 模块从"规则能建、永不触发、永不通知"改成真链路。三个关键取舍：①**不重造 SQL 执行底座** —— datasource-service 的 `executeQuery` 一直是真实现，只补一个内部只读端点做接线（顺带更正上一轮"三个模块共用一块缺失底座"的错误归因）；②**跨服务口径延续 D41** —— 取数失败/口令被拒一律 503，"查到但无结果行"是**无法判定**而不是"未越界"，未知 `condition` 抛错而不是 default false，因为**漏报比误报更难发现**；③**失败必须诚实** —— 发不出去就记 FAILED，绝不写"已发送"（钉钉失败也返 HTTP 200 是这类假绿的典型形态）。对外副作用一律默认关（`alert.check.enabled=false`、钉钉渠道停用），验证靠本地收件口 + `alert_notify_log` 留痕，**不需要登录态即可复核**。

**证据边界**：本轮只有编译证据（`mvn -o -q -pl common/common-security,datasource-service,alert-service,gateway-service -am compile` 退出码 0）+ SQL 事务内 dry-run + 组件扫描面静态核对（alert/datasource/user/auth 四个 `@ComponentScan` 都含 `com.dataviz.common` ⇒ 共享内部拦截器确会被注册；全后端仅 2 个 `/internal` 控制器、前端零引用 ⇒ 网关通配拒绝不误伤）。**8083/8090 仍跑 09-21 13:41 的旧包 ⇒ 新链路零运行时验证**，进度表状态列已逐条标"运行时未验"。

---

## 2026-09-22 · AA5 补丁经用户批准后**真提交执行**

- [2026-09-22] `db_datasource.datasource` — 901/902 的 `config` 改为 `host=localhost` **且把 password 写进 config JSON**（连接工厂不看表的 `username`/`password` 列），两列同步更新保持一致
- [2026-09-22] `db_alert.alert_rule` — 901~905 `enabled=0`（**不删行**）；**新增 911/912/913 三条真 SQL 规则**（分别覆盖立即触发 / `LTE` 边界 + `duration=120` 持续时长 / 恒不触发）
- [2026-09-22] `db_alert.notify_channel` — 902 钉钉 `enabled=0`（假 token 但 `oapi.dingtalk.com` 是真域名，防第三方副作用）；**新增 911 WEBHOOK** → `http://127.0.0.1:18099/hook`
- [2026-09-22] `db_alert.alert_event` — 901~905 上的 214 行 `PENDING` 改 `ACKNOWLEDGED`（只改 status 不删行）⇒ **拆掉"抑制恒真"这个会把验证结论带偏的坑**
- **执行后取证（新开会话，证明确已提交而非事务内假象）**：`enabled_new_rules=3`、`webhook_ch=1`、`old_enabled=0`、`ding_enabled=0`、`pending_demo=0`，901 config 落库为 `localhost` + 带 `password`

**目的**：让 #70 的运行时实测有可跑的数据。补丁的 dry-run 与真提交之间隔了一次用户批准 —— 数据写库属"改动共享状态"，不代做决定。
---

## 2026-09-22 · 横切阶段 AB：OLAP 真执行（任务 #71~#74，决策 D45/D46，报告第十六章）

**本批逐文件（改了什么 + 为什么）**

- [2026-09-22] `backend/common/common-core/src/main/java/com/dataviz/common/core/client/DatasourceQueryResult.java` — **新建**：`{columns, rows, rowCount, executionTime, sql}`，集合字段给空集合默认值（调用方不必判 null）
- [2026-09-22] `backend/common/common-core/src/main/java/com/dataviz/common/core/client/DatasourceQueryClient.java` — **新建（D45）**：`@Component` + **`RestTemplateBuilder` 自建模板**（不注入共享 bean）；`query(datasourceId, sql, maxRows, timeoutSeconds)` 打 `POST /api/datasource/internal/query` 带 `X-Internal-Token`；`queryScalar` 无结果行返回 `null`（= 无法判定）、非数值列 400；`translate()` **把错误体解析回原始 code**（403 点名"内部口令不被数据源服务接受"，transport/5xx 才 503）；`base-url`/`token` 的 `@Value` **一律带默认值**（该组件会被 16 个服务实例化）
- [2026-09-22] `backend/alert-service/.../client/DatasourceQueryClient.java` — **归档**（D40）：连同 `target/classes` 的 `.class` 移入 `backend/_archive/alert-datasource-client-20260922/{src,target-classes}/`。不归档就是两个同类型 `@Component` ⇒ 按类型注入歧义、启动即炸
- [2026-09-22] `backend/alert-service/.../engine/AlertEvaluator.java` — 仅改 import 到 `com.dataviz.common.core.client.DatasourceQueryClient`
- [2026-09-22] `backend/alert-service/.../config/RestClientConfig.java` — **重写为只剩 `@Bean("notifyRestTemplate")`**（3s/10s），注释指向 common-core 客户端；查询侧超时归客户端自己
- [2026-09-22] `backend/model-service/.../vo/DatasetMetaVO.java` — **新建**：`datasetId/tenantId/name/datasourceId/tableName/sqlQuery/dimensions/metrics`，**刻意不含任何凭证字段**
- [2026-09-22] `backend/model-service/.../controller/InternalModelController.java` — **新建（AB1）**：`GET /api/model/internal/dataset/{id}` → `R<DatasetMetaVO>`；查不到返回 `data:null` 而非 404（D41）；javadoc 点名该路由**无 StripPrefix** ⇒ 免登前缀要带 `/api`（D38）
- [2026-09-22] `backend/model-service/.../service/DatasetService.java` — 加 `DatasetMetaVO getDatasetMeta(Long id)`，注释说明"返回 null 而不是抛 404"的理由（内部调用方要能区分"记录不存在"与"调不通"）
- [2026-09-22] `backend/model-service/.../service/impl/DatasetServiceImpl.java` — **AB1 + AB4**：字段 `JdbcTemplate` → `DatasourceQueryClient`；新增 `PREVIEW_DEFAULT_ROWS=100`/`PREVIEW_MAX_ROWS=1000`/`TABLE_IDENTIFIER` 正则；`getDatasetMeta` 走 `BeanUtils.copyProperties` + 显式补 `datasetId`；`previewDataset` **改为按 `dataset.datasource_id` 经内部接口执行**，未绑定数据源直接 400（不再悄悄用本服务的 db_model 连接）；`buildPreviewSql` 双分支（`sqlQuery` 只收单条 SELECT / `tableName` 过标识符白名单）
- [2026-09-22] `backend/common/common-security/.../config/SecurityConfig.java` — `EXCLUDE_PATHS` 补 `/api/model/internal/**`（与 `/user/internal/**`、`/api/datasource/internal/**` 并列，前缀差异同 D38）
- [2026-09-22] `backend/model-service/src/main/resources/application.yml` — 补 `internal.api.token: ${INTERNAL_API_TOKEN:dataviz-internal-dev-token}` + fail-closed 说明注释
- [2026-09-22] `backend/analysis-service/.../client/dto/DatasetMeta.java` — **新建**：本地镜像类（服务间只走 HTTP，不依赖对方 jar）
- [2026-09-22] `backend/analysis-service/.../client/ModelServiceClient.java` — **新建（AB3 前置）**：自建 `RestTemplate`（3s/10s）+ `X-Internal-Token` 打 model 内部端点；`data:null` 原样返回（= 不存在），transport/5xx → `BizException(SERVICE_UNAVAILABLE)`（D41）
- [2026-09-22] `backend/analysis-service/.../engine/SqlBuilder.java` — **重写（AB3 / D46）**：签名改 `buildSql(dto, meta)`；`FROM` 由元数据决定（合法表名 或 单条 SELECT 包成 `( … ) t_source`），**不再臆造 `dataset_{id}`**；操作符/聚合/排序方向/标识符/字面量**五处白名单，未知即 400**（因为 SQL 是字符串过 HTTP、没有参数绑定）；`escapeString` 拒 >1024 与控制字符、先双写反斜杠再双写单引号；LIKE 另转义 `%`/`_`；恒定追加 `LIMIT`（默认 1000、上限 5000）
- [2026-09-22] `backend/analysis-service/.../engine/QueryEngine.java` — **重写（AB3）**：`execute()` 流水线 = 取元数据（null→404、无 datasourceId→400、跨租户→403）→ 建 SQL → 权限改写 → **按最终 SQL 查缓存**（key `query:cache:{tenant}:{user}:{sha256}`，TTL 默认 60s、`≤0` 关闭，**读写失败只 warn 继续查库**）→ `datasourceQueryClient.query()` 真执行 → **如实回填 columns/rows/rowCount/sql/executionTime** → 写缓存；删除旧 stub（`rows=new ArrayList<>(); rowCount=0` 与 `// In production…`）
- [2026-09-22] `backend/analysis-service/src/main/resources/application.yml` — 补 `model-service.base-url`、`datasource-service.base-url`、`datasource-service.query-read-timeout-seconds: 35`、`internal.api.token`、`analysis.query-cache.ttl-seconds: ${ANALYSIS_QUERY_CACHE_TTL:60}`（注释点名"改之前缓存读了就扔"）
- [2026-09-22] `docs/10-移动端大屏重构进度表.md` — 新增决策 **D45/D46**；新增**横切阶段 AB** 表（AB1~AB5）；§6.3 analysis 行划掉改判 + etl 行展开 + 新增"model 数据集预览串库"行；§6.7 第 3 条标"OLAP 半边已完成"；风险表 OLAP 一行换成 4 行（含 **API-23** 静默截断、ETL 契约分叉）并把"只有编译证据"风险扩到 AA/AB（点名 8083/8085/8086/8090 + **16.4 的路由判据**）；关键文件 +1 条（AB 全量文件与归档路径）；**第五章变更记录追加 AB 行，并修掉 AA 行末尾被拼接进来的 Z 验收行副本**（实测 `node` 逐字符比对确认与第 336 行 body 完全相等，多 cut 1547 字符）
- [2026-09-22] `docs/12-接口测试报告.md` — 新增**第十六章**（16.0 交付一览 / 16.1 API-10 取证 / 16.2 D45 落点理由 / 16.3 假绿→真执行 + 注入收口 / **16.4 "串库为什么看不出来"** / 16.5 ETL 三套契约与伪成功记账 / 16.6 验收步骤分"我可代跑"与"必须你跑" / 16.7 新增缺陷 **API-21~API-24**）；第九章 API-4、API-10 状态改写并**当场更正我上轮两处说错**（pom 结论错、export 并非遗留）；第一章统计追加 AB 四条 + 更正 API-18 已执行

**整体逻辑/目的**：把"自助分析"从**看起来正常地返回空**改成真执行，并且**先把注入面收口再通电** —— 旧实现因为根本不执行，所以拼串看起来无害；一旦接上真执行，同一批拼串代码就是可注入的 SQL 通道，所以白名单与真执行必须同批落地，不能拆成"下轮再加防护"。三个取舍：①**取证先于动手**（`model_dataset` 4 行 vs `dataset` 0 行 + `DatasetMapper` 绑定关系，决定元数据从哪读，否则会改错两张表）；②**共享客户端落 `common-core`** 而不是新模块或第三份副本（D45：零 pom 改动、零 IDEA reimport、避免第三次复制 D41 语义）；③**不臆造 ETL 契约**（AB5 停在等决策，因为三种"真执行"形态互斥且要动写权限面）。同时把两条"看不见的好消息"如实登记为缺陷：`PermissionFilter` 一旦真配上规则就会把 SQL 拼到 `LIMIT` 之后（API-21），以及 5000 行静默截断连导出一起中招（API-23）。

**证据边界（重要）**：`mvn -o -q compile`（整仓库）**退出码 0** —— 这是**唯一**的通过证据。8083/8085/8086/8090/8080 全部仍是 09-21 13:41 的旧包 ⇒ **AB1~AB4 运行时零验证**。DB 侧取证为真（`COUNT(*)`、`SUM()`、`SHOW COLUMNS`、`VIEW_DEFINITION`、`JSON_LENGTH(dag_json)` 均实测），源码侧取证为真（`Dataset.java` 零引用、`perm:row:`/`perm:col:` 零写入方、`readFromSource()` 硬编码 1000L 均逐条读过）。**特别登记一条方法论**：`db_model.demo_*` 基表与 `db_analysis` 三个视图（**我自己在 Y1 建的透传视图**）行数与聚合值完全相同 ⇒ 在这一块"数字对得上"**不构成路由正确的证据**，验收必须走 16.6 A4 的结构差异判据（`model_dataset` 只在 `db_model`，打 ds 901 必须失败）。

**本批追加（写报告时又回改的三处，补记）**

- [2026-09-22] `docs/12-接口测试报告.md` — 16.5 表第 2 行的"证据"列原写 `grep -rn "executeTask\|DagExecutor"`，**管道符把 Markdown 表格打断成 5 列**（用脚本逐行校列数才发现）⇒ 改成中文描述；同时把 API-4 行里我上轮说错的两句当场更正（"必须动 pom"错 —— 落 `common-core` 零 pom 改动；"导出只有表头是遗留项"错 —— `ExportController:42/107` 调的就是同一个 `queryEngine.execute()`，随 AB3 一并修好，但同吃 5000 行上限）；16.3 追加第 5 条把这件事写实
- [2026-09-22] `docs/10-移动端大屏重构进度表.md` §6.3 — **修列数**：analysis 与 alert 两行在我上轮改写时把"模块"和"事实"两列合并成了一个单元格（3 列变 2 列，表格结构坏掉）⇒ 拆回三列；etl 行补 `dag_json` 不可达与十类 vs 四类算子的实测细节并点名 **API-24**；预览串库行补 16.4 的"数值等值不构证据"根因；alert 行的"演示数据仍拦路"改为"已由 AA5 补丁解掉（经批准真提交）"
- [2026-09-22] `docs/10` 变更记录 AA 行 — "只欠用户批准真提交"补上**当日已获批准并真提交**的取证结论（避免下轮读到旧的"待批准"）；AB5 阶段行按 16.5 全量重写（`dag_json` 列不可达 + 十类 vs 四类 + **API-24 假绿记账** + "把假绿改成显式 503 不需要等新契约"）；风险表 API-23 行补"导出同口径 ⇒ 导出全量不成立"
- 本轮为核对事实**只读**执行过的取证：`SELECT COUNT(*)`/`SUM()` 对比两库三张 demo 表、`SHOW COLUMNS FROM db_etl.etl_task`、`JSON_LENGTH(dag_json)`、`information_schema.views.VIEW_DEFINITION`、`db_datasource` 的 `config.database`（901→`db_analysis`、902→`db_model`）、`grep` 确认 `Dataset.java` 零引用与 `perm:row:`/`perm:col:` 零写入方

---

## 2026-09-22 · 上传 GitHub 前的 .gitignore 加固（用户请求）

- [2026-09-22] `.gitignore` — **全量重写**（原 41 行只有 Java/IDE/Node 三类基础规则）。补齐本项目实际会漏出去的产物：`unpackage/`+`.hbuilderx/`（uni 三端构建）、`release/`+`dist_electron/`+`*.asar`（pc-desktop）、`logs/`+`uploads/`+`exports/`（file-service 本地存储根 `./uploads` 与运行日志）、`.turbo/`+`.vite/`+`*.tsbuildinfo`+`.pnpm-store/`（workspace 缓存）、`.qoder-credits/`（**29M 本机工具数据**）、`cap-tmp*`/`login-tmp*`/`_check-*.cjs`/`_scan-*.cjs`/`tmp_*.cjs`（历轮调试残留命名）、`deploy/apisix;D/` 与 `deploy/apisix/config.yaml;D`（重定向误建垃圾）。新增本地密钥口径：`.env`、`*.pem`/`*.key`/`*.jks`、`secrets/`、`application-local.yml`（把真口令从 `application.yml` 的默认值挪到这里）。**刻意不动的入库项**：源码、`deploy/sql/**`、`docs/**`、`devlog/**`、`frontend/.env.development`/`.env.production`（Vite 约定要随包走）、以及 `**_archive/`（当前无 git 历史，它们仍是唯一可回查副本）
- 验证方式：在**系统临时目录里 `git init` 一个测试仓库**并复制本 `.gitignore`，用 `git check-ignore -v` 跑约 50 条真实路径（含 `frontend/apps/*/node_modules`、`backend/*/target/*.jar`、`deploy/apisix;D` 目录形态），**并反向断言** README/`.gitlab-ci.yml`/`application.yml`/SQL 补丁/docs/devlog/`_archive` 下的 Java 与 Vue 文件不被吞。两轮全部符合预期，未在工作区建 `.git`（工作区仍非 git 仓库）
- 判据记录：`find . -name "*.jar" -not -path "*/target/*"` **为空** ⇒ 仓库内无入库依赖 jar，故 blanket `*.jar` 安全（原文件本就有此规则，保留）

**目的**：用户要把仓库推到 GitHub。规则的正确性靠 `git check-ignore` 实证而不是"看着像"；同时把"哪些文件里有真口令"作为**必须先处理的事项**向用户点名（凭据类文件的读取受工具策略限制，不由我代读）。
