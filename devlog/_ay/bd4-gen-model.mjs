/**
 * BD4 生成器：数据模型层 + 分析层 + 行列权限演示配置。
 *
 * 产出四份文件，SQL 与自检口径都从这里出，避免"手写补丁和核对脚本各说一套"：
 *   deploy/sql/patch/2026-10-18-bd-demo-model.sql  执行补丁
 *   devlog/_ay/bd4-contract.json                   声明口径（给 bd4-contract.cjs 逐字段核）
 *   devlog/_ay/bd4-probe.sql                       每个数据集来源的 LIMIT 1 列名探针
 *   devlog/_ay/bd4-count.sql                       每个数据集来源的行数探针
 *   devlog/_ay/bd4-readback.sql                    补丁执行后把 911+ 段原样读回（给契约核对用）
 *
 * 三条来自源码的硬约束（写错就是"配了但静默空图"，不是报错）：
 *   1. SqlBuilder.resolveFrom：table_name 必须是裸标识符（不能带库名前缀），自定义 SQL 必须单条且 SELECT 开头
 *   2. 前端 model.ts 的 parseJsonArray：dimensions/metrics/filters 三列是 JSON 文本，键名要和 shared-types 对得上
 *   3. SqlBuilder 白名单：聚合函数 sum/avg/count/max/min（+distinct 变体）、操作符 eq/!=/gt/… 都要能被 canonicalize
 */
import fs from 'node:fs'

const OUT_SQL = 'E:/wowowo/deploy/sql/patch/2026-10-18-bd-demo-model.sql'
const OUT_CONTRACT = 'E:/wowowo/devlog/_ay/bd4-contract.json'
const OUT_PROBE = 'E:/wowowo/devlog/_ay/bd4-probe.sql'
const OUT_COUNT = 'E:/wowowo/devlog/_ay/bd4-count.sql'
const OUT_READBACK = 'E:/wowowo/devlog/_ay/bd4-readback.sql'

const DS_DWH = 901
const DS_BIZ = 902

/** 单引号包裹：反斜杠和单引号都要转义（MySQL 默认接受 \ 转义） */
const str = (v) => `'${String(v).replace(/\\/g, '\\\\').replace(/'/g, "''")}'`
const json = (v) => str(JSON.stringify(v))

const dim = (field, displayName, type, format, dataType) => ({ field, displayName, type, format, dataType })
const met = (field, displayName, aggregation, dataType, format) => ({ field, displayName, aggregation, dataType, format })

const DATE_FMT = 'yyyy-MM-dd'
const TS_FMT = 'yyyy-MM-dd HH:mm:ss'
const MONEY = '#,##0.00'
const INT = '#,##0'
const RATE = '0.00'

const SQL_STORE_TARGET = [
  'SELECT s.store_name, s.region, s.city,',
  'MAX(s.monthly_target) AS target_amount,',
  'SUM(t.amount) AS amount, SUM(t.profit) AS profit,',
  'ROUND(SUM(t.amount) / NULLIF(MAX(s.monthly_target), 0) * 100, 2) AS achievement_rate,',
  'ROUND(SUM(t.profit) / NULLIF(SUM(t.amount), 0) * 100, 2) AS margin_rate',
  'FROM db_model.demo_stores s',
  'JOIN db_analysis.dws_store_sales t ON t.store_id = s.store_id',
  'GROUP BY s.store_name, s.region, s.city',
].join(' ')

const SQL_CHANNEL_CATEGORY = [
  'SELECT channel, category, SUM(amount) AS amount, SUM(profit) AS profit,',
  'SUM(order_count) AS order_count,',
  'ROUND(SUM(profit) / NULLIF(SUM(amount), 0) * 100, 2) AS margin_rate',
  'FROM dwd_sales_daily GROUP BY channel, category',
].join(' ')

const SQL_DAILY_TREND = [
  'SELECT stat_date, SUM(amount) AS amount, SUM(profit) AS profit,',
  'SUM(order_count) AS order_count, SUM(qty) AS qty,',
  'ROUND(SUM(profit) / NULLIF(SUM(amount), 0) * 100, 2) AS margin_rate',
  'FROM dwd_sales_daily GROUP BY stat_date',
].join(' ')

/**
 * 11 个数据集：8 个物理层（一层一个，和 BD2 落地层、BD3 DAG 一一对应）+ 3 个自定义 SQL。
 * 自定义 SQL 是"类型演示"也是真需求：跨库关联门店目标、把 DWD 再卷到两个更细的分析粒度。
 */
const DATASETS = [
  {
    id: 911, name: '订单明细（ODS）', datasourceId: DS_DWH, table: 'ods_order_detail',
    dims: [
      dim('stat_date', '下单日期', 'time', DATE_FMT, 'DATE'),
      dim('stat_hour', '下单小时', 'category', null, 'TINYINT'),
      dim('region', '大区', 'category', null, 'VARCHAR'),
      dim('city', '城市', 'category', null, 'VARCHAR'),
      dim('store_name', '门店', 'category', null, 'VARCHAR'),
      dim('channel', '渠道', 'category', null, 'VARCHAR'),
      dim('category', '商品类目', 'category', null, 'VARCHAR'),
      dim('member_level', '会员等级', 'category', null, 'VARCHAR'),
      dim('order_status', '订单状态', 'category', null, 'VARCHAR'),
      dim('created_at', '下单时间', 'time', TS_FMT, 'DATETIME'),
    ],
    mets: [
      met('pay_amount', '实付金额', 'sum', 'DECIMAL', MONEY),
      met('profit', '毛利', 'sum', 'DECIMAL', MONEY),
      met('qty', '件数', 'sum', 'BIGINT', INT),
      met('order_id', '订单数', 'count', 'BIGINT', INT),
    ],
    filters: [{ field: 'order_status', operator: '!=', value: 'CANCELLED' }],
    note: '2000 行明细，预览和图表都要配 limit；报告不挂这张表（超过 1000 行会被默认 limit 截断）',
  },
  {
    id: 912, name: '日销售汇总（DWD）', datasourceId: DS_DWH, table: 'dwd_sales_daily',
    dims: [
      dim('stat_date', '统计日期', 'time', DATE_FMT, 'DATE'),
      dim('region', '大区', 'category', null, 'VARCHAR'),
      dim('category', '商品类目', 'category', null, 'VARCHAR'),
      dim('channel', '渠道', 'category', null, 'VARCHAR'),
    ],
    mets: [
      met('amount', '销售额', 'sum', 'DECIMAL', MONEY),
      met('profit', '毛利', 'sum', 'DECIMAL', MONEY),
      met('order_count', '订单量', 'sum', 'BIGINT', INT),
      met('qty', '件数', 'sum', 'BIGINT', INT),
    ],
    filters: [],
    note: '1746 行 = 日期×大区×类目×渠道，直接拖会超 limit，报告挂它的再聚合视图 920/921',
  },
  {
    id: 913, name: '门店销售汇总（DWS）', datasourceId: DS_DWH, table: 'dws_store_sales',
    dims: [
      dim('store_name', '门店', 'category', null, 'VARCHAR'),
      dim('region', '大区', 'category', null, 'VARCHAR'),
      dim('city', '城市', 'category', null, 'VARCHAR'),
    ],
    mets: [
      met('amount', '销售额', 'sum', 'DECIMAL', MONEY),
      met('profit', '毛利', 'sum', 'DECIMAL', MONEY),
      met('order_count', '订单量', 'sum', 'BIGINT', INT),
      met('avg_pay_amount', '平均客单价', 'avg', 'DECIMAL', MONEY),
    ],
    filters: [],
    note: '21 行门店粒度；911 号行权限规则挂在这里，用来演示同一个数据集在不同角色下返回不同范围',
  },
  {
    id: 914, name: '会员等级汇总（DWS）', datasourceId: DS_DWH, table: 'dws_member_level',
    dims: [dim('member_level', '会员等级', 'category', null, 'VARCHAR')],
    mets: [
      met('amount', '消费金额', 'sum', 'DECIMAL', MONEY),
      met('order_count', '订单量', 'sum', 'BIGINT', INT),
      met('avg_pay_amount', '平均客单价', 'avg', 'DECIMAL', MONEY),
    ],
    filters: [],
    note: '4 行等级粒度，散客已在 ETL 里被 member_id > 0 过滤掉',
  },
  {
    id: 915, name: '门店绩效（ADS）', datasourceId: DS_DWH, table: 'ads_store_performance',
    dims: [
      dim('store_name', '门店', 'category', null, 'VARCHAR'),
      dim('region', '大区', 'category', null, 'VARCHAR'),
      dim('city', '城市', 'category', null, 'VARCHAR'),
    ],
    mets: [
      met('amount', '销售额', 'sum', 'DECIMAL', MONEY),
      met('profit', '毛利', 'sum', 'DECIMAL', MONEY),
      met('order_count', '订单量', 'sum', 'BIGINT', INT),
      met('target_amount', '月度目标', 'sum', 'DECIMAL', MONEY),
      met('avg_pay_amount', '平均客单价', 'avg', 'DECIMAL', MONEY),
      met('achievement_rate', '达成率', 'avg', 'DECIMAL', RATE),
      met('margin_rate', '毛利率', 'avg', 'DECIMAL', RATE),
    ],
    filters: [],
    note: '21 行；912 号列权限规则把 profit/margin_rate 对普通用户遮掉，就是给这个数据集用的',
  },
  {
    id: 916, name: '时段成交分布（ADS）', datasourceId: DS_DWH, table: 'ads_hour_heat',
    dims: [dim('stat_hour', '下单小时', 'category', null, 'TINYINT')],
    mets: [
      met('orders', '订单量', 'sum', 'BIGINT', INT),
      met('amount', '销售额', 'sum', 'DECIMAL', MONEY),
      met('avg_amount', '时段均值', 'avg', 'DECIMAL', MONEY),
    ],
    filters: [],
    note: '12 行营业时段，直接适合折线/柱状',
  },
  {
    id: 917, name: '会员价值（ADS）', datasourceId: DS_DWH, table: 'ads_member_value',
    dims: [dim('member_level', '会员等级', 'category', null, 'VARCHAR')],
    mets: [
      met('members', '会员数', 'sum', 'BIGINT', INT),
      met('amount', '消费金额', 'sum', 'DECIMAL', MONEY),
      met('avg_amount', '人均消费', 'avg', 'DECIMAL', MONEY),
      met('order_count', '订单量', 'sum', 'BIGINT', INT),
      met('repurchase_rate', '复购率', 'avg', 'DECIMAL', RATE),
    ],
    filters: [],
    note: '4 行等级粒度，members/repurchase_rate 由 917 号 DAG 的去重 SQL 产出',
  },
  {
    id: 918, name: '实时成交流水（ADS）', datasourceId: DS_DWH, table: 'ads_realtime_feed',
    dims: [
      dim('feed_time', '成交时间', 'time', TS_FMT, 'DATETIME'),
      dim('store_name', '门店', 'category', null, 'VARCHAR'),
      dim('city', '城市', 'category', null, 'VARCHAR'),
      dim('category', '商品类目', 'category', null, 'VARCHAR'),
      dim('channel', '渠道', 'category', null, 'VARCHAR'),
    ],
    mets: [met('amount', '成交金额', 'sum', 'DECIMAL', MONEY)],
    filters: [],
    note: '40 行最新流水，给大屏滚动榜用',
  },
  {
    id: 919, name: '门店目标达成（跨库关联）', datasourceId: DS_DWH, sqlQuery: SQL_STORE_TARGET,
    dims: [
      dim('store_name', '门店', 'category', null, 'VARCHAR'),
      dim('region', '大区', 'category', null, 'VARCHAR'),
      dim('city', '城市', 'category', null, 'VARCHAR'),
    ],
    mets: [
      met('target_amount', '月度目标', 'sum', 'DECIMAL', MONEY),
      met('amount', '销售额', 'sum', 'DECIMAL', MONEY),
      met('profit', '毛利', 'sum', 'DECIMAL', MONEY),
      met('achievement_rate', '达成率', 'avg', 'DECIMAL', RATE),
      met('margin_rate', '毛利率', 'avg', 'DECIMAL', RATE),
    ],
    filters: [],
    note: '自定义 SQL 型：把业务库 db_model.demo_stores 的月度目标和分析库的门店汇总关联起来（同一 MySQL 实例跨库，不是跨数据源联邦）',
  },
  {
    id: 920, name: '渠道品类毛利（自定义SQL）', datasourceId: DS_DWH, sqlQuery: SQL_CHANNEL_CATEGORY,
    dims: [
      dim('channel', '渠道', 'category', null, 'VARCHAR'),
      dim('category', '商品类目', 'category', null, 'VARCHAR'),
    ],
    mets: [
      met('amount', '销售额', 'sum', 'DECIMAL', MONEY),
      met('profit', '毛利', 'sum', 'DECIMAL', MONEY),
      met('order_count', '订单量', 'sum', 'BIGINT', INT),
      met('margin_rate', '毛利率', 'avg', 'DECIMAL', RATE),
    ],
    filters: [],
    note: '20 行 = 4 渠道 × 5 类目，DWD 再卷一层，堆叠柱状/热力都能直接接',
  },
  {
    id: 921, name: '每日销售趋势（自定义SQL）', datasourceId: DS_DWH, sqlQuery: SQL_DAILY_TREND,
    dims: [dim('stat_date', '统计日期', 'time', DATE_FMT, 'DATE')],
    mets: [
      met('amount', '销售额', 'sum', 'DECIMAL', MONEY),
      met('profit', '毛利', 'sum', 'DECIMAL', MONEY),
      met('order_count', '订单量', 'sum', 'BIGINT', INT),
      met('qty', '件数', 'sum', 'BIGINT', INT),
      met('margin_rate', '毛利率', 'avg', 'DECIMAL', RATE),
    ],
    filters: [],
    note: '90 行日粒度，给大屏趋势线和报告用',
  },
]

/**
 * 报告 6 条。
 * getReportData 目前把 config 当注释用（只按 datasetId 走 SELECT *），
 * 所以每条报告都挑"表本身就是那个粒度"的数据集：行数 ≤ 1000 且逐行即图上的点，
 * 否则症状就是报告画出来 1746 根柱子。config 仍按真实查询 DSL 写，改后端时不用重配。
 */
const REPORTS = [
  {
    id: 911, name: '门店绩效排行', datasetId: 915, isPublished: 1,
    config: { dimensions: [{ field: 'store_name' }], metrics: [{ field: 'amount', aggFunction: 'SUM', alias: 'amount' }, { field: 'achievement_rate', aggFunction: 'AVG', alias: 'achievement_rate' }], limit: 50 },
    description: '21 家门店的销售额与达成率排行。数据集 915 的 profit/margin_rate 对普通用户遮列，所以报告只用 amount/achievement_rate',
  },
  {
    id: 912, name: '会员价值结构', datasetId: 917, isPublished: 1,
    config: { dimensions: [{ field: 'member_level' }], metrics: [{ field: 'amount', aggFunction: 'SUM', alias: 'amount' }, { field: 'members', aggFunction: 'SUM', alias: 'members' }], orders: [{ field: 'amount', direction: 'DESC' }], limit: 20 },
    description: '四个会员等级的消费额与人数结构，环形图/条形图通用',
  },
  {
    id: 913, name: '时段成交曲线', datasetId: 916, isPublished: 1,
    config: { dimensions: [{ field: 'stat_hour' }], metrics: [{ field: 'orders', aggFunction: 'SUM', alias: 'orders' }, { field: 'amount', aggFunction: 'SUM', alias: 'amount' }], orders: [{ field: 'stat_hour', direction: 'ASC' }], limit: 24 },
    description: '营业 12 个小时段的订单量与销售额，折线图',
  },
  {
    id: 914, name: '渠道×品类毛利矩阵', datasetId: 920, isPublished: 1,
    config: { dimensions: [{ field: 'channel' }, { field: 'category' }], metrics: [{ field: 'margin_rate', aggFunction: 'AVG', alias: 'margin_rate' }], limit: 50 },
    description: '4 渠道 × 5 类目的毛利率，热力图/堆叠柱状；两个维度时前端把最后一个维度当系列',
  },
  {
    id: 915, name: '每日销售趋势', datasetId: 921, isPublished: 1,
    config: { dimensions: [{ field: 'stat_date' }], metrics: [{ field: 'amount', aggFunction: 'SUM', alias: 'amount' }, { field: 'profit', aggFunction: 'SUM', alias: 'profit' }], orders: [{ field: 'stat_date', direction: 'ASC' }], limit: 120 },
    description: '90 天日粒度趋势线，行数在报告默认 limit 之内',
  },
  {
    id: 916, name: '大区目标达成对比', datasetId: 919, isPublished: 1,
    config: { dimensions: [{ field: 'region' }], metrics: [{ field: 'amount', aggFunction: 'SUM', alias: 'amount' }, { field: 'target_amount', aggFunction: 'SUM', alias: 'target_amount' }], limit: 20 },
    description: '六个大区的销售额与月度目标对比，来自跨库关联的自定义 SQL 数据集',
  },
  {
    id: 917, name: '明细口径草稿（未发布）', datasetId: 911, isPublished: 0,
    config: { dimensions: [{ field: 'region' }, { field: 'channel' }], metrics: [{ field: 'pay_amount', aggFunction: 'SUM', alias: 'pay_amount' }], filters: [{ field: 'order_status', operator: '!=', value: 'CANCELLED' }], limit: 500 },
    description: '留一条草稿说明边界：明细数据集 911 有 2000 行，报告默认 limit 1000 会截断，所以没发布',
  },
]

/** 行列权限规则：这是 API-21 那条链路上唯一缺的东西 —— 表里有代码但没有一条规则 */
const RULES = [
  {
    id: 911, name: '普通用户只看华东门店', datasetId: 913, scopeType: 'ROLE', scopeValue: 'user',
    conditions: [{ field: 'region', operator: 'eq', value: '华东' }],
    restrictedColumns: null, enabled: 1,
    remark: '演示行级权限。analyst01/designer01/viewer01 都是角色 user ⇒ 只返回华东；admin 是 super_admin，走 RBAC 短路口径不看规则 ⇒ 看全量；ops01 角色 admin 一条都不命中 ⇒ 按 deny-all 返回空，这是 DataPermissionResolver 的设计（配了规则还不命中就放开全表，等于规则白配）',
  },
  {
    id: 912, name: '普通用户隐藏毛利列', datasetId: 915, scopeType: 'ROLE', scopeValue: 'user',
    conditions: [], restrictedColumns: 'profit,margin_rate', enabled: 1,
    remark: '演示列级权限：只配遮列不配行条件 ⇒ 命中的人在 SELECT * 回列里看不到 profit/margin_rate；显式点名这两列则直接 403。同数据集的行上没有限制',
  },
]

const lines = []
const probe = []
const counts = []

lines.push('-- ============================================================================')
lines.push('-- BD4 演示数据包：数据模型（数据集/维度/指标）+ 分析（报告）+ 行列权限规则')
lines.push('-- 生成器：devlog/_ay/bd4-gen-model.mjs —— 改配置请改生成器后重新生成')
lines.push('-- 前置：BD2 已灌好 8 个落地层，BD3 已配好 8 条 DAG（911-918）')
lines.push('-- 回滚：见文件末尾 rollback 注释块')
lines.push('-- ============================================================================')
lines.push('')
lines.push('SET NAMES utf8mb4;')
lines.push('')
lines.push('-- ============ 1. 数据集 db_model.model_dataset（911-921） ============')
lines.push('-- 旧数据集 901/902/903 指向 demo_sales_daily / demo_user_behavior / demo_orders，')
lines.push('-- 那三张表没有主键、append 重跑会翻倍，且和新的 ODS/DWD/DWS/ADS 分层无关；保留不动，')
lines.push('-- 新数据包一律挂新层。ID 段 911 起，与 BD3 的 ETL 任务号一一对应，看号就知道是哪一层。')
lines.push('USE db_model;')
lines.push('')
lines.push('DELETE FROM model_dataset WHERE id BETWEEN 911 AND 921;')
lines.push('')
for (const d of DATASETS) {
  const cols = ['id', 'tenant_id', 'name', 'datasource_id', 'table_name', 'sql_query', 'dimensions', 'metrics', 'filters', 'create_by', 'deleted']
  const vals = [
    d.id, 1, str(d.name), d.datasourceId,
    d.table ? str(d.table) : 'NULL',
    d.sqlQuery ? str(d.sqlQuery) : 'NULL',
    json(d.dims.map((x) => ({ field: x.field, displayName: x.displayName, type: x.type, ...(x.format ? { format: x.format } : {}) }))),
    json(d.mets.map((x) => ({ field: x.field, displayName: x.displayName, aggregation: x.aggregation, ...(x.format ? { format: x.format } : {}) }))),
    json(d.filters),
    "'admin'", 0,
  ]
  lines.push(`-- ${d.id} ${d.name}：${d.note}`)
  lines.push(`INSERT INTO model_dataset (${cols.join(', ')}) VALUES (${vals.join(', ')});`)
}
lines.push('')

lines.push('-- ============ 2. 字段字典 db_model.model_dimension / model_metric ============')
lines.push('-- 这两张表是数据集 JSON 的展开形态（后端 DimensionController/MetricController 读它们）。')
lines.push('-- 自定义 SQL 数据集没有物理表，table_name 一律留 NULL —— 写成猜出来的表名会让人以为能按表找到它。')
lines.push('')
lines.push('DELETE FROM model_dimension WHERE id BETWEEN 911 AND 999;')
lines.push('DELETE FROM model_metric WHERE id BETWEEN 911 AND 999;')
lines.push('')
let dimId = 911
const dimRows = []
const dimInserts = []
for (const d of DATASETS) {
  d.dims.forEach((x, i) => {
    const dimType = x.type === 'time' ? 'TIME' : x.field === 'region' ? 'GEO' : 'CATEGORY'
    dimRows.push({ id: dimId, datasetId: d.id, field: x.field, displayName: x.displayName, datasourceId: d.datasourceId, table: d.table ?? null, dataType: x.dataType, dimType, sort: i + 1 })
    dimInserts.push(`(${dimId}, 1, ${d.id}, ${str(x.field)}, ${str(x.displayName)}, ${str(x.field)}, ${d.datasourceId}, ${d.table ? str(d.table) : 'NULL'}, ${str(x.dataType)}, ${str(dimType)}, ${str(x.format ?? '')}, ${i + 1}, 'admin', 0)`)
    dimId += 1
  })
}
lines.push('INSERT INTO model_dimension (id, tenant_id, dataset_id, name, display_name, column_name, datasource_id, table_name, data_type, dimension_type, format, sort, create_by, deleted) VALUES')
lines.push(dimInserts.map((v) => '  ' + v).join(',\n') + ';')
lines.push('')
let metId = 911
const metRows = []
const metInserts = []
for (const d of DATASETS) {
  d.mets.forEach((x, i) => {
    metRows.push({ id: metId, datasetId: d.id, field: x.field, displayName: x.displayName, aggregation: x.aggregation.toUpperCase(), datasourceId: d.datasourceId, table: d.table ?? null, dataType: x.dataType, sort: i + 1 })
    metInserts.push(`(${metId}, 1, ${d.id}, ${str(x.field)}, ${str(x.displayName)}, ${str(x.field)}, ${str(x.aggregation.toUpperCase())}, ${str(x.aggregation.toUpperCase())}, ${str(x.dataType)}, ${str(x.format ?? '')}, ${i + 1}, ${d.datasourceId}, ${d.table ? str(d.table) : 'NULL'}, 'admin', 0)`)
    metId += 1
  })
}
lines.push('INSERT INTO model_metric (id, tenant_id, dataset_id, name, display_name, column_name, aggregation_type, agg_function, data_type, format, sort, datasource_id, table_name, create_by, deleted) VALUES')
lines.push(metInserts.map((v) => '  ' + v).join(',\n') + ';')
lines.push('')

lines.push('-- ============ 3. 分析报告 db_analysis.analysis_report（911-917） ============')
lines.push('-- 6 条已发布 + 1 条草稿。config 按真实查询 DSL 写（dimensions/metrics/filters/orders/limit），')
lines.push('-- 但 ReportServiceImpl.getReportData 目前只取 datasetId 走 SELECT *，config 还没接上 ——')
lines.push('-- 所以每条报告的来源都是"表本身就是那个粒度"且行数 ≤ 1000，报告页画出来才和 config 说的一致。')
lines.push('USE db_analysis;')
lines.push('')
lines.push('DELETE FROM analysis_report WHERE id BETWEEN 911 AND 917;')
lines.push('')
for (const r of REPORTS) {
  lines.push(`INSERT INTO analysis_report (id, tenant_id, name, description, datasource_id, dataset_id, config, is_published, create_by, deleted)`)
  lines.push(`  VALUES (${r.id}, 1, ${str(r.name)}, ${str(r.description)}, ${DS_DWH}, ${r.datasetId}, ${json(r.config)}, ${r.isPublished}, 'admin', 0);`)
}
lines.push('')

lines.push('-- ============ 4. 行列权限规则 db_analysis.data_perm_rule（911-912） ============')
lines.push('-- 这张表此前一行数据都没有：代码链路（AS4/API-21）齐了，但没有规则就没法证明规则生效。')
lines.push('-- 两条都 enabled=1，影响面写在 remark 里。要撤掉就把 enabled 置 0（保留行，规则是审计对象）。')
lines.push('')
lines.push('DELETE FROM data_perm_rule WHERE id BETWEEN 911 AND 912;')
lines.push('')
for (const r of RULES) {
  lines.push(`INSERT INTO data_perm_rule (id, name, dataset_id, scope_type, scope_value, conditions_json, restricted_columns, enabled, remark, tenant_id, create_by, deleted)`)
  lines.push(`  VALUES (${r.id}, ${str(r.name)}, ${r.datasetId}, ${str(r.scopeType)}, ${str(r.scopeValue)}, ${r.conditions.length ? json(r.conditions) : 'NULL'}, ${r.restrictedColumns ? str(r.restrictedColumns) : 'NULL'}, ${r.enabled}, ${str(r.remark)}, 1, 'admin', 0);`)
}
lines.push('')

lines.push('-- ============ 5. 自检（人工看输出，不阻断） ============')
lines.push('-- 5.1 数量：数据集 11 / 维度 ' + dimRows.length + ' / 指标 ' + metRows.length + ' / 报告 7 / 规则 2')
lines.push("SELECT CONCAT('@@COUNT model_dataset    = ', COUNT(*)) FROM db_model.model_dataset WHERE id BETWEEN 911 AND 921 AND deleted = 0;")
lines.push("SELECT CONCAT('@@COUNT model_dimension    = ', COUNT(*)) FROM db_model.model_dimension WHERE id BETWEEN 911 AND 999 AND deleted = 0;")
lines.push("SELECT CONCAT('@@COUNT model_metric       = ', COUNT(*)) FROM db_model.model_metric WHERE id BETWEEN 911 AND 999 AND deleted = 0;")
lines.push("SELECT CONCAT('@@COUNT analysis_report    = ', COUNT(*)) FROM analysis_report WHERE id BETWEEN 911 AND 917 AND deleted = 0;")
lines.push("SELECT CONCAT('@@COUNT data_perm_rule     = ', COUNT(*)) FROM data_perm_rule WHERE id BETWEEN 911 AND 912 AND deleted = 0;")
lines.push('')
lines.push("-- 5.2 引用完整性：报告/规则挂的数据集必须存在且未删除（挂到删掉的 dataset 上，症状是查询 404）")
lines.push("SELECT CONCAT('@@BAD report ', r.id, ' -> ', r.dataset_id) FROM analysis_report r")
lines.push('  LEFT JOIN db_model.model_dataset d ON d.id = r.dataset_id AND d.deleted = 0')
lines.push('  WHERE r.deleted = 0 AND d.id IS NULL;')
lines.push("SELECT CONCAT('@@BAD rule ', p.id, ' -> ', p.dataset_id) FROM data_perm_rule p")
lines.push('  LEFT JOIN db_model.model_dataset d ON d.id = p.dataset_id AND d.deleted = 0')
lines.push('  WHERE p.deleted = 0 AND d.id IS NULL;')
lines.push('')
lines.push('-- 5.3 数据源可用性：数据集绑的数据源必须是"可用"（903 ClickHouse 已按实际能力置 0，绑上去就是死路）')
lines.push("SELECT CONCAT('@@BAD dataset ', d.id, ' 绑了不可用数据源 ', d.datasource_id)")
lines.push('  FROM db_model.model_dataset d JOIN db_datasource.datasource s ON s.id = d.datasource_id')
lines.push('  WHERE d.deleted = 0 AND s.status <> 1;')
lines.push('')
lines.push('-- 5.4 表名合法性：物理表型数据集的 table_name 必须能在 information_schema 里找到')
lines.push("SELECT CONCAT('@@BAD dataset ', d.id, ' 找不到表 ', d.table_name)")
lines.push('  FROM db_model.model_dataset d')
lines.push('  WHERE d.deleted = 0 AND d.table_name IS NOT NULL')
lines.push("    AND NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS c")
lines.push("      WHERE c.TABLE_SCHEMA = 'db_analysis' AND c.TABLE_NAME = d.table_name);")
lines.push('')
lines.push('-- 5.5 报告口径：config 里点名的维度/指标，必须真的是那个数据集声明过的字段')
lines.push('--     （JSON 文本列在 MySQL 5.7 里做不了 JSON_TABLE，这一条交给 devlog/_ay/bd4-contract.cjs 逐字段核）')
lines.push("SELECT CONCAT('@@NOTE 报告字段核对请跑 node devlog/_ay/bd4-contract.cjs') AS n;")
lines.push('')
lines.push('-- ============================================================================')
lines.push('-- rollback（人工执行；只删本补丁写进去的 911+ 段，旧数据 901-905 一条不动）')
lines.push('--   USE db_model;')
lines.push('--   DELETE FROM model_dataset WHERE id BETWEEN 911 AND 921;')
lines.push('--   DELETE FROM model_dimension WHERE id BETWEEN 911 AND 999;')
lines.push('--   DELETE FROM model_metric WHERE id BETWEEN 911 AND 999;')
lines.push('--   USE db_analysis;')
lines.push('--   DELETE FROM analysis_report WHERE id BETWEEN 911 AND 917;')
lines.push('--   DELETE FROM data_perm_rule WHERE id BETWEEN 911 AND 912;')
lines.push('-- ============================================================================')

fs.writeFileSync(OUT_SQL, lines.join('\n') + '\n', 'utf8')

/**
 * 探针：每个数据集的 FROM 来源取 1 行，读真实返回列名（LIMIT 0 在 -B 模式下连表头都不打印）。
 * 派生表自带别名 t_source，不能再补 probe_node —— MySQL 只允许一个别名，写了就是 1064。
 */
probe.push('SET NAMES utf8mb4;')
probe.push('-- BD4 数据集来源列名探针（生成器产出，勿手改）')
for (const d of DATASETS) {
  const src = d.sqlQuery ? `(${d.sqlQuery}) t_source` : `\`${d.table}\``
  probe.push(`SELECT '@@PROBE ${d.id}' AS marker;`)
  probe.push('USE db_analysis;')
  probe.push(`SELECT * FROM ${src} LIMIT 1;`)
}
fs.writeFileSync(OUT_PROBE, probe.join('\n') + '\n', 'utf8')

counts.push('SET NAMES utf8mb4;')
counts.push('USE db_analysis;')
counts.push('-- 行数探针：报告只能挂"表本身就是那个粒度"且 ≤ 1000 行的来源，这里给判据')
for (const d of DATASETS) {
  const src = d.sqlQuery ? `(${d.sqlQuery}) t_source` : `\`${d.table}\``
  counts.push(`SELECT CONCAT('@@CNT ~|~ ${d.id} ~|~ ', COUNT(*)) FROM ${src};`)
}
fs.writeFileSync(OUT_COUNT, counts.join('\n') + '\n', 'utf8')

/**
 * 回读：把补丁写进去的行原样取回来给 bd4-contract.cjs 逐字段核。
 * 分隔符用 ' ~|~ ' 而不是制表符 —— mysql -B 会把值里的制表符转义成字面 \t，
 * 解析器就得先猜一层转义；换成不可能出现在数据里的三字符序列，读回来就是写进去的。
 */
const read = []
const SEP = ' ~|~ '
read.push('SET NAMES utf8mb4;')
read.push('USE db_model;')
read.push(`SELECT CONCAT('@@MD${SEP}', id, '${SEP}', datasource_id, '${SEP}', COALESCE(table_name, 'NULL'), '${SEP}', COALESCE(sql_query, 'NULL'), '${SEP}', COALESCE(dimensions, 'NULL'), '${SEP}', COALESCE(metrics, 'NULL'), '${SEP}', COALESCE(filters, 'NULL')) FROM model_dataset WHERE id BETWEEN 911 AND 921 ORDER BY id;`)
read.push(`SELECT CONCAT('@@DIM${SEP}', id, '${SEP}', dataset_id, '${SEP}', column_name, '${SEP}', dimension_type, '${SEP}', COALESCE(table_name, 'NULL'), '${SEP}', data_type, '${SEP}', sort) FROM model_dimension WHERE id BETWEEN 911 AND 999 ORDER BY id;`)
read.push(`SELECT CONCAT('@@MET${SEP}', id, '${SEP}', dataset_id, '${SEP}', column_name, '${SEP}', aggregation_type, '${SEP}', COALESCE(table_name, 'NULL'), '${SEP}', data_type, '${SEP}', sort) FROM model_metric WHERE id BETWEEN 911 AND 999 ORDER BY id;`)
read.push('USE db_analysis;')
read.push(`SELECT CONCAT('@@RPT${SEP}', id, '${SEP}', dataset_id, '${SEP}', is_published, '${SEP}', COALESCE(config, 'NULL')) FROM analysis_report WHERE id BETWEEN 911 AND 917 ORDER BY id;`)
read.push(`SELECT CONCAT('@@RULE${SEP}', id, '${SEP}', dataset_id, '${SEP}', scope_type, '${SEP}', scope_value, '${SEP}', COALESCE(conditions_json, 'NULL'), '${SEP}', COALESCE(restricted_columns, 'NULL'), '${SEP}', enabled) FROM data_perm_rule WHERE id BETWEEN 911 AND 912 ORDER BY id;`)
read.push(`SELECT CONCAT('@@DS${SEP}', id, '${SEP}', name, '${SEP}', status) FROM db_datasource.datasource WHERE id IN (901, 902, 903, 904) ORDER BY id;`)
fs.writeFileSync(OUT_READBACK, read.join('\n') + '\n', 'utf8')

/**
 * 查询语义探针：把报告 config 按 SqlBuilder 的规则拼成它"接上之后"会产出的那条 SQL，跑一遍。
 * 现在 ReportServiceImpl.getReportData 只按 datasetId 走 SELECT *，config 还没进 SQL ——
 * 这一份给出的是"config 说的口径本身取不取到数"，是 BD5 绑数据集的图表能不能出数的前置判据。
 * 拼法逐条对齐源码：维度裸列 + GROUP BY、指标 AGG(列) AS `别名`、filters 走白名单操作符、末尾 LIMIT。
 */
const OUT_QUERY = 'E:/wowowo/devlog/_ay/bd4-query.sql'
const query = []
query.push('SET NAMES utf8mb4;')
query.push('USE db_analysis;')
query.push('-- 每条已发布报告的 config-SQL 行数与列名（生成器产出，勿手改）')
const backtick = (v) => '`' + String(v).replace(/[^a-zA-Z0-9_]/g, '') + '`'
const OP = { eq: '=', '!=': '<>', ne: '<>', gt: '>', lt: '<', gte: '>=', lte: '<=' }
for (const r of REPORTS) {
  if (r.isPublished !== 1) continue
  const ds = DATASETS.find((x) => x.id === r.datasetId)
  const src = ds.sqlQuery ? `(${ds.sqlQuery}) t_source` : `\`${ds.table}\``
  const dims = (r.config.dimensions ?? []).map((d) => backtick(d.field))
  const mets = (r.config.metrics ?? []).map((m) => `${(m.aggFunction ?? 'SUM').toUpperCase()}(${backtick(m.field)}) AS ${backtick(m.alias ?? m.field)}`)
  const select = [...dims, ...mets].join(', ')
  let sql = `SELECT ${select} FROM ${src}`
  const filters = (r.config.filters ?? []).map((f) => `${backtick(f.field)} ${OP[f.operator] ?? '='} '${String(f.value).replace(/'/g, "''")}'`)
  if (filters.length) sql += ` WHERE ${filters.join(' AND ')}`
  if (dims.length) sql += ` GROUP BY ${dims.join(', ')}`
  const orders = (r.config.orders ?? []).map((o) => `${backtick(o.field)} ${String(o.direction).toUpperCase()}`)
  // 排序字段要么是 SELECT 出来的维度，要么是指标别名（MySQL 允许按别名排序），不需要特例
  if (orders.length) sql += ` ORDER BY ${orders.join(', ')}`
  sql += ` LIMIT ${r.config.limit ?? 1000}`
  query.push(`SELECT '@@Q ${r.id}' AS marker;`)
  query.push(`SELECT CONCAT('@@ROWS ~|~ ${r.id} ~|~ ', COUNT(*)) FROM (${sql}) wrapped;`)
  query.push(`SELECT * FROM (${sql}) sample LIMIT 3;`)
}
fs.writeFileSync(OUT_QUERY, query.join('\n') + '\n', 'utf8')

fs.writeFileSync(OUT_CONTRACT, JSON.stringify({
  datasets: DATASETS.map((d) => ({
    id: d.id, name: d.name, datasourceId: d.datasourceId, table: d.table ?? null, sqlQuery: d.sqlQuery ?? null,
    dimensions: d.dims.map((x) => ({ field: x.field, displayName: x.displayName, type: x.type, format: x.format ?? null })),
    metrics: d.mets.map((x) => ({ field: x.field, displayName: x.displayName, aggregation: x.aggregation, format: x.format ?? null })),
    filters: d.filters, note: d.note,
  })),
  reports: REPORTS,
  rules: RULES,
  dimensionRows: dimRows,
  metricRows: metRows,
}), null, 2, 'utf8')

console.log(`已生成：SQL ${lines.length} 行 / 数据集 ${DATASETS.length} / 维度行 ${dimRows.length} / 指标行 ${metRows.length} / 报告 ${REPORTS.length} / 规则 ${RULES.length}`)
