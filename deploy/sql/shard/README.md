# 分库分表的物理表与迁移

`gen-shard-ddl.cjs` 生成这里的所有 SQL；不要手改生成物。

**桶位哈希（Java 与 MySQL 必须同一条式子）**

```
bucket = MOD( 无符号整数( MD5(CAST(tenant_id AS CHAR)) 的前 4 字节按大端拼出来的数 ), buckets )
```

MySQL 侧写法：`MOD(CAST(CONV(LEFT(MD5(CAST(tenant_id AS CHAR)), 8), 16, 10) AS UNSIGNED), 2)`（取 MD5 十六进制串前 8 个字符 = 前 4 字节）。
Java 侧写法：`ShardRouter.stableHash` 必须取同样的前 4 字节大端无符号值 —— 两边不一致的后果是
"应用写到 ds1，回填把同一条租户塞进 ds0"，数据在盘上但按任何条件都读不到。

演示租户实测落点（buckets=2）：

--   tenant 1 → ds0
--   tenant 901 → ds0
--   tenant 902 → ds0
--   tenant 903 → ds1
--   tenant 904 → ds1
--   tenant 905 → ds1

**执行顺序**（Docker 起来后）

1. `2026-11-06-pi4-01-derive-tenant-id.sql` —— 给 `openapi_log` / `alert_notify_log` 补 `tenant_id` 派生列并回填
   （这两张表本来没有租户列，没有它就没法分库；LIKE 建月度表会复制这一列，所以必须最先跑）
   ⚠️ 跑完先数孤儿：`SELECT COUNT(*) FROM db_openapi.openapi_log WHERE tenant_id IS NULL`。
   没有 `client_id` 又没有租户的行**分不了库**，必须先决定处置，不许静默给个默认值。
   2026-11-07 实测：`openapi_log` 17 行里 8 行无归属（全是 `/api/openapi/callback/webhook/test` 被 401 拒掉的探针记录，
   `client_id`/`app_name` 都是空）。决定是**不虚构租户**：这 8 行留在逻辑表里不参与分片，只经超管全局视图可见。
2. `2026-11-07-pi4-01b-target-instance-schema.sql` —— **只在 ds1（3307）跑**：`dataviz-mysql-2` 是空实例，
   库和基表都没有，`CREATE TABLE ... LIKE` 会直接报 `Unknown database`，于是 3307 上一条月度表都建不出来。
   这份镜像是结构 only、零行；由 `node devlog/_pi/gen-ds1-schema.cjs` 从 ds0 的现网结构导出，
   表清单解析自 pi4-02，不重写第二遍。
3. `2026-11-06-pi4-02-month-tables.sql` —— **两个实例各跑一遍**
   （回读 `@@MONTH-TABLES` 每行应为 7；两边各 49 张。只跑一边 = 一半的写没有落点）
4. `node devlog/_pi/pi-backfill-shards.cjs` —— 回填 + 两侧对账，对不平就拒绝归档原表
5. 打开 `shard.enabled=true` 起服务，跑跨实例判据（同一条租户的两个月份分别落在哪个实例、扇出归并读的分页对不对）

