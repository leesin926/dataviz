/**
 * BD3 契约自检：把 8 条演示 DAG 按「引擎会怎么读它」逐节点核一遍。
 *
 * 为什么要单独核：ETL 引擎里最安静的失败是列名对不上 —— 聚合节点取不到值就写 0、
 * 目标端按首行键序写列，跑完是「成功」，只有数是错的；目标列漏了 NOT NULL 字段则是整批写不进去。
 * 这里用 库里回读的 transform_config + 源端 LIMIT 1 探针 + information_schema 列清单 提前挡掉，
 * 不需要点界面上的「执行」。
 *
 * 输入：
 *   devlog/bd3-readback.out —— 库里实际的 DAG（顺带验证补丁的 SQL 转义没把 JSON 弄坏）
 *   devlog/_ay/bd3-contract.json —— 生成器认定的 DAG，与回读比对
 *   devlog/bd3-probe.out —— 各 input 节点 SQL 的真实返回列
 */
const fs = require('fs')

const SUPPORTED_NODE = new Set(['input', 'filter', 'aggregate', 'output'])
const SUPPORTED_OPERATORS = new Set(['eq', '==', 'ne', '!=', 'gt', '>', 'lt', '<', 'gte', '>=', 'lte', '<=', 'contains', 'in'])
const AGG_FUNCS = new Set(['sum', 'avg', 'count', 'min', 'max'])
const IDENT = /^[A-Za-z_][A-Za-z0-9_]*$/

const lines = fs.readFileSync('devlog/bd3-readback.out', 'utf8').replace(/\r/g, '').split('\n').filter(Boolean)

/** 库里的 DAG：@@TASK <id> <json> */
const dbTasks = new Map()
/** 目标表真实列：@@COLS <table>.<column> <nullable> <default> */
const tableCols = new Map()
for (const line of lines) {
  const parts = line.split('\t')
  if (parts[0] === '@@TASK') {
    dbTasks.set(Number(parts[1]), parts[2])
  } else if (parts[0] === '@@COLS') {
    const [table, name] = parts[1].split('.')
    if (!tableCols.has(table)) tableCols.set(table, [])
    tableCols.get(table).push({ name, notNull: parts[2] === 'NO', noDefault: parts[3] === '~NULL~' })
  }
}

/** 探针输出：@@PROBE <taskId> <nodeId> 之后紧跟表头行 */
const probeLines = fs.readFileSync('devlog/bd3-probe.out', 'utf8').replace(/\r/g, '').split('\n').filter(Boolean)
const probes = new Map()
probeLines.forEach((l, i) => {
  const m = /^@@PROBE (\d+) (\S+)$/.exec(l)
  if (!m) return
  const next = probeLines[i + 1]
  if (!next || next.startsWith('@@PROBE') || next === 'marker') { probes.set(`${m[1]}.${m[2]}`, null); return }
  probes.set(`${m[1]}.${m[2]}`, next.split('\t'))
})

const expected = JSON.parse(fs.readFileSync('devlog/_ay/bd3-contract.json', 'utf8'))
const problems = []
const stats = { tasks: 0, nodes: 0, outCols: 0 }
const fail = (id, msg) => problems.push(`${id} · ${msg}`)

for (const item of expected) {
  stats.tasks += 1
  const raw = dbTasks.get(item.id)
  if (!raw) { fail(item.id, '库里读不到该任务'); continue }
  let dag
  try {
    dag = JSON.parse(raw)
  } catch (e) {
    fail(item.id, `库里 transform_config 不是合法 JSON（SQL 转义把内容弄坏了）：${e.message}`)
    continue
  }
  if (JSON.stringify(dag) !== JSON.stringify(item.dag)) {
    fail(item.id, '库里的 DAG 与生成器认定的不一致（补丁被手改过，或写入时转义漂了）')
  }

  const { nodes, edges } = dag
  if (nodes[0].type !== 'input') fail(item.id, '首节点不是源端')
  if (nodes[nodes.length - 1].type !== 'output') fail(item.id, '末节点不是目标端')
  edges.forEach((e, i) => {
    if (e.source !== nodes[i].id || e.target !== nodes[i + 1].id) fail(item.id, `边 ${e.id} 不是线性串联`)
  })

  let available = null
  for (const node of nodes) {
    stats.nodes += 1
    if (!SUPPORTED_NODE.has(node.type)) { fail(item.id, `节点类型 ${node.type} 引擎不认`); continue }
    if (node.type === 'input') {
      if (!node.config.datasourceId) fail(item.id, '源端没有 datasourceId')
      if (!node.config.sql && !node.config.tableName) fail(item.id, '源端既没有 sql 也没有 tableName')
      const probed = probes.get(`${item.id}.${node.id}`)
      if (!probed) { fail(item.id, `源端节点 ${node.id} 没有取数探针结果`); continue }
      if (!probed.every((c) => IDENT.test(c))) {
        fail(item.id, `源端返回列里有非法标识符：${probed.join(', ')}`)
      }
      if (node.config.columns && node.config.columns.join(',') !== probed.join(',')) {
        fail(item.id, `源端声明的投影列与实际返回不一致：声明 [${node.config.columns}] / 实际 [${probed}]`)
      }
      available = probed
    } else if (node.type === 'filter') {
      for (const c of node.config.filters || []) {
        if (!available.includes(c.field)) fail(item.id, `过滤字段 ${c.field} 不在上游列里（引擎按不通过处理，等于整批被丢掉）`)
        if (!SUPPORTED_OPERATORS.has(String(c.operator))) fail(item.id, `过滤 operator ${c.operator} 引擎不支持`)
        if (c.value === null || c.value === undefined) fail(item.id, `过滤字段 ${c.field} 没填值，引擎按不通过处理`)
      }
    } else if (node.type === 'aggregate') {
      for (const g of node.config.groupBy || []) {
        if (!IDENT.test(g)) fail(item.id, `分组字段 ${g} 是表达式，引擎只认普通列`)
        if (!available.includes(g)) fail(item.id, `分组字段 ${g} 不在上游列里（每行取到 null，全表会被聚成一行）`)
      }
      const aliases = []
      for (const a of node.config.aggregations || []) {
        if (!AGG_FUNCS.has(a.function)) fail(item.id, `聚合函数 ${a.function} 不支持`)
        if (a.field !== '1' && a.field !== '*' && !available.includes(a.field)) {
          fail(item.id, `聚合字段 ${a.field} 不在上游列里（会算成 0 写进目标表）`)
        }
        if (!IDENT.test(a.alias)) fail(item.id, `聚合别名 ${a.alias} 不是合法标识符`)
        aliases.push(a.alias)
      }
      available = [...(node.config.groupBy || []), ...aliases]
    } else if (node.type === 'output') {
      if (node.config.writeMode !== 'append') fail(item.id, `写入方式 ${node.config.writeMode}：引擎只实现 append`)
      const real = tableCols.get(node.config.tableName)
      if (!real) { fail(item.id, `目标表 ${node.config.tableName} 在库里不存在`); continue }
      const realNames = real.map((c) => c.name)
      for (const col of node.config.columns) {
        stats.outCols += 1
        if (!realNames.includes(col)) fail(item.id, `目标列 ${col} 不在 ${node.config.tableName} 里（写入被服务端拒）`)
        if (!available.includes(col)) fail(item.id, `目标列 ${col} 上游给不出来（会静默写 null）`)
      }
      for (const c of real.filter((x) => x.notNull && x.noDefault)) {
        if (!node.config.columns.includes(c.name)) {
          fail(item.id, `${node.config.tableName}.${c.name} NOT NULL 且无默认值，却没进目标列清单 —— 整批写入失败`)
        }
      }
    }
  }
}

console.log(`已核 ${stats.tasks} 条任务 / ${stats.nodes} 个节点 / ${stats.outCols} 个目标列`)
if (problems.length === 0) {
  console.log('契约自检：全部通过')
  process.exit(0)
}
console.log(`契约自检：${problems.length} 处问题`)
problems.forEach((p) => console.log('  - ' + p))
process.exit(1)
