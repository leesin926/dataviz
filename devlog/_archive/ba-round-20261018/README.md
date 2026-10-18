# 阶段 BA 证据归档（2026-10-18）

本轮（BA0~BA2，报告 docs/12 §19.35，决策 docs/10 D84）产生的构建、门禁与 SQL 读数。

| 文件 | 内容 | 是否入库 |
|---|---|---|
| `tmp-ba2-sql.out` | `2026-10-18-ay-query-history-columns.sql` 的执行读数：4 个校验结果集（补列后列清单 / `total_rows`+`visible_rows`=9 / 主表与备份表行数一致） | 是 |
| `tmp-ba2-gate.log` | `mvn -o test` 全仓门禁：**BUILD SUCCESS / 362 tests / 0 失败 / 1 skipped** | 否（`.gitignore:59 *.log`） |
| `tmp-ba2-pkg.log` | **失败那一次**打包：`spring-boot:repackage` 报 `Unable to rename … .jar.original`，而 fat jar 已被覆盖成 45 KB 瘦包（D84 ② 的原始证据） | 否 |
| `tmp-ba2-pkg2.log` | 停进程后重打包：`[ERROR]` 0 条，产物回到 82,326,181 字节 | 否 |
| `tmp-ba2-8087.snapshot.log` | 8087（PID 89828）启动日志快照，**是拷贝不是移动** —— 正跑着的 JVM 占着 `RedirectStandardOutput` 的句柄，`mv` 会 `Device or resource busy` | 否 |

探针与运行时读数本身在 `devlog/_ay/` 并已入库：`ba1-z1-criteria.cjs`/`.json`（Z1 判据 13 PASS，`S11c` 两轮读数 `500/500` → `200/400`）、`ba2-analysis-history.cjs`/`.json`（A1~A4 四条）、`ba2-precheck.sql`/`ba2-precheck2.sql`（执行前对活库的列预检，否掉了补丁的第 2、3 步）、`ba2-inspect.sql`、`ba2-cleanup.sql`（只删本轮我自己写的行，跑完核对 0 残留）。
