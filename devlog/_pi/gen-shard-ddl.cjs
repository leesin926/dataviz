// PI4 · 分片物理表与回填的生成器（不连库，纯生成：Docker 停着也能审）
// 为什么生成而不是手写 49 条 CREATE：月度表集合是"规则 + 窗口"的展开，
// 手写一定会出现"某张表少建了一个月"这种只有运行时才炸的洞；生成物可重跑、可 diff。
// 桶位哈希必须和 MySQL 侧完全一致，否则 Java 写到 ds1、SQL 回填却塞进 ds0 —— 数据看起来在，读永远读不到。
// 口径：bucket = MOD(无符号整数(MD5(CAST(tenant_id AS CHAR)) 的前 4 字节大端), buckets)
const fs = require('fs')
// 表清单 / 窗口 / 桶数 / 哈希式子都在 shard-config.cjs，三个脚本共用一份
const { MONTHS, TABLES, PARITY_EXPR, bucketExpr, routablePredicate, bucketOf, monthBounds, schemas, WINDOW_FROM, WINDOW_TO } = require('./shard-config.cjs')

const OUT_DIR = 'E:/wowowo/deploy/sql/shard'

fs.mkdirSync(OUT_DIR, { recursive: true })

const months = []
for (const t of TABLES) {
  months.push(`-- ${t.full}（时间列 ${t.time}）`)
  for (const m of MONTHS) {
    // CREATE TABLE ... LIKE 连索引与注释一起复制，所以派生列（tenant_id）必须先补上再建月度表
    // 库名与表名必须各自加反引号：`db_alert.alert_event` 是一个标识符，MySQL 会当表名找不到它
    months.push(`CREATE TABLE IF NOT EXISTS \`${t.schema}\`.\`${t.logic}_${m}\` LIKE \`${t.schema}\`.\`${t.logic}\`;`)
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
-- 用派生表而不是直接 GROUP BY 表达式：MySQL 8 认不出 CONCAT 里的 SUBSTRING_INDEX 与分组列的函数依赖，
-- 直写会撞 only_full_group_by 报 1055（DDL 已经成功，只有这行自检失败，很容易误判成"建表没成"）
SELECT CONCAT('@@MONTH-TABLES ', s, '.', b, ' = ', c) AS check_line
FROM (SELECT table_schema AS s, SUBSTRING_INDEX(table_name, '_20', 1) AS b, COUNT(*) AS c
      FROM information_schema.TABLES
      WHERE table_name REGEXP '_(${MONTHS.join('|')})$'
        AND table_schema IN (${schemas().map(s => `'${s}'`).join(', ')})
      GROUP BY 1, 2) x
ORDER BY 1;
`, 'utf8')

const backfill = []
for (const t of TABLES) {
  for (const b of [0, 1]) {
    for (const m of MONTHS) {
      const [lo, nm] = monthBounds(m)
      backfill.push(`-- ${t.full} → ${t.logic}_${m} @ds${b}（route-by=${t.routeBy}；列序由 CREATE TABLE ... LIKE 保证一致，所以 SELECT * 是对的）`)
      backfill.push(`INSERT INTO \`${t.schema}\`.\`${t.logic}_${m}\``)
      backfill.push(`  SELECT * FROM \`${t.schema}\`.\`${t.logic}\``)
      backfill.push(`  WHERE ${t.time} >= '${lo}' AND ${t.time} < '${nm}'`)
      backfill.push(`    AND ${routablePredicate(t)} AND ${bucketExpr(t)} = ${b};`)
      backfill.push('')
    }
  }
  backfill.push(`-- ${t.full} 窗口外的存量行数（必须为 0，否则扩窗口重跑，别让老数据留在没人读的表里）`)
  backfill.push(`SELECT CONCAT('@@OUT-OF-WINDOW ${t.full} = ', COUNT(*)) FROM \`${t.schema}\`.\`${t.logic}\``)
  backfill.push(`  WHERE ${t.time} < '${WINDOW_FROM}' OR ${t.time} >= '${WINDOW_TO}';`)
  backfill.push(`-- ${t.full} 路由维度缺值的行数（不参与分片，留在逻辑表；见 README 的 API-162 决定）`)
  backfill.push(`SELECT CONCAT('@@UNASSIGNED ${t.full} = ', COUNT(*)) FROM \`${t.schema}\`.\`${t.logic}\``)
  backfill.push(`  WHERE NOT (${routablePredicate(t)});`)
  backfill.push('')
}
fs.writeFileSync(OUT_DIR + '/2026-11-06-pi4-03-backfill-TEMPLATE.sql',
  `-- PI4 · 回填模板（**不要直接执行**）：这是给 devlog/_pi/pi-backfill-shards.cjs 用的语句清单。
-- 为什么不直接执行：月度行既要按时间分、又要按租户哈希分实例，而"搬到第二个实例"必须跨实例搬运
-- （MySQL 没有本地跨实例 INSERT），所以真正的执行器是驱动脚本：ds0 本地写 + mysqldump→ds1 + 两边行数对账，
-- 对不平就拒绝收尾。留这个模板是为了让 SQL 判据本身可评审。
-- 另外：tenant_id 为空的行两边都数不到（MOD(NULL)=NULL），驱动脚本单独数、单独留档，不静默丢弃。
SET NAMES utf8mb4;

${backfill.join('\n')}`, 'utf8')

// README.md 不在这里生成：它记的是实测数（孤儿行数、实测落点、执行到哪一步），
// 生成物每次重跑都会把实测证据抹成模板话。这里只打印判据，README 手写维护。
console.log('已生成：')
console.log('  deploy/sql/shard/2026-11-06-pi4-02-month-tables.sql  ' +
  fs.readFileSync(OUT_DIR + '/2026-11-06-pi4-02-month-tables.sql', 'utf8').split('\n').length + ' 行')
console.log('  deploy/sql/shard/2026-11-06-pi4-03-backfill-TEMPLATE.sql')
console.log('  （README.md 手写，不由本脚本生成）')
console.log('桶位判据（Java 侧必须复现同样的数）：')
for (const t of [1, 901, 902, 903, 904, 905]) console.log('  tenant ' + t + ' → ds' + bucketOf(t))
