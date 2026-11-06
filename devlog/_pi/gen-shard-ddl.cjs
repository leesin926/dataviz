// PI4 · 分片物理表与回填的生成器（不连库，纯生成：Docker 停着也能审）
// 为什么生成而不是手写 49 条 CREATE：月度表集合是"规则 + 窗口"的展开，
// 手写一定会出现"某张表少建了一个月"这种只有运行时才炸的洞；生成物可重跑、可 diff。
// 桶位哈希必须和 MySQL 侧完全一致，否则 Java 写到 ds1、SQL 回填却塞进 ds0 —— 数据看起来在，读永远读不到。
// 口径：bucket = MOD(无符号整数(MD5(CAST(tenant_id AS CHAR)) 的前 4 字节大端), buckets)
const fs = require('fs')
const crypto = require('crypto')

const OUT_DIR = 'E:/wowowo/deploy/sql/shard'
const BUCKETS = 2
// 窗口：存量演示数据从 2026-08 起（BD 轮），向后铺到 2027-02 留半年余量。
// 超出窗口的历史行不会被静默丢弃 —— 回填脚本会把它们数出来，逼人来扩窗口。
const MONTHS = ['202608', '202609', '202610', '202611', '202612', '202701', '202702']

// schema.table: 时间列（sys_login_log 用的是 login_time，不是 create_time —— 这类差异最容易漏）
const TABLES = [
  { logic: 'db_alert.alert_event', time: 'create_time' },
  { logic: 'db_alert.alert_notify_log', time: 'create_time' },
  { logic: 'db_openapi.openapi_log', time: 'create_time' },
  { logic: 'db_auth.sys_login_log', time: 'login_time' },
  { logic: 'db_analysis.query_history', time: 'create_time' },
  { logic: 'db_admin.audit_log', time: 'create_time' },
  { logic: 'db_monitor.audit_log', time: 'create_time' }
]

function bucketOf(tenantId, buckets) {
  const h = crypto.createHash('md5').update(String(tenantId)).digest()
  const head = (h[0] & 0xff) * 16777216 + (h[1] & 0xff) * 65536 + (h[2] & 0xff) * 256 + (h[3] & 0xff)
  return head % buckets
}

// MySQL 侧同一条式子：前 8 个十六进制字符 = 摘要前 4 字节，CONV 出来就是无符号大端整数
const PARITY_EXPR = (t, b) =>
  `MOD(CAST(CONV(LEFT(MD5(CAST(${t} AS CHAR)), 8), 16, 10) AS UNSIGNED), ${b})`

fs.mkdirSync(OUT_DIR, { recursive: true })

const months = []
for (const t of TABLES) {
  const schema = t.logic.split('.')[0]
  const logic = t.logic.split('.')[1]
  months.push(`-- ${t.logic}（时间列 ${t.time}）`)
  for (const m of MONTHS) {
    // CREATE TABLE ... LIKE 连索引与注释一起复制，所以派生列（tenant_id）必须先补上再建月度表
    // 库名与表名必须各自加反引号：`db_alert.alert_event` 是一个标识符，MySQL 会当表名找不到它
    months.push(`CREATE TABLE IF NOT EXISTS \`${schema}\`.\`${logic}_${m}\` LIKE \`${schema}\`.\`${logic}\`;`)
  }
  months.push('')
}

fs.writeFileSync(OUT_DIR + '/2026-11-06-pi4-02-month-tables.sql',
  `-- PI4 · 分表物理表：每个实例都建同一套月度表（数据按租户哈希分到实例，表结构两边一致）
-- ⚠️ 本文件必须在 **两个实例各跑一遍**：3306（ds0）与 3307（ds1）。只跑一边 = 一半的写没有落点。
--    跑法：docker exec -i dataviz-mysql   sh -c '...mysql...' < 本文件
--          docker exec -i dataviz-mysql-2 sh -c '...mysql...' < 本文件
-- 前置：先跑 2026-11-06-pi4-01-derive-tenant-id.sql（LIKE 要复制 tenant_id 派生列）。
-- 生成物，别手改：改窗口请改 devlog/_pi/gen-shard-ddl.cjs 后重新生成。
SET NAMES utf8mb4;

${months.join('\n')}
-- 回读自检（两实例各跑一次，期望每张逻辑表 ${MONTHS.length} 张月度表）
SELECT CONCAT('@@MONTH-TABLES ', table_schema, '.', SUBSTRING_INDEX(table_name, '_20', 1),
              ' = ', COUNT(*)) AS check_line
FROM information_schema.TABLES
WHERE table_name REGEXP '_(202608|202609|202610|202611|202612|202701|202702)$'
  AND table_schema IN ('db_alert', 'db_openapi', 'db_auth', 'db_analysis', 'db_admin', 'db_monitor')
GROUP BY table_schema, SUBSTRING_INDEX(table_name, '_20', 1)
ORDER BY 1;
`, 'utf8')

const backfill = []
for (const t of TABLES) {
  const schema = t.logic.split('.')[0]
  const logic = t.logic.split('.')[1]
  for (const b of [0, 1]) {
    for (const m of MONTHS) {
      const lo = `${m.slice(0, 4)}-${m.slice(4, 6)}-01 00:00:00`
      const nm = `${+m.slice(0, 4) + (m.slice(4, 6) === '12' ? 1 : 0)}-${String((+m.slice(4, 6) % 12) + 1).padStart(2, '0')}-01 00:00:00`
      backfill.push(`-- ${t.logic} → ${logic}_${m} @ds${b}（列序由 CREATE TABLE ... LIKE 保证一致，所以 SELECT * 是对的）`)
      backfill.push(`INSERT INTO \`${schema}\`.\`${logic}_${m}\``)
      backfill.push(`  SELECT * FROM \`${schema}\`.\`${logic}\``)
      backfill.push(`  WHERE ${t.time} >= '${lo}' AND ${t.time} < '${nm}'`)
      backfill.push(`    AND ${PARITY_EXPR('tenant_id', BUCKETS)} = ${b};`)
      backfill.push('')
    }
  }
  backfill.push(`-- ${t.logic} 窗口外的存量行数（必须为 0，否则扩窗口重跑，别让老数据留在没人读的表里）`)
  backfill.push(`SELECT CONCAT('@@OUT-OF-WINDOW ${t.logic} = ', COUNT(*)) FROM \`${schema}\`.\`${logic}\``)
  backfill.push(`  WHERE ${t.time} < '2026-08-01 00:00:00' OR ${t.time} >= '2027-03-01 00:00:00';`)
  backfill.push('')
}
fs.writeFileSync(OUT_DIR + '/2026-11-06-pi4-03-backfill-TEMPLATE.sql',
  `-- PI4 · 回填模板（**不要直接执行**）：这是给 devlog/_pi/pi-backfill-shards.cjs 用的语句清单。
-- 为什么不直接执行：月度行既要按时间分、又要按租户哈希分实例，而"搬到第二个实例"必须跨实例搬运
-- （MySQL 没有本地跨实例 INSERT），所以真正的执行器是驱动脚本：ds0 本地写 + mysqldump→ds1 + 两边行数对账，
-- 对不平就拒绝把原表改名成 zz_bak_。留这个模板是为了让 SQL 判据本身可评审。
SET NAMES utf8mb4;

${backfill.join('\n')}`, 'utf8')

const table = [1, 901, 902, 903, 904, 905].map(t => `--   tenant ${t} → ds${bucketOf(t, BUCKETS)}`).join('\n')
fs.writeFileSync(OUT_DIR + '/README.md',
  `# 分库分表的物理表与迁移

\`gen-shard-ddl.cjs\` 生成这里的所有 SQL；不要手改生成物。

**桶位哈希（Java 与 MySQL 必须同一条式子）**

\`\`\`
bucket = MOD( 无符号整数( MD5(CAST(tenant_id AS CHAR)) 的前 4 字节按大端拼出来的数 ), buckets )
\`\`\`

MySQL 侧写法：\`${PARITY_EXPR('tenant_id', BUCKETS)}\`（取 MD5 十六进制串前 8 个字符 = 前 4 字节）。
Java 侧写法：\`ShardRouter.stableHash\` 必须取同样的前 4 字节大端无符号值 —— 两边不一致的后果是
"应用写到 ds1，回填把同一条租户塞进 ds0"，数据在盘上但按任何条件都读不到。

演示租户实测落点（buckets=${BUCKETS}）：

${table}

**执行顺序**（Docker 起来后）

1. \`2026-11-06-pi4-01-derive-tenant-id.sql\` —— 给 \`openapi_log\` / \`alert_notify_log\` 补 \`tenant_id\` 派生列并回填
   （这两张表本来没有租户列，没有它就没法分库；LIKE 建月度表会复制这一列，所以必须最先跑）
2. \`2026-11-06-pi4-02-month-tables.sql\` —— **两个实例各跑一遍**
3. \`node devlog/_pi/pi-backfill-shards.cjs\` —— 回填 + 两侧对账，对不平就拒绝归档原表
4. 打开 \`shard.enabled=true\` 起服务，跑跨实例判据（同一条租户的两个月份分别落在哪个实例、扇出归并读的分页对不对）

`, 'utf8')

console.log('已生成：')
console.log('  deploy/sql/shard/2026-11-06-pi4-02-month-tables.sql  ' + fs.readFileSync(OUT_DIR + '/2026-11-06-pi4-02-month-tables.sql', 'utf8').split('\n').length + ' 行')
console.log('  deploy/sql/shard/2026-11-06-pi4-03-backfill-TEMPLATE.sql')
console.log('  deploy/sql/shard/README.md')
console.log('桶位判据（Java 侧必须复现同样的数）：')
for (const t of [1, 901, 902, 903, 904, 905]) console.log('  tenant ' + t + ' → ds' + bucketOf(t, BUCKETS))
