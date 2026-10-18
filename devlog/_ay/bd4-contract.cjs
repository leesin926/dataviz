/**
 * BD4 契约自检：把"补丁里声明的口径"和"库里真的写进去的东西 + 数据源真的能返回的列"三方对齐。
 *
 * 要挡住的失败类别只有一个 —— 静默为空：
 *   数据集 JSON 里的 field 拼错一个字母，前端字段面板照常显示、查询照常 200，
 *   图上就是一条空系列。BD1/API-108、BD3 的 DAG 自检踩的都是同一类，这里在配置阶段就拦住。
 *
 * 输入四份读数（都是 mysql -B 的原始输出，跑法见文件末尾）：
 *   devlog/bd4-probe.out    每个数据集来源 LIMIT 1 的真实列名
 *   devlog/bd4-count.out    每个数据集来源的行数
 *   devlog/bd4-readback.out 补丁写进去的 911+ 段原样回读（分隔符 ' ~|~ '，不是制表符：-B 会把制表符转义成字面 \t）
 *   devlog/_ay/bd4-contract.json  声明口径（生成器产出）
 *
 * 判据来自源码，不是猜的：
 *   SqlBuilder.IDENTIFIER / OPERATORS / AGGREGATIONS / resolveFrom 的单语句-SELECT 约束
 *   shared-types query.ts 的 QueryDimension.type、QueryMeasure.aggregation
 *   DataPermissionResolver.scopeMatches 只认 USER / ROLE
 */
const fs = require('node:fs')

const CONTRACT = JSON.parse(fs.readFileSync('E:/wowowo/devlog/_ay/bd4-contract.json', 'utf8'))
const probeOut = fs.readFileSync('E:/wowowo/devlog/bd4-probe.out', 'utf8')
const countOut = fs.readFileSync('E:/wowowo/devlog/bd4-count.out', 'utf8')
const readOut = fs.readFileSync('E:/wowowo/devlog/bd4-readback.out', 'utf8')

/** SqlBuilder.AGGREGATIONS 的键 + distinct 三种写法（canonicalize = 小写 + 去下划线 + 去空格） */
const AGG_WHITELIST = new Set(['sum', 'avg', 'count', 'max', 'min', 'distinct', 'countdistinct'])
/** SqlBuilder.OPERATORS 的键（同样先做 canonicalize） */
const OPERATOR_WHITELIST = new Set(['=', 'eq', '!=', 'ne', '<>', '>', 'gt', '>=', 'gte', '<', 'lt', '<=', 'lte', 'like', 'notlike', 'in', 'notin', 'between', 'notbetween', 'isnull', 'isnotnull'])
const IDENTIFIER = /^[A-Za-z_][A-Za-z0-9_]{0,63}$/
/** 报告数据走 SELECT *，默认 limit 1000 —— 超过就是"图上只有前 1000 行" */
const REPORT_ROW_CEILING = 1000

const errors = []
const warns = []
const fail = (where, msg) => errors.push(`${where}：${msg}`)
const canonical = (v) => String(v ?? '').trim().toLowerCase().replace(/_/g, '').replace(/ /g, '')

/**
 * 声明口径与库内 JSON 的比对要做归一：
 * 生成器只在 format 有值时才写这个键（'category' 维度不该带 "format":null），
 * 契约文件里则是显式 null。归一到同一个键序、丢掉 nullish 之后再比，才既严又不假报。
 */
const normDims = (arr) => (arr ?? []).map((x) => ({ field: x.field, displayName: x.displayName, type: x.type, ...(x.format ? { format: x.format } : {}) }))
const normMets = (arr) => (arr ?? []).map((x) => ({ field: x.field, displayName: x.displayName, aggregation: x.aggregation, ...(x.format ? { format: x.format } : {}) }))
const normFilters = (arr) => (arr ?? []).map((x) => ({ field: x.field, operator: x.operator, value: x.value }))

/** -B 输出：SELECT 表达式回显行不以 @@ 开头，是噪声；结果行的第一段就是标记，字段从第二段起 */
function rowsOf(text, tag) {
  return text.split(/\r?\n/).filter((l) => l.startsWith(tag)).map((l) => l.split(' ~|~ ').slice(1).map((s) => s.trim()))
}

/** 探针输出是"marker 行 + 表头行 + 数据行"三段，表头即真实返回列名 */
function probeColumns(text) {
  const lines = text.split(/\r?\n/)
  const out = new Map()
  lines.forEach((line, i) => {
    const m = /^@@PROBE (\d+)$/.exec(line.trim())
    if (!m) return
    const header = lines[i + 1]
    if (!header || header.startsWith('@@PROBE') || !header.includes('\t')) {
      out.set(Number(m[1]), null)
      return
    }
    out.set(Number(m[1]), header.split('\t'))
  })
  return out
}

const columns = probeColumns(probeOut)
const rowCount = new Map(rowsOf(countOut, '@@CNT').map((r) => [Number(r[0]), Number(r[1])]))
const mdRows = rowsOf(readOut, '@@MD')
const dimRows = rowsOf(readOut, '@@DIM')
const metRows = rowsOf(readOut, '@@MET')
const rptRows = rowsOf(readOut, '@@RPT')
const ruleRows = rowsOf(readOut, '@@RULE')
const dsRows = rowsOf(readOut, '@@DS')

const mdBy = new Map(mdRows.map((r) => [Number(r[0]), r]))
const dsStatus = new Map(dsRows.map((r) => [Number(r[0]), { name: r[1], status: Number(r[2]) }]))
const dimsByDataset = new Map()
const metsByDataset = new Map()
for (const r of dimRows) {
  const dsId = Number(r[1])
  if (!dimsByDataset.has(dsId)) dimsByDataset.set(dsId, [])
  dimsByDataset.get(dsId).push({ id: Number(r[0]), field: r[2], type: r[3], table: r[4], dataType: r[5], sort: Number(r[6]) })
}
for (const r of metRows) {
  const dsId = Number(r[1])
  if (!metsByDataset.has(dsId)) metsByDataset.set(dsId, [])
  metsByDataset.get(dsId).push({ id: Number(r[0]), field: r[2], agg: r[3], table: r[4], dataType: r[5], sort: Number(r[6]) })
}
const rptBy = new Map(rptRows.map((r) => [Number(r[0]), r]))
const ruleBy = new Map(ruleRows.map((r) => [Number(r[0]), r]))

const declaredById = new Map(CONTRACT.datasets.map((d) => [d.id, d]))

let checkedNodes = 0
let checkedFields = 0

// ---------- 1. 数据集：声明 == 库内，且来源真的能返回这些列 ----------
for (const d of CONTRACT.datasets) {
  const where = `数据集 ${d.id} ${d.name}`
  const row = mdBy.get(d.id)
  if (!row) { fail(where, '库里没有这行'); continue }
  checkedNodes++

  const [, dsId, table, sqlQuery, dimsJson, metsJson, filtersJson] = row
  if (Number(dsId) !== d.datasourceId) fail(where, `数据源 ${dsId} 与声明 ${d.datasourceId} 不符`)
  const status = dsStatus.get(Number(dsId))
  if (!status) fail(where, `数据源 ${dsId} 在 datasource 表里不存在`)
  else if (status.status !== 1) fail(where, `数据源 ${dsId}（${status.name}）status=${status.status} 不可用，查询必然连不上`)

  const hasTable = table !== 'NULL'
  const hasSql = sqlQuery !== 'NULL'
  if (hasTable === hasSql) fail(where, 'table_name 与 sql_query 必须恰好一个非空，两个都有或都没有都说明配置写坏了')

  if (hasTable && !IDENTIFIER.test(table)) fail(where, `表名 ${table} 不是 SqlBuilder 认的裸标识符（不能带库名前缀）`)
  if (hasSql) {
    const single = sqlQuery.replace(/;\s*$/, '').trim()
    if (single.includes(';')) fail(where, '自定义 SQL 含多条语句，resolveFrom 会直接拒绝')
    if (!single.toUpperCase().startsWith('SELECT')) fail(where, '自定义 SQL 不是 SELECT 开头，resolveFrom 会拒绝')
    if (/\n/.test(sqlQuery)) fail(where, '自定义 SQL 里有换行 —— 存进去的应是单行')
  }

  const src = d.sqlQuery ? d.sqlQuery : `表 ${d.table}`
  const cols = columns.get(d.id)
  if (!cols) fail(where, `探针没能从 ${src} 取到列名（查询本身就跑不了）`)
  else {
    const set = new Set(cols)
    const parse = (raw, label) => {
      let arr
      try { arr = JSON.parse(raw) } catch (e) { fail(where, `${label} 不是合法 JSON：${e.message}`); return [] }
      if (!Array.isArray(arr)) { fail(where, `${label} 不是数组`); return [] }
      return arr
    }
    const declaredDims = d.dimensions
    const dbDims = parse(dimsJson, 'dimensions')
    const dbMets = parse(metsJson, 'metrics')
    const dbFilters = parse(filtersJson, 'filters')

    if (JSON.stringify(normDims(dbDims)) !== JSON.stringify(normDims(declaredDims))) fail(where, '库里 dimensions 与声明不一致')
    if (JSON.stringify(normMets(dbMets)) !== JSON.stringify(normMets(d.metrics))) fail(where, '库里 metrics 与声明不一致')
    if (JSON.stringify(normFilters(dbFilters)) !== JSON.stringify(normFilters(d.filters))) fail(where, '库里 filters 与声明不一致')

    for (const x of dbDims) {
      checkedFields++
      if (!IDENTIFIER.test(x.field)) fail(where, `维度字段 ${x.field} 不是合法标识符`)
      if (!set.has(x.field)) fail(where, `维度字段 ${x.field} 不在 ${src} 的真实返回列里 —— 图上就是一条空系列`)
      if (x.type !== 'category' && x.type !== 'time') fail(where, `维度 ${x.field} 的 type=${x.type} 前端 QueryDimension 只认 category/time`)
      if (!x.displayName) fail(where, `维度 ${x.field} 没有 displayName，字段面板只能显示物理列名`)
    }
    for (const x of dbMets) {
      checkedFields++
      if (!IDENTIFIER.test(x.field)) fail(where, `指标字段 ${x.field} 不是合法标识符`)
      if (!set.has(x.field)) fail(where, `指标字段 ${x.field} 不在 ${src} 的真实返回列里 —— 系列恒为 null`)
      if (!AGG_WHITELIST.has(canonical(x.aggregation))) fail(where, `指标 ${x.field} 的聚合 ${x.aggregation} 不在 SqlBuilder 白名单里，查询会 400`)
      if (!x.displayName) fail(where, `指标 ${x.field} 没有 displayName`)
    }
    for (const x of dbFilters) {
      checkedFields++
      if (!set.has(x.field)) fail(where, `过滤字段 ${x.field} 不在 ${src} 的真实返回列里`)
      if (!OPERATOR_WHITELIST.has(canonical(x.operator))) fail(where, `过滤操作符 ${x.operator} 不在 SqlBuilder 白名单里`)
      if (x.value === null || x.value === undefined) fail(where, `过滤条件 ${x.field} 没有值，literal() 会直接 400`)
    }

    // 物理表型：SqlBuilder 查的是数据集绑定的数据源，列名以探针为准；自定义 SQL 型的别名同样来自探针
    if (!hasTable) {
      for (const x of [...dbDims, ...dbMets]) {
        if (/^(DATE|ROUND|SUM|COUNT)/i.test(x.field)) fail(where, `字段 ${x.field} 像是表达式而不是别名，别名必须等于 SELECT 里的 AS 名`)
      }
    }
  }

  // 字段字典要和 JSON 一一对应（两张表是数据集的展开形态，对不上就是"字典里查不到这个字段"）
  const dictDims = dimsByDataset.get(d.id) ?? []
  const dictMets = metsByDataset.get(d.id) ?? []
  const jsonDims = JSON.parse(mdBy.get(d.id)[4] || '[]')
  const jsonMets = JSON.parse(mdBy.get(d.id)[5] || '[]')
  if (dictDims.map((x) => x.field).join() !== jsonDims.map((x) => x.field).join()) fail(where, `model_dimension 与 JSON 维度不一致：${dictDims.map((x) => x.field)} vs ${jsonDims.map((x) => x.field)}`)
  if (dictMets.map((x) => x.field).join() !== jsonMets.map((x) => x.field).join()) fail(where, `model_metric 与 JSON 指标不一致：${dictMets.map((x) => x.field)} vs ${jsonMets.map((x) => x.field)}`)
  if (hasTable) {
    for (const x of [...dictDims, ...dictMets]) if (x.table !== table) fail(where, `字典行 ${x.field} 的 table_name=${x.table} 与数据集 ${table} 不符`)
  } else {
    for (const x of [...dictDims, ...dictMets]) if (x.table !== 'NULL') fail(where, `自定义 SQL 数据集的字典行 ${x.field} 却写了 table_name=${x.table}`)
  }
  dictDims.forEach((x, i) => { if (x.sort !== i + 1) fail(where, `维度 ${x.field} 的 sort=${x.sort} 与 JSON 顺序 ${i + 1} 不符`) })
  dictMets.forEach((x, i) => { if (x.sort !== i + 1) fail(where, `指标 ${x.field} 的 sort=${x.sort} 与 JSON 顺序 ${i + 1} 不符`) })
}

// ---------- 2. 报告：字段必须来自数据集声明，来源行数必须在默认 limit 之内 ----------
for (const r of CONTRACT.reports) {
  const where = `报告 ${r.id} ${r.name}`
  const row = rptBy.get(r.id)
  if (!row) { fail(where, '库里没有这行'); continue }
  checkedNodes++
  const [, datasetId, published, configJson] = row
  if (Number(published) !== r.isPublished) fail(where, `发布状态 ${published} 与声明 ${r.isPublished} 不符`)
  const ds = declaredById.get(Number(datasetId))
  if (!ds) { fail(where, `挂的数据集 ${datasetId} 不在本数据包里`); continue }
  let config
  try { config = JSON.parse(configJson) } catch (e) { fail(where, `config 不是合法 JSON：${e.message}`); continue }
  const dimFields = new Set(ds.dimensions.map((x) => x.field))
  const metFields = new Set(ds.metrics.map((x) => x.field))
  for (const x of config.dimensions ?? []) {
    checkedFields++
    if (!dimFields.has(x.field)) fail(where, `维度 ${x.field} 不是数据集 ${ds.id} 声明的维度`)
  }
  for (const x of config.metrics ?? []) {
    checkedFields++
    if (!metFields.has(x.field)) fail(where, `指标 ${x.field} 不是数据集 ${ds.id} 声明的指标`)
    if (x.aggFunction && !AGG_WHITELIST.has(canonical(x.aggFunction))) fail(where, `聚合 ${x.aggFunction} 不在白名单`)
    if (!x.alias) fail(where, `指标 ${x.field} 没写 alias，结果列名会回落成 ${canonical(x.aggFunction)}_${x.field}`)
  }
  for (const x of config.filters ?? []) {
    checkedFields++
    if (!dimFields.has(x.field) && !metFields.has(x.field)) fail(where, `过滤字段 ${x.field} 数据集里不存在`)
    if (!OPERATOR_WHITELIST.has(canonical(x.operator))) fail(where, `过滤操作符 ${x.operator} 不在白名单`)
  }
  for (const x of config.orders ?? []) {
    if (!dimFields.has(x.field) && !metFields.has(x.field)) fail(where, `排序字段 ${x.field} 数据集里不存在`)
    if (String(x.direction).toUpperCase() !== 'ASC' && String(x.direction).toUpperCase() !== 'DESC') fail(where, `排序方向 ${x.direction} 非法`)
  }
  const n = rowCount.get(Number(datasetId))
  if (n === undefined) fail(where, `没有数据集 ${ds.id} 的行数读数`)
  else if (Number(published) === 1 && n > REPORT_ROW_CEILING) fail(where, `来源 ${n} 行 > 默认 limit ${REPORT_ROW_CEILING}，getReportData 走 SELECT * 只会画前 ${REPORT_ROW_CEILING} 行`)
}

// ---------- 3. 行列权限规则：字段存在、操作符合白名单、命中方式认得 ----------
for (const r of CONTRACT.rules) {
  const where = `规则 ${r.id} ${r.name}`
  const row = ruleBy.get(r.id)
  if (!row) { fail(where, '库里没有这行'); continue }
  checkedNodes++
  const [, datasetId, scopeType, scopeValue, conditionsJson, restricted, enabled] = row
  const ds = declaredById.get(Number(datasetId))
  if (!ds) { fail(where, `作用数据集 ${datasetId} 不存在`); continue }
  if (Number(enabled) !== r.enabled) fail(where, '启用状态与声明不符')
  if (scopeType !== 'USER' && scopeType !== 'ROLE') fail(where, `命中方式 ${scopeType} DataPermissionResolver 不认，会被当成不匹配`)
  if (scopeType === 'ROLE' && !/^(super_admin|admin|user)$/.test(scopeValue)) warns.push(`${where}：角色码 ${scopeValue} 不在 sys_role 里，这条规则谁都命中不了`)
  if (scopeType === 'USER' && !/^\d+$/.test(scopeValue)) fail(where, `USER 型规则要存用户ID，收到 ${scopeValue}`)
  const cols = columns.get(Number(datasetId)) ?? []
  const hasCond = conditionsJson !== 'NULL'
  const noCond = restricted === 'NULL'
  if (!hasCond && noCond) fail(where, '行条件和列权限都是空 —— 一条什么都不限制的启用规则只会触发 deny-all')
  if (hasCond) {
    let parsed
    try { parsed = JSON.parse(conditionsJson) } catch (e) { fail(where, `行条件不是合法 JSON：${e.message}`); continue }
    for (const c of parsed) {
      checkedFields++
      if (!cols.includes(c.field)) fail(where, `行条件字段 ${c.field} 不在数据集 ${datasetId} 的真实返回列里`)
      if (!OPERATOR_WHITELIST.has(canonical(c.operator))) fail(where, `行条件操作符 ${c.operator} 不在白名单，渲染时 400`)
      if (String(c.value).startsWith(':') && ![':userId', ':tenantId', ':username', ':deptId'].includes(String(c.value))) fail(where, `未知占位符 ${c.value}`)
    }
  }
  if (!noCond) {
    for (const c of restricted.split(',')) {
      checkedFields++
      if (!cols.includes(c.trim())) fail(where, `受限列 ${c} 不在数据集 ${datasetId} 的真实返回列里（遮了个不存在的列，等于没遮）`)
    }
  }
}

// ---------- 4. 覆盖面：声明的条目都要被核到 ----------
if (mdRows.length !== CONTRACT.datasets.length) fail('数据集总数', `库里 ${mdRows.length} 行，声明 ${CONTRACT.datasets.length} 行`)
if (rptRows.length !== CONTRACT.reports.length) fail('报告总数', `库里 ${rptRows.length} 行，声明 ${CONTRACT.reports.length} 行`)
if (ruleRows.length !== CONTRACT.rules.length) fail('规则总数', `库里 ${ruleRows.length} 行，声明 ${CONTRACT.rules.length} 行`)
if (dimRows.length !== CONTRACT.dimensionRows.length) fail('维度字典总数', `库里 ${dimRows.length} 行，声明 ${CONTRACT.dimensionRows.length} 行`)
if (metRows.length !== CONTRACT.metricRows.length) fail('指标字典总数', `库里 ${metRows.length} 行，声明 ${CONTRACT.metricRows.length} 行`)

console.log(`已核 ${checkedNodes} 个配置条目 / ${checkedFields} 个字段引用`)
for (const w of warns) console.log('提示 ' + w)
if (errors.length) {
  for (const e of errors) console.log('失败 ' + e)
  console.log(`契约自检：${errors.length} 项不通过`)
  process.exit(1)
}
console.log('契约自检：全部通过')
