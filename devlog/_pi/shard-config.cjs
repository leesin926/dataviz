// PI4 · 分片配置的唯一来源：表清单、月份窗口、桶数、哈希式子、两个实例的容器名。
//
// 为什么要有这个文件：这几组数在三个地方都要用 —— 建表生成器（gen-shard-ddl.cjs）、
// 目标实例基表镜像（gen-ds1-schema.cjs）、回填驱动（pi-backfill-shards.cjs）。
// 手写三遍的必然后果是"月度表按新清单建了、回填还按老清单跑"，那种错在盘上表现为行在但读不到。
//
// 桶位哈希式子必须与 Java 的 ShardRouter.stableHash 完全一致（改这里就要改那里，
// 并由 backend/common/common-mybatis 的 ShardBucketParityTest 钉住），
// 也必须是 deploy/sql/shard/README.md 里写着的那一条。
const crypto = require('crypto')

const BUCKETS = 2

// 存量演示数据从 2026-08 起（BD 轮），向后铺到 2027-02 留半年余量。
// 窗口外的行不会被静默丢弃 —— 回填驱动会把它们数出来并拒绝收尾，逼人来扩窗口。
const MONTHS = ['202608', '202609', '202610', '202611', '202612', '202701', '202702']

// schema.table: 时间列（sys_login_log 用的是 login_time，不是 create_time —— 这类差异最容易漏）
// routeBy: TENANT = 按租户哈希选实例；NONE = 按月份轮转实例（该表的租户列不能当路由键，见下）
// full 是给注释与报错用的"库.表"形态，避免每个使用方各自拼一遍
const TABLES = [
  { schema: 'db_alert', logic: 'alert_event', time: 'create_time', routeBy: 'TENANT' },
  { schema: 'db_alert', logic: 'alert_notify_log', time: 'create_time', routeBy: 'TENANT' },
  // openapi_log 的 tenant_id 是派生列（pi4-01），而且鉴权失败的行天生没有租户：
  // 拿它当路由键等于"401 的审计写不进去"，所以这张表按月份轮转实例，租户只是审计列。
  { schema: 'db_openapi', logic: 'openapi_log', time: 'create_time', routeBy: 'NONE' },
  { schema: 'db_auth', logic: 'sys_login_log', time: 'login_time', routeBy: 'TENANT' },
  { schema: 'db_analysis', logic: 'query_history', time: 'create_time', routeBy: 'TENANT' },
  { schema: 'db_admin', logic: 'audit_log', time: 'create_time', routeBy: 'TENANT' },
  { schema: 'db_monitor', logic: 'audit_log', time: 'create_time', routeBy: 'TENANT' }
].map(t => ({ ...t, full: `${t.schema}.${t.logic}` }))

const DS0_CONTAINER = 'dataviz-mysql'
const DS1_CONTAINER = 'dataviz-mysql-2'

/** MD5 前 4 字节大端无符号整数 % buckets —— 与 MySQL 侧 PARITY_EXPR 同一条式子。 */
function bucketOf (tenantId, buckets = BUCKETS) {
  const h = crypto.createHash('md5').update(String(tenantId)).digest()
  const head = (h[0] & 0xff) * 16777216 + (h[1] & 0xff) * 65536 + (h[2] & 0xff) * 256 + (h[3] & 0xff)
  return head % buckets
}

// MySQL 侧同一条式子：前 8 个十六进制字符 = 摘要前 4 字节，CONV 出来就是无符号大端整数
const PARITY_EXPR = (column, buckets = BUCKETS) =>
  `MOD(CAST(CONV(LEFT(MD5(CAST(${column} AS CHAR)), 8), 16, 10) AS UNSIGNED), ${buckets})`

/**
 * 一行落哪个实例的 SQL 表达式：TENANT 按租户哈希，NONE 按月份串哈希。
 * 与 Java 侧 ShardRouteBy 同名同义；这里不带反引号，因为同一条串要进 mysqldump --where（走 sh -c，
 * 反引号会被 shell 当命令替换）。列名都是 shard-config 里白名单过的裸标识符，裸写安全。
 */
function bucketExpr (t, buckets = BUCKETS) {
  return t.routeBy === 'NONE'
    ? PARITY_EXPR(`DATE_FORMAT(${t.time}, '%Y%m')`, buckets)
    : PARITY_EXPR('tenant_id', buckets)
}

/** 参与分片的行的"可路由"条件：路由维度缺值的行不搬（TENANT 缺租户就不搬，NONE 只缺时间才不搬） */
function routablePredicate (t) {
  const timeNotNull = `${t.time} IS NOT NULL`
  return t.routeBy === 'NONE' ? timeNotNull : `tenant_id IS NOT NULL AND ${timeNotNull}`
}

/** '202611' → ['2026-11-01 00:00:00', '2026-12-01 00:00:00']（左闭右开，跨年自动进位） */
function monthBounds (m) {
  const year = +m.slice(0, 4)
  const month = +m.slice(4, 6)
  const nextYear = month === 12 ? year + 1 : year
  const nextMonth = month === 12 ? 1 : month + 1
  return [
    `${year}-${String(month).padStart(2, '0')}-01 00:00:00`,
    `${nextYear}-${String(nextMonth).padStart(2, '0')}-01 00:00:00`
  ]
}

function schemas () {
  return [...new Set(TABLES.map(t => t.schema))]
}

module.exports = {
  BUCKETS,
  MONTHS,
  TABLES,
  DS0_CONTAINER,
  DS1_CONTAINER,
  bucketOf,
  PARITY_EXPR,
  bucketExpr,
  routablePredicate,
  monthBounds,
  schemas,
  // 窗口整体边界：回填与对账用它判断"有没有行落在窗口外"
  WINDOW_FROM: monthBounds(MONTHS[0])[0],
  WINDOW_TO: monthBounds(MONTHS[MONTHS.length - 1])[1]
}
