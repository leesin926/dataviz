/**
 * BD3：生成「数据源 + ETL 设计」补丁 SQL。
 *
 * 之所以用脚本而不是手写：每条 etl_task 的 transform_config 必须和 BD2 里真正装载数据的
 * INSERT..SELECT 语义一致，两处手抄一定会漂。这里把 DAG 当成唯一事实来源，
 * 顺带产出契约自检所需的列清单。
 */
import fs from 'node:fs'

const OUT = 'deploy/sql/patch/2026-10-18-bd-demo-etl.sql'

/** 数据源：902=订单业务库(db_model)，901=分析库(db_analysis) */
const DS_BIZ = 902
const DS_DWH = 901

/** ODS 取数原文：与 2026-10-18-bd-demo-data.sql 的装载 SELECT 同形（表名不带库名前缀，走各自数据源的默认库） */
const SQL_ODS = [
  'SELECT o.order_id AS order_id, DATE(o.created_at) AS stat_date, HOUR(o.created_at) AS stat_hour,',
  "       o.region AS region, s.city AS city, o.store_id AS store_id, s.store_name AS store_name,",
  '       o.channel AS channel, o.category AS category, o.user_id AS member_id,',
  "       IFNULL(m.level, '散客') AS member_level, o.pay_amount AS pay_amount, o.profit AS profit,",
  '       o.qty AS qty, o.status AS order_status, o.created_at AS created_at',
  '  FROM demo_orders o',
  '  JOIN demo_stores s ON s.store_id = o.store_id',
  '  LEFT JOIN demo_members m ON m.member_id = o.user_id',
  " WHERE o.status IN ('PAID', 'SHIPPED')",
].join(' ')

const SQL_ADS_STORE = [
  'SELECT d.store_id AS store_id, d.store_name AS store_name, d.region AS region, d.city AS city,',
  '       d.amount AS amount, d.profit AS profit, d.order_count AS order_count,',
  '       d.avg_pay_amount AS avg_pay_amount, s.monthly_target AS target_amount,',
  '       ROUND(d.amount / NULLIF(s.monthly_target, 0) * 100, 2) AS achievement_rate,',
  '       ROUND(d.profit / NULLIF(d.amount, 0) * 100, 2) AS margin_rate',
  '  FROM dws_store_sales d',
  '  JOIN db_model.demo_stores s ON s.store_id = d.store_id',
].join(' ')

const SQL_ADS_MEMBER = [
  'SELECT t.member_level AS member_level, t.members AS members, t.amount AS amount,',
  '       ROUND(t.amount / NULLIF(t.members, 0), 2) AS avg_amount, t.order_count AS order_count,',
  '       ROUND(t.repeat_members / NULLIF(t.members, 0) * 100, 2) AS repurchase_rate',
  '  FROM (SELECT o.member_level AS member_level, COUNT(DISTINCT o.member_id) AS members,',
  '               COUNT(DISTINCT CASE WHEN o.cnt >= 2 THEN o.member_id END) AS repeat_members,',
  '               SUM(o.amount) AS amount, SUM(o.cnt) AS order_count',
  '          FROM (SELECT member_level, member_id, SUM(pay_amount) AS amount, COUNT(order_id) AS cnt',
  '                   FROM ods_order_detail WHERE member_id > 0',
  '                  GROUP BY member_level, member_id) o',
  '         GROUP BY o.member_level) t',
].join(' ')

const SQL_ADS_FEED = [
  'SELECT order_id AS feed_id, created_at AS feed_time, store_name AS store_name, city AS city,',
  '       category AS category, channel AS channel, pay_amount AS amount',
  '  FROM ods_order_detail',
  ' ORDER BY created_at DESC',
  ' LIMIT 40',
].join(' ')

let nodeSeq = 0
const inputNode = (name, x, y, config) => ({ id: `i${++nodeSeq}`, type: 'input', name, x, y, config })
const filterNode = (name, x, y, config) => ({ id: `f${++nodeSeq}`, type: 'filter', name, x, y, config })
const aggNode = (name, x, y, config) => ({ id: `a${++nodeSeq}`, type: 'aggregate', name, x, y, config })
const outputNode = (name, x, y, config) => ({ id: `o${++nodeSeq}`, type: 'output', name, x, y, config })
const chain = (nodes) => ({
  nodes,
  edges: nodes.slice(1).map((n, idx) => ({ id: `e${idx + 1}`, source: nodes[idx].id, target: n.id })),
})

const ODS_COLS = ['order_id', 'stat_date', 'stat_hour', 'region', 'city', 'store_id', 'store_name',
  'channel', 'category', 'member_id', 'member_level', 'pay_amount', 'profit', 'qty', 'order_status', 'created_at']

/** 每条任务的 DAG 与目标表列清单（目标列顺序 = 建表列顺序，便于与设计器画布对照） */
const TASKS = [
  {
    id: 911,
    name: 'ODS 订单明细入仓',
    description: '业务库有效订单关联门店/会员后落到分析库 ODS。取数用自定义 SQL（一次 JOIN 搞定维度退化），'
      + '有效口径 = 状态 PAID/SHIPPED。目标表主键 order_id：重跑前要先清空 ods_order_detail，否则会报主键冲突而不是悄悄翻倍。',
    source: DS_BIZ, target: DS_DWH, sourceTable: 'demo_orders', targetTable: 'ods_order_detail',
    cron: '30 1 * * *',
    dag: chain([
      inputNode('订单明细(业务库)', 60, 140, { datasourceId: DS_BIZ, sql: SQL_ODS }),
      outputNode('写入 ods_order_detail', 380, 140, {
        datasourceId: DS_DWH, tableName: 'ods_order_detail', writeMode: 'append', columns: ODS_COLS,
      }),
    ]),
    outCols: ODS_COLS,
  },
  {
    id: 912,
    name: 'DWD 日销售汇总',
    description: 'ODS 明细按 日期×大区×品类×渠道 汇总。分组字段只能是普通列，所以 stat_date 在 ODS 阶段就投影好了；'
      + '这段 DAG 与装载 dwd_sales_daily 的 INSERT..SELECT 语义一致，点执行可复算同一批数。',
    source: DS_DWH, target: DS_DWH, sourceTable: 'ods_order_detail', targetTable: 'dwd_sales_daily',
    cron: '0 2 * * *',
    dag: chain([
      inputNode('ODS 明细', 60, 140, {
        datasourceId: DS_DWH, tableName: 'ods_order_detail',
        columns: ['stat_date', 'region', 'category', 'channel', 'pay_amount', 'profit', 'order_id', 'qty'],
      }),
      aggNode('按日期/大区/品类/渠道汇总', 300, 140, {
        groupBy: ['stat_date', 'region', 'category', 'channel'],
        aggregations: [
          { field: 'pay_amount', function: 'sum', alias: 'amount' },
          { field: 'profit', function: 'sum', alias: 'profit' },
          { field: 'order_id', function: 'count', alias: 'order_count' },
          { field: 'qty', function: 'sum', alias: 'qty' },
        ],
      }),
      outputNode('写入 dwd_sales_daily', 560, 140, {
        datasourceId: DS_DWH, tableName: 'dwd_sales_daily', writeMode: 'append',
        columns: ['stat_date', 'region', 'category', 'channel', 'amount', 'profit', 'order_count', 'qty'],
      }),
    ]),
    outCols: ['stat_date', 'region', 'category', 'channel', 'amount', 'profit', 'order_count', 'qty'],
  },
  {
    id: 913,
    name: 'DWS 门店销售汇总',
    description: 'ODS 明细按门店汇总，产出金额/毛利/单量/客单，供门店排行与绩效层复用。',
    source: DS_DWH, target: DS_DWH, sourceTable: 'ods_order_detail', targetTable: 'dws_store_sales',
    cron: '10 2 * * *',
    dag: chain([
      inputNode('ODS 明细', 60, 140, {
        datasourceId: DS_DWH, tableName: 'ods_order_detail',
        columns: ['store_id', 'store_name', 'region', 'city', 'pay_amount', 'profit', 'order_id'],
      }),
      aggNode('按门店汇总', 300, 140, {
        groupBy: ['store_id', 'store_name', 'region', 'city'],
        aggregations: [
          { field: 'pay_amount', function: 'sum', alias: 'amount' },
          { field: 'profit', function: 'sum', alias: 'profit' },
          { field: 'order_id', function: 'count', alias: 'order_count' },
          { field: 'pay_amount', function: 'avg', alias: 'avg_pay_amount' },
        ],
      }),
      outputNode('写入 dws_store_sales', 560, 140, {
        datasourceId: DS_DWH, tableName: 'dws_store_sales', writeMode: 'append',
        columns: ['store_id', 'store_name', 'region', 'city', 'amount', 'profit', 'order_count', 'avg_pay_amount'],
      }),
    ]),
    outCols: ['store_id', 'store_name', 'region', 'city', 'amount', 'profit', 'order_count', 'avg_pay_amount'],
  },
  {
    id: 914,
    name: 'DWS 会员等级汇总',
    description: '散客（member_id=0）先用过滤节点剔掉，再按会员等级汇总 —— 演示 filter→aggregate 两段串联，'
      + '过滤条件写在 SQL 里还是节点里效果一样，放节点里是为了在画布上看出口径。',
    source: DS_DWH, target: DS_DWH, sourceTable: 'ods_order_detail', targetTable: 'dws_member_level',
    cron: '20 2 * * *',
    dag: chain([
      inputNode('ODS 明细', 60, 140, {
        datasourceId: DS_DWH, tableName: 'ods_order_detail',
        columns: ['member_id', 'member_level', 'pay_amount', 'order_id'],
      }),
      filterNode('剔除散客', 300, 140, { filters: [{ field: 'member_id', operator: 'gt', value: 0 }] }),
      aggNode('按会员等级汇总', 540, 140, {
        groupBy: ['member_level'],
        aggregations: [
          { field: 'pay_amount', function: 'sum', alias: 'amount' },
          { field: 'order_id', function: 'count', alias: 'order_count' },
          { field: 'pay_amount', function: 'avg', alias: 'avg_pay_amount' },
        ],
      }),
      outputNode('写入 dws_member_level', 780, 140, {
        datasourceId: DS_DWH, tableName: 'dws_member_level', writeMode: 'append',
        columns: ['member_level', 'amount', 'order_count', 'avg_pay_amount'],
      }),
    ]),
    outCols: ['member_level', 'amount', 'order_count', 'avg_pay_amount'],
  },
  {
    id: 915,
    name: 'ADS 门店绩效（关联月度目标）',
    description: '门店汇总要关联业务库的月度目标才能算达成率/毛利率，跨库 JOIN 用自定义 SQL 节点最省事；'
      + '达成率是现算的（amount/target_amount），不是写进演示数据的死数。',
    source: DS_DWH, target: DS_DWH, sourceTable: 'dws_store_sales', targetTable: 'ads_store_performance',
    cron: '40 2 * * *',
    dag: chain([
      inputNode('门店汇总×月度目标', 60, 140, { datasourceId: DS_DWH, sql: SQL_ADS_STORE }),
      outputNode('写入 ads_store_performance', 400, 140, {
        datasourceId: DS_DWH, tableName: 'ads_store_performance', writeMode: 'append',
        columns: ['store_id', 'store_name', 'region', 'city', 'amount', 'profit', 'order_count',
          'avg_pay_amount', 'target_amount', 'achievement_rate', 'margin_rate'],
      }),
    ]),
    outCols: ['store_id', 'store_name', 'region', 'city', 'amount', 'profit', 'order_count',
      'avg_pay_amount', 'target_amount', 'achievement_rate', 'margin_rate'],
  },
  {
    id: 916,
    name: 'ADS 时段热力',
    description: '按下单小时汇总，供大屏热力条/时段折线用。行数少（一天 12 个时段），聚合节点直接算完。',
    source: DS_DWH, target: DS_DWH, sourceTable: 'ods_order_detail', targetTable: 'ads_hour_heat',
    cron: '50 2 * * *',
    dag: chain([
      inputNode('ODS 明细', 60, 140, {
        datasourceId: DS_DWH, tableName: 'ods_order_detail',
        columns: ['stat_hour', 'order_id', 'pay_amount'],
      }),
      aggNode('按小时汇总', 300, 140, {
        groupBy: ['stat_hour'],
        aggregations: [
          { field: 'order_id', function: 'count', alias: 'orders' },
          { field: 'pay_amount', function: 'sum', alias: 'amount' },
          { field: 'pay_amount', function: 'avg', alias: 'avg_amount' },
        ],
      }),
      outputNode('写入 ads_hour_heat', 560, 140, {
        datasourceId: DS_DWH, tableName: 'ads_hour_heat', writeMode: 'append',
        columns: ['stat_hour', 'orders', 'amount', 'avg_amount'],
      }),
    ]),
    outCols: ['stat_hour', 'orders', 'amount', 'avg_amount'],
  },
  {
    id: 917,
    name: 'ADS 会员价值分层',
    description: '复购率要按会员去重再判 ≥2 单，聚合算子没有 distinct 能力，所以这段用自定义 SQL 一次算完，'
      + '把「算子能做的用画布、算子做不了的用 SQL」这条实际用法留在演示里。',
    source: DS_DWH, target: DS_DWH, sourceTable: 'ods_order_detail', targetTable: 'ads_member_value',
    cron: '0 3 * * *',
    dag: chain([
      inputNode('会员分层去重', 60, 140, { datasourceId: DS_DWH, sql: SQL_ADS_MEMBER }),
      outputNode('写入 ads_member_value', 400, 140, {
        datasourceId: DS_DWH, tableName: 'ads_member_value', writeMode: 'append',
        columns: ['member_level', 'members', 'amount', 'avg_amount', 'order_count', 'repurchase_rate'],
      }),
    ]),
    outCols: ['member_level', 'members', 'amount', 'avg_amount', 'order_count', 'repurchase_rate'],
  },
  {
    id: 918,
    name: 'ADS 实时成交流水',
    description: '大屏滚动列表用的最近 40 笔。取数节点带 ORDER BY/LIMIT，目标表主键 feed_id=order_id，'
      + '所以这也是一条「全量刷新」任务：重跑先清空 ads_realtime_feed。',
    source: DS_DWH, target: DS_DWH, sourceTable: 'ods_order_detail', targetTable: 'ads_realtime_feed',
    cron: '*/10 * * * *',
    dag: chain([
      inputNode('最近 40 笔成交', 60, 140, { datasourceId: DS_DWH, sql: SQL_ADS_FEED }),
      outputNode('写入 ads_realtime_feed', 400, 140, {
        datasourceId: DS_DWH, tableName: 'ads_realtime_feed', writeMode: 'append',
        columns: ['feed_id', 'feed_time', 'store_name', 'city', 'category', 'channel', 'amount'],
      }),
    ]),
    outCols: ['feed_id', 'feed_time', 'store_name', 'city', 'category', 'channel', 'amount'],
  },
]

/** 单引号包裹 SQL/JSON：反斜杠和单引号都要转义（MySQL 默认接受 \ 转义） */
const str = (v) => `'${String(v).replace(/\\/g, '\\\\').replace(/'/g, "''")}'`

const lines = []
lines.push('-- BD3：演示数据包的「数据源 + ETL 设计」配置')
lines.push('-- 由 devlog/_ay/bd3-gen-etl.mjs 生成；不要手改本文件，改脚本后重新生成。')
lines.push('-- 前置：已执行 2026-10-18-bd-demo-data.sql（业务库三层数据 + db_analysis 分层表）')
lines.push('-- 回滚：见文件末尾的 rollback 注释块')
lines.push('')
lines.push('SET NAMES utf8mb4;')
lines.push('')
lines.push('-- ============ 1. 数据源：把话说清楚 ============')
lines.push("UPDATE db_datasource.datasource SET description = '分析库（数据源 901）：承载 ODS/DWD/DWS/ADS 四层演示表，ETL 目标端与所有数据集查询都走它' WHERE id = 901;")
lines.push("UPDATE db_datasource.datasource SET description = '订单业务库（数据源 902）：demo_orders / demo_stores / demo_members 三张源表，ETL 起点' WHERE id = 902;")
lines.push('-- ClickHouse 驱动没打进 datasource-service 的包（classpath 里只有 mysql-connector），')
lines.push('-- 挂着「可用」只会让人反复点测试连接然后怀疑自己。按实际能力置为不可用。')
lines.push("UPDATE db_datasource.datasource SET status = 0, description = '行为日志集群（示例，未启用）：平台当前只内置 MySQL JDBC 驱动，该类型测试连接必然失败；接入需先给 datasource-service 引入 clickhouse-jdbc 并重新打包' WHERE id = 903;")
lines.push("UPDATE db_datasource.datasource SET description = '第三方指标开放接口（示例，未启用）：非 JDBC 类型不参与 SQL 取数，仅作列表形态展示' WHERE id = 904;")
lines.push('')
lines.push('-- ============ 2. 旧任务：按当前引擎能力标注，不假装能跑 ============')
lines.push('-- 引擎只实现 append，分组字段只认普通列，非 JDBC 源不能取数 —— 三条旧任务分别踩了这三种写法。')
lines.push('-- CONCAT 追加而不是覆盖：原说明是这批演示数据自带的，不该被我改掉；NOT LIKE 让补丁可重复执行而不重复追加。')
lines.push('-- 停摆与补文案分两条：状态要无条件收口到 STOPPED，说明只在没标注过时追加。')
const legacyNote = (id, note) => {
  lines.push(`UPDATE db_etl.etl_task SET status = 'STOPPED' WHERE id = ${id};`)
  lines.push(`UPDATE db_etl.etl_task SET description = CONCAT(COALESCE(description, ''), ${str(' 【旧任务对照】' + note)})`)
  lines.push(`  WHERE id = ${id} AND description NOT LIKE '%旧任务对照%';`)
}
legacyNote(901, '写入方式 upsert 当前引擎不支持（只实现 append），点执行会明确报错；等价的可执行版见 912。')
legacyNote(902, '源端与目标端同表，append 跑一次翻一倍；仅作画布示例，别点执行。')
legacyNote(903, '源端 904 是 api 型数据源（非 JDBC，已停用），且 overwrite 未实现；等 ClickHouse/API 驱动接入后再启用。')
lines.push('')
lines.push('-- ============ 3. 可执行的演示 DAG（911~918） ============')
lines.push('-- 状态一律 STOPPED：调度字段只是设计意图，etl-service 里没有 @Scheduled 自动触发，')
lines.push('-- 跑一次靠界面上的「执行」，跑完 last_run_status 由引擎自己写。')
lines.push('-- 历史（etl_task_log/instance）这里刻意不预填：这 8 条任务还没被引擎跑过，')
lines.push('-- 造一条成功日志等于把「全流程打通」写成假账。')
for (const t of TASKS) {
  const json = JSON.stringify(t.dag)
  lines.push('')
  lines.push(`-- ${t.id} ${t.name}：${t.sourceTable} -> ${t.targetTable}`)
  lines.push('DELETE FROM db_etl.etl_task WHERE id = ' + t.id + ';')
  lines.push('INSERT INTO db_etl.etl_task')
  lines.push('  (id, tenant_id, name, description, source_datasource_id, target_datasource_id,')
  lines.push('   status, schedule_cron, source_table, target_table, transform_config, deleted)')
  lines.push('VALUES (' + t.id + ', 1, ' + str(t.name) + ', ' + str(t.description) + ', ' + t.source + ', ' + t.target + ',')
  lines.push("  'STOPPED', " + str(t.cron) + ', ' + str(t.sourceTable) + ', ' + str(t.targetTable) + ', ' + str(json) + ', 0);')
}
lines.push('')
lines.push('-- ============ 4. 自检 ============')
lines.push("SELECT '任务总数(演示 8 条 + 旧 3 条)' AS check_name, COUNT(*) AS value FROM db_etl.etl_task WHERE id BETWEEN 901 AND 918 AND deleted = 0;")
lines.push("SELECT 'transform_config 非法 JSON 条数(必须 0)' AS check_name, COUNT(*) AS value FROM db_etl.etl_task WHERE id BETWEEN 911 AND 918 AND JSON_VALID(transform_config) = 0;")
lines.push("SELECT '写入方式非 append 的新任务(必须 0)' AS check_name, COUNT(*) AS value FROM db_etl.etl_task")
lines.push("  WHERE id BETWEEN 911 AND 918 AND transform_config LIKE '%overwrite%' AND transform_config NOT LIKE '%append%';")
lines.push("-- 分组字段是否只认普通列这类判据要拆 JSON，MySQL 5.7 没有 JSON_TABLE，交给 devlog/_ay/bd3-contract.cjs 逐节点核。")
lines.push("SELECT '每条新任务都有源端与目标节点' AS check_name,")
lines.push("  CONCAT(SUM(JSON_EXTRACT(transform_config, '$.nodes[0].type') = 'input'")
lines.push("              AND JSON_LENGTH(JSON_EXTRACT(transform_config, '$.nodes')) >= 2), '/', COUNT(*)) AS value")
lines.push('  FROM db_etl.etl_task WHERE id BETWEEN 911 AND 918;')
lines.push("SELECT '引用的数据源存在且可用' AS check_name,")
lines.push("  CONCAT(SUM(d.status = 1), '/', COUNT(*)) AS value")
lines.push('  FROM db_etl.etl_task t')
lines.push('  JOIN db_datasource.datasource d ON d.id IN (t.source_datasource_id, t.target_datasource_id) AND d.deleted = 0')
lines.push(' WHERE t.id BETWEEN 911 AND 918;')
lines.push("SELECT '新任务的历史条数(必须 0：还没跑过就不该有记录)' AS check_name, COUNT(*) AS value FROM db_etl.etl_task_log WHERE task_id BETWEEN 911 AND 918;")
lines.push('')
lines.push('-- 回滚（需要时手工执行）：')
lines.push('--   DELETE FROM db_etl.etl_task WHERE id BETWEEN 911 AND 918;')
lines.push('--   UPDATE db_datasource.datasource SET status = 1 WHERE id = 903;')
lines.push('')

fs.writeFileSync(OUT, lines.join('\n'), 'utf8')

/**
 * 源端取数探针：把每个 input 节点真正要跑的 SQL 套一层 LIMIT 1，只看列名（多带一行样本，无副作用）。
 * 不点界面上的「执行」也能证明「DAG 声明的列 == 数据源实际返回的列」——
 * 引擎里最安静的失败就是列名对不上：聚合节点取不到值就当 0 写进目标表。
 * LIMIT 0 拿不到列名（mysql 批处理模式空结果集不打表头），所以用 1。
 */
const probes = TASKS.flatMap((t) => t.dag.nodes.filter((n) => n.type === 'input').map((n) => ({
  taskId: t.id, taskName: t.name, nodeId: n.id, nodeName: n.name, datasourceId: n.config.datasourceId,
  sql: (n.config.sql || `SELECT ${n.config.columns.join(', ')} FROM ${n.config.tableName}`).replace(/\s+/g, ' ').trim(),
})))
const SCHEMA_BY_DS = { [DS_BIZ]: 'db_model', [DS_DWH]: 'db_analysis' }
const probeSql = ['-- 由 bd3-gen-etl.mjs 生成：逐节点验证源端 SQL 的返回列（LIMIT 0，不落数据）', 'SET NAMES utf8mb4;', '']
let lastDs = null
for (const p of probes) {
  // 探针要按各数据源的默认库跑，否则未限定的表名在 root 会话里找不到
  if (p.datasourceId !== lastDs) {
    probeSql.push(`USE ${SCHEMA_BY_DS[p.datasourceId]};`)
    lastDs = p.datasourceId
  }
  probeSql.push(`SELECT '@@PROBE ${p.taskId} ${p.nodeId}' AS marker;`)
  probeSql.push(`SELECT * FROM (${p.sql}) probe_node LIMIT 1;`)
}
probeSql.push("SELECT '@@PROBE done' AS marker;")
fs.writeFileSync('devlog/_ay/bd3-probe.sql', probeSql.join('\n') + '\n', 'utf8')
fs.writeFileSync('devlog/_ay/bd3-probe-plan.json', JSON.stringify(probes, null, 2), 'utf8')

// 契约自检需要的列清单（供 devlog/_ay/bd3-contract.cjs 与真实 information_schema 对照）
fs.writeFileSync('devlog/_ay/bd3-contract.json', JSON.stringify(TASKS.map((t) => ({
  id: t.id, name: t.name, targetTable: t.targetTable, outCols: t.outCols, dag: t.dag,
})), null, 2), 'utf8')

console.log('生成 ' + OUT + '，任务 ' + TASKS.length + ' 条')
