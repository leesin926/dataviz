#!/usr/bin/env node
/**
 * BD5 大屏组件契约核对（对渲染器，不是对数据库）
 *
 * 为什么需要它：补丁里的 13 条 SQL 自检只能证明"JSON 形状对"，证不了"渲染器认这套字段"。
 * 而这一层的失败全是静默的 —— type 拼错 → ScreenUnknown 灰块，图表少 chartConfig → 占位框，
 * 颜色撞白 → 归一化把浅色悄悄改成品牌蓝。库里看着一切正常，页面上少一块。
 * 所以这里把判据拿在**前端源码本身**手里：注册表、组件定义、撞色阈值、聚合白名单
 * 全部从仓库现读，不另抄一份类型表（抄的那份会随时间变成假话）。
 *
 * 跑法：node devlog/_ay/bd5-contract.cjs
 * 依赖：devlog/_ay/bd5-contract.json（生成器声明）、devlog/bd5-readback.out（库里回读）、
 *       devlog/bd5-query.out（911 等价查询行数）、devlog/_ay/bd4-contract.json（数据集声明面）
 * 退出码：0 = 全部通过；1 = 有失败项（逐条打印）。
 */
const fs = require('fs')
const path = require('path')

const ROOT = process.cwd()
const read = (p) => fs.readFileSync(path.join(ROOT, p), 'utf8')

const fails = []
const passes = []
const notes = []
const assert = (cond, label, detail) => {
  if (cond) passes.push(label)
  else fails.push(label + (detail ? ` —— ${detail}` : ''))
}
const note = (label) => notes.push(label)

// ---------------------------------------------------------------- 源码侧白名单

const SCREEN_TS = 'frontend/packages/shared-types/src/screen.ts'
const screenSrc = read(SCREEN_TS)

// 撞色判据先就位：后面 CANVAS_INK 求值、定义数组求值、颜色核对都要用（const 有 TDZ，顺序不能反）
const CONTRAST_MIN = Number(/CANVAS_CONTRAST_MIN\s*=\s*(\d+)/.exec(screenSrc)[1])
const BG_COLOR = /CANVAS_BG_COLOR\s*=\s*'([^']+)'/.exec(screenSrc)[1]

/** 与 colorLuma 同实现（照抄 screen.ts:356 那段正则，注释里指向原文） */
function colorLuma(color) {
  if (!color) return null
  const value = String(color).trim()
  const m = /^#?([0-9a-f]{3}|[0-9a-f]{6})$/i.exec(value)
  if (m) {
    const hex = m[1].length === 3 ? m[1].split('').map((c) => c + c).join('') : m[1]
    return 0.299 * parseInt(hex.slice(0, 2), 16) + 0.587 * parseInt(hex.slice(2, 4), 16)
      + 0.114 * parseInt(hex.slice(4, 6), 16)
  }
  const rgb = /rgba?\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)/i.exec(value)
  if (rgb) return 0.299 * +rgb[1] + 0.587 * +rgb[2] + 0.114 * +rgb[3]
  return null
}
const BASE_LUMA = colorLuma(BG_COLOR) ?? 255
const clashes = (v) => typeof v === 'string' && colorLuma(v) !== null && Math.abs(colorLuma(v) - BASE_LUMA) < CONTRAST_MIN

/** ChartType 枚举的值集合（enum 成员是 `NAME = 'value',`） */
const chartTypes = [...read('frontend/packages/shared-types/src/chart.ts')
  .matchAll(/^\s+[A-Z_]+\s*=\s*'([^']+)'/gm)].map((m) => m[1])

/**
 * CANVAS_INK 现值。源码里每个键写成 `inkDark ? 深色档 : 浅色档`，
 * 而 inkDark = isDarkColor(CANVAS_BG_COLOR)，白底 ⇒ 取浅色档（冒号后面那一支）。
 */
function canvasInk() {
  const start = screenSrc.indexOf('export const CANVAS_INK = {')
  if (start < 0) throw new Error('CANVAS_INK 解析不到，定义数组求值会缺标识符')
  const open = screenSrc.indexOf('{', start)
  const close = screenSrc.indexOf('} as const', open)
  if (close < 0) throw new Error('CANVAS_INK 对象体没截到')
  const body = screenSrc.slice(open + 1, close)
  const inkDark = colorLuma(BG_COLOR) !== null && colorLuma(BG_COLOR) < 150
  // 与源码同一个判据（isDarkColor：luma<150 为深），把三元交给真实布尔值求值，不猜分支
  // eslint-disable-next-line no-new-func
  return new Function(`const inkDark = ${inkDark}\nreturn {${body}}`)()
}
const CANVAS_INK = canvasInk()

/**
 * 从 `const NAME = {` / `[` 起做括号扫描（跳过字符串与行注释），截出完整初始化表达式原文。
 * 定义数组引用的这些顶层常量必须在 shim 里以原貌出现：抄一份进 checker，
 * 源码改值时 checker 不会跟着错，就成了假判据。
 * 截出后只剥类型断言（` as 类型`）—— 断言纯给 tsc 看，运行时删掉不改变值。
 */
function declLiteral(name) {
  const at = screenSrc.indexOf(`const ${name} = `)
  if (at < 0) return null
  let i = at + `const ${name} = `.length
  while (/\s/.test(screenSrc[i])) i++
  const head = screenSrc[i]
  // 字符串常量（如 DEFAULT_GEO_URL）：整段照抄，值本身就是判据的一部分
  if (head === "'" || head === '"' || head === '`') {
    const end = screenSrc.indexOf(head, i + 1)
    return end < 0 ? null : screenSrc.slice(i, end + 1)
  }
  if (head !== '{' && head !== '[') return null
  const closeOf = { '{': '}', '[': ']' }
  const open = screenSrc[i]
  let depth = 0
  let inStr = null
  for (let j = i; j < screenSrc.length; j++) {
    const ch = screenSrc[j]
    if (inStr) {
      if (ch === '\\') j++
      else if (ch === inStr) inStr = null
      continue
    }
    if (ch === '"' || ch === "'" || ch === '`') { inStr = ch; continue }
    if (ch === '/' && screenSrc[j + 1] === '/') { j = screenSrc.indexOf('\n', j); if (j < 0) break; continue }
    if (ch === open) depth++
    else if (ch === closeOf[open]) { depth--; if (depth === 0) return stripTs(screenSrc.slice(i, j + 1)) }
  }
  return null
}

/** 剥掉 TS 断言与标注，让数组字面量能喂给 new Function（值本身不动） */
const stripTs = (src) => src
  .replace(/\s+as\s+(?:const\b|[A-Za-z_$][\w$]*(?:\s*<[^<>]*>)?(?:\s*\[\s*\])?)/g, '')
  .replace(/:\s*Screen(?:Platform|StyleField)\[\]/g, '')

/**
 * 取组件定义数组。用求值而不是正则：定义体里的三元、嵌套对象、`as const` 用正则切不稳，
 * 切错就等于白名单造假。这里只喂给它源码里那段数组字面量，标识符全部由本地 shim 提供
 * （ChartType 用真实枚举值，DEMO_* 演示数据集与核对无关， stub 成空即可）。
 */
function loadDefinitions() {
  const start = screenSrc.indexOf('BUILTIN_COMPONENT_DEFINITIONS: ScreenComponentDefinition[] = [')
  if (start < 0) throw new Error(`${SCREEN_TS} 里找不到组件定义数组，白名单无从核对`)
  const open = screenSrc.indexOf('= [', start) + 2 // 不能直接 indexOf('[')：会撞上类型标注 ScreenComponentDefinition[] 的那个
  let depth = 0
  let close = -1
  for (let i = open; i < screenSrc.length; i++) {
    if (screenSrc[i] === '[') depth++
    else if (screenSrc[i] === ']') { depth--; if (depth === 0) { close = i; break } }
  }
  const body = stripTs(screenSrc.slice(open + 1, close))
  const stubs = [...new Set([...body.matchAll(/\b(DEMO_[A-Z_0-9]+)\b/g)].map((m) => m[1]))]
    .map((name) => `const ${name} = []`)
    .join('\n    ')
  // 定义体引用到、但上面没安排的顶层常量：从 screen.ts 原文摘出来补进 shim。
  // 求值时缺哪个就补哪个，比预先列清单稳（清单会随源码漂移）。
  const known = new Set(['ChartType', 'CANVAS_INK', 'ALL_ENDS', 'PC_ONLY'])
  const needed = [...new Set([...body.matchAll(/\b([A-Z][A-Za-z0-9_]*)\b/g)].map((m) => m[1]))]
    .filter((name) => !known.has(name) && !/^DEMO_/.test(name) && name !== 'ChartType')
    .filter((name) => declLiteral(name) !== null)
  const hoisted = needed.map((name) => `const ${name} = ${declLiteral(name)}`).join('\n    ')
  const shim = `
    const ChartType = ${JSON.stringify(enumMap)}
    const CANVAS_INK = ${JSON.stringify(CANVAS_INK)}
    const ALL_ENDS = ['pc', 'mobile', 'tablet']
    const PC_ONLY = ['pc']
    ${hoisted}
    ${stubs}
    return [${body}]
  `
  // 定义体里若引用了 shim 没提供的标识符，new Function 会抛 ReferenceError —— 不能吞掉，
  // 吞掉就等于白名单静默缺失。这里显式把它转成可读的补齐提示。
  try {
    // eslint-disable-next-line no-new-func
    return new Function(shim)()
  } catch (e) {
    const miss = /(\w+) is not defined/.exec(e.message)
    throw new Error(`定义数组 shim 缺标识符 ${miss ? miss[1] : '?'}：它既不是顶层常量字面量，也没被 stub。`
      + `（${e.message}）`)
  }
}

/** 枚举成员名 → 值（chart.ts 里是 `BAR = 'bar'`），定义数组求值时当 shim 用 */
const enumMap = {}
for (const m of read('frontend/packages/shared-types/src/chart.ts')
  .matchAll(/^\s+([A-Z_0-9]+)\s*=\s*'([^']+)'/gm)) enumMap[m[1]] = m[2]

const definitions = loadDefinitions()
const chartPresetTypes = definitions.filter((d) => d.chartPreset).map((d) => d.type)
const registryTypes = [...read('frontend/packages/screen-engine/src/render/componentRegistry.ts')
  .matchAll(/^\s*\['([a-zA-Z]+)',\s*Screen/gm)].map((m) => m[1])
const renderableTypes = new Set([...registryTypes, ...chartPresetTypes])

/** 组件类型 → 该类型可投放的端（编辑器面板与 isRenderableOnPlatform 同一口径） */
const platformsOf = Object.fromEntries(definitions.map((d) => [d.type, d.platforms || []]))

/** 撞色归一改写的 props 键（值在上面已解析成 CANVAS_INK） */
const INK_KEYS = [...(/const INK_PROP_TARGET[^=]*=\s*\{([\s\S]*?)\n\}/.exec(screenSrc)[1])
  .matchAll(/^\s*([A-Za-z]+):/gm)].map((m) => m[1])

/** clock 的 dateFormat 只认 shared-utils 的清单，认不了会静默退回 YYYY-MM-DD */
const DATE_FORMATS = [...(/export const DATE_FORMATS\s*=\s*\[([\s\S]*?)\]/
  .exec(read('frontend/packages/shared-utils/src/date.ts'))[1]).matchAll(/'([^']+)'/g)].map((m) => m[1])

/** SqlBuilder 的聚合与排序白名单（写错了是 400，不是空白图） */
const sqlSrc = read('backend/analysis-service/src/main/java/com/dataviz/analysis/engine/SqlBuilder.java')
const AGGREGATIONS = [...sqlSrc.matchAll(/AGGREGATIONS\.put\("([a-z]+)"/g)].map((m) => m[1])
note(`判据取自源码：聚合 ${AGGREGATIONS.join('/')}，撞色阈值 ${CONTRAST_MIN}（底色 ${BG_COLOR}），`
  + `渲染类型 ${renderableTypes.size} 个，日期格式 ${DATE_FORMATS.length} 个`)

// 白名单本身要先证明"不是空的"：解析失败时下面每条依赖它的检查都会恒真通过，比漏检更危险
assert(AGGREGATIONS.length >= 5, `SqlBuilder 聚合白名单解析出 ${AGGREGATIONS.length} 项`)
assert(INK_KEYS.length >= 10, `撞色 props 键解析出 ${INK_KEYS.length} 项`)
assert(DATE_FORMATS.length >= 5, `DATE_FORMATS 解析出 ${DATE_FORMATS.length} 项`)
assert(chartTypes.length >= 10, `ChartType 枚举解析出 ${chartTypes.length} 项`)
assert(registryTypes.length >= 15, `渲染注册表解析出 ${registryTypes.length} 项`)
assert(chartPresetTypes.length >= 10, `图表预设类型解析出 ${chartPresetTypes.length} 项`,
  'definitions 数组求值落空时这里是 0，后面所有图表检查会集体空转')
assert(Object.keys(platformsOf).length >= 20, `组件定义解析出 ${Object.keys(platformsOf).length} 个类型`)
assert(BASE_LUMA === 255, `底色亮度按 ${BG_COLOR} 算出 ${BASE_LUMA}（白底应为 255）`)

// ---------------------------------------------------------------- 数据面

const declared = JSON.parse(read('devlog/_ay/bd5-contract.json'))
const bd4 = JSON.parse(read('devlog/_ay/bd4-contract.json'))
const datasetById = Object.fromEntries(bd4.datasets.map((d) => [d.id, d]))
const fieldsOfDataset = (id) => {
  const d = datasetById[id]
  if (!d) return null
  return new Set([...(d.dimensions || []).map((x) => x.field), ...(d.metrics || []).map((x) => x.field)])
}

/** 回读文件按标签切出来；~|~ 是分隔符（mysql -B 会把真 tab 转义成字面 \t） */
function readout(file, tag) {
  const out = {}
  const head = `@@${tag} `
  for (const line of read(file).split(/\r?\n/)) {
    if (!line.startsWith(head)) continue // 首行是 SELECT 表达式回显，不是数据
    const parts = line.slice(head.length).split(' ~|~ ')
    out[parts[0]] = parts.slice(1)
  }
  return out
}
const dbScreen = readout('devlog/bd5-readback.out', 'SCREEN')
const dbCfg = readout('devlog/bd5-readback.out', 'CFG')
const dbCmp = readout('devlog/bd5-readback.out', 'CMP')
const dbVar = readout('devlog/bd5-readback.out', 'VAR')
const dbRows = {}
for (const line of read('devlog/bd5-query.out').split(/\r?\n/)) {
  const m = /^@@ROWS ~\|~ (\S+) ~\|~ (\d+)$/.exec(line)
  if (m) dbRows[m[1]] = Number(m[2])
}

/**
 * 等价探针的 SQL 原文（bd5-gen-screen.mjs 生成）。第 11 节拿它对账 ORDER BY 的写法，
 * 所以要留在内存里让变异能改它 —— 改文件等于改被测物，负向自测不该碰盘。
 */
let probeSql = read('devlog/_ay/bd5-query.sql')

/**
 * 变异注入（只给 bd5-contract-negative.cjs 用）：checker 自己不失败不算被证明，
 * 必须有一种办法让它必然失败一次。BD5_MUTATE 指名字，改完后把回读串同步过去，
 * 使第 1 节仍自洽 —— 这样"哪个检查被点着"是可指的，而不是全体塌一遍。
 */
function mutate(kind) {
  const c901 = declared.screens[0].components
  const c911 = declared.screens[1].components
  const byId = (id) => c901.find((c) => c.id === id) || c911.find((c) => c.id === id)
  switch (kind) {
    case 'unknown-type': byId('c1').type = 'mysteryChart'; break
    case 'no-chartConfig': delete byId('c13').chartConfig; break
    case 'bad-chartType': byId('c13').chartConfig.chartType = 'volcano'; break
    case 'outside-canvas': byId('c1').x = 3000; break
    case 'drop-from-layer': declared.screens[0].layers[0].componentIds =
      declared.screens[0].layers[0].componentIds.filter((x) => x !== 'c1'); break
    case 'dangling-layer': declared.screens[0].layers[0].componentIds.push('ghost99'); break
    case 'static-field-typo': {
      const g = byId('c13')
      g.chartConfig.dimensions[0].field = 'nme'
      break
    }
    case 'live-screen-static': {
      const d = c911.find((x) => x.chartConfig && x.chartConfig.datasetId)
      d.request = { sourceType: 'static', staticData: { columns: [{ field: 'name' }], rows: [{ name: 'x' }] } }
      break
    }
    case 'bad-aggregation': {
      const d = c911.find((x) => x.chartConfig && (x.chartConfig.measures || []).length)
      d.chartConfig.measures[0].aggregation = 'stddev'
      break
    }
    case 'bad-sort-order': {
      const d = c911.find((x) => (x.chartConfig && x.chartConfig.sorts || []).length)
      d.chartConfig.sorts[0].order = 'sideways'
      break
    }
    case 'hidden-column': {
      // 915 的受限列是 profit/margin_rate（BD4 规则 912）：直连图表点名它 = 登录态下被遮列
      const g = c911.find((x) => x.type === 'gaugeChart')
      g.chartConfig.measures = [{ field: 'profit', aggregation: 'sum' }]
      break
    }
    case 'white-ink': byId('c6').props.color = '#f7f8fa'; break
    case 'statCard-string': byId('c6').props.value = '5973361'; break
    case 'progress-negative': byId('c22').props.percent = -3; break
    case 'table-ragged': {
      const t = c901.find((x) => x.type === 'table' || x.type === 'scrollBoard')
      if (t) t.props.rows = [t.props.rows[0].slice(0, 1)]
      break
    }
    case 'clock-format': {
      const ck = c901.find((x) => x.type === 'clock')
      if (ck) ck.props.dateFormat = 'YYYY年MM月DD日 星期W'
      break
    }
    case 'mobile-pc-only': declared.screens[0].variants.mobile.components.push({
      id: 'mX', type: 'map', name: '违规', x: 0, y: 0, w: 100, h: 100, zIndex: 99, visible: true, locked: false, props: {},
    }); break
    case 'gauge-drift': {
      const g = byId([...c901].find((x) => x.type === 'gaugeChart').id)
      g.request.staticData.rows[0].value = 88.8
      break
    }
    case 'rows-zero': {
      const d = c911.find((x) => x.chartConfig && x.chartConfig.datasetId)
      dbRows[d.id] = 0
      break
    }
    case 'probe-bare-order': {
      // 回到 API-109 修复前的形状：ORDER BY 排裸列而不是投影别名。
      // 探针当年就是这么写的，于是它全绿、屏上三张图空着。
      const next = probeSql.replace(/ORDER BY `sum_amount`/g, 'ORDER BY `amount`')
      // 没替换到任何一处就等于变异没发生，静默通过会把"检查失效"洗成"检查通过"
      if (next === probeSql) throw new Error('变异 probe-bare-order 没有命中任何 ORDER BY，探针形状已变，检查需同步')
      probeSql = next
      break
    }
    case 'db-drift': {
      // 库里少一个组件：只有第 1 节能抓到（其余各节看的是声明面）
      dbCmp[String(declared.screens[0].id)] =
        [JSON.stringify(declared.screens[0].components.slice(1))]
      return
    }
    default: throw new Error(`未知变异 ${kind}`)
  }
  // 声明面变了，把回读串同步过去：让"除目标检查外其余仍绿"成立
  for (const s of declared.screens) {
    dbCmp[String(s.id)] = [JSON.stringify(s.components)]
    if (s.variants) dbVar[String(s.id)] = [JSON.stringify(s.variants)]
  }
}
if (process.env.BD5_MUTATE) {
  mutate(process.env.BD5_MUTATE)
  note(`注入了变异 ${process.env.BD5_MUTATE}（负向自测，本次运行预期失败）`)
}

// ---------------------------------------------------------------- 1 回读 == 声明

/**
 * 递归排序键后比较。MySQL 的 JSON 列会把对象键按字典序重排（实测 {"h":…,"w":…,"x":…}），
 * 逐字串比较等于把"库里存了同样内容"错判成"库里读不到"。
 */
function canon(v) {
  if (Array.isArray(v)) return v.map(canon)
  if (v && typeof v === 'object') {
    return Object.fromEntries(Object.keys(v).sort().map((k) => [k, canon(v[k])]))
  }
  return v
}
const same = (a, b) => JSON.stringify(canon(a)) === JSON.stringify(canon(b))

for (const s of declared.screens) {
  const id = String(s.id)
  assert(dbCmp[id], `${id} 库里读不到 components_json`)
  if (!dbCmp[id]) continue
  const cmp = JSON.parse(dbCmp[id].join(' ~|~ '))
  const cfg = JSON.parse(dbCfg[id].join(' ~|~ '))
  const vr = dbVar[id] && dbVar[id][0] !== 'NULL' ? JSON.parse(dbVar[id].join(' ~|~ ')) : null
  assert(same(cmp, s.components),
    `${id} 库里组件与声明逐字段一致`, `库里 ${cmp.length} / 声明 ${s.components.length}`)
  // 契约面把 config 拆成 width/height/layers 三项（生成器就是这么发的），所以逐项目视比，
  // 而不是拿整块 config 去对一个不存在的 s.config —— 那样 undefined 也"相等"
  assert(cfg.width === s.width && cfg.height === s.height, `${id} 库里画布尺寸与声明一致`,
    `库里 ${cfg.width}x${cfg.height} / 声明 ${s.width}x${s.height}`)
  assert(same(cfg.layers, s.layers), `${id} 库里图层与声明一致`)
  assert(cfg.backgroundColor === BG_COLOR, `${id} 库里画布底色是 ${BG_COLOR}`, `库里是 ${cfg.backgroundColor}`)
  assert(same(vr || null, s.variants || null), `${id} 库里 variants 与声明一致`)
  const [name, status, token] = dbScreen[id]
  assert(name === s.name && token === s.shareToken, `${id} 库里的名称/令牌与声明一致`, `${name} / ${token}`)
  assert(String(s.status) === '1' && status === '1', `${id} 库里是发布态（免登链接的前置）`, `status=${status}`)
}
assert(declared.screens[0].shareToken === 'smoke0921aaaa',
  '901 沿用已发出去的免登令牌（换了令牌等于把旧链接作废）', declared.screens[0].shareToken)

// ---------------------------------------------------------------- 2 渲染器可达 + 类型在注册表

const all = []
for (const s of declared.screens) {
  for (const c of s.components) all.push({ screen: s.id, c, canvas: { w: s.width, h: s.height } })
  const mobile = (s.variants && s.variants.mobile && s.variants.mobile.components) || []
  for (const c of mobile) all.push({ screen: `${s.id}/mobile`, c, canvas: { w: s.variants.mobile.width, h: s.variants.mobile.height } })
}
for (const { screen, c } of all) {
  assert(renderableTypes.has(c.type), `${screen} ${c.id} 的 type '${c.type}' 有渲染器`, 'ScreenUnknown 灰块')
}
for (const { screen, c } of all) {
  if (!chartPresetTypes.includes(c.type)) continue
  const cfgx = c.chartConfig
  assert(!!cfgx, `${screen} ${c.id} 图表组件必须带 chartConfig`, 'ScreenChart 的 v-if 落空 → 占位框')
  if (!cfgx) continue
  assert(chartTypes.includes(cfgx.chartType), `${screen} ${c.id} chartType '${cfgx.chartType}' 在 ChartType 枚举里`)
}

// ---------------------------------------------------------------- 3 几何在画布内

for (const { screen, c, canvas } of all) {
  const inside = c.x >= 0 && c.y >= 0 && c.x + c.w <= canvas.w && c.y + c.h <= canvas.h
  assert(inside, `${screen} ${c.id} 落在 ${canvas.w}x${canvas.h} 画布内`,
    `${c.x},${c.y}+${c.w}x${c.h}`)
}

// ---------------------------------------------------------------- 4 图层 == 组件集合

for (const s of declared.screens) {
  const ids = s.components.map((c) => c.id)
  const layered = (s.layers || []).reduce((acc, l) => acc.concat(l.componentIds || []), [])
  const dup = ids.length !== new Set(ids).size
  assert(!dup, `${s.id} 组件 id 不重复`)
  assert(new Set(layered).size === layered.length, `${s.id} 每个组件只归入一个图层`, `${layered.length} 条引用 / ${ids.length} 个组件`)
  const missing = ids.filter((i) => !layered.includes(i))
  const dangling = layered.filter((i) => !ids.includes(i))
  assert(missing.length === 0, `${s.id} 没有游离组件（图层面板漏项）`, missing.join(','))
  assert(dangling.length === 0, `${s.id} 图层不引用不存在的组件`, dangling.join(','))
}

// ---------------------------------------------------------------- 5 数据源契约：静态 / 数据集

function keySetOf(columns) {
  return new Set((columns || []).map((col) => (typeof col === 'string' ? col : col.field)))
}
for (const s of declared.screens) {
  for (const c of s.components) {
    if (!chartPresetTypes.includes(c.type)) continue
    const cfgx = c.chartConfig
    // 第 2 节已经为"缺 chartConfig"报过一次；这里必须跳过而不是解引用 ——
    // 崩在这里会让后面所有检查（包括负向自测的其余 19 种变异）没有读数
    if (!cfgx) continue
    const req = c.request || {}
    const dims = (cfgx.dimensions || []).map((d) => d.field)
    const mets = (cfgx.measures || []).map((m) => m.field)
    if (s.id === 901) {
      assert(req.sourceType === 'static' && req.staticData, `${s.id} ${c.id} 走 staticData`, `sourceType=${req.sourceType}`)
      if (!req.staticData) continue
      const keys = keySetOf(req.staticData.columns)
      assert(Array.isArray(req.staticData.rows) && req.staticData.rows.length > 0,
        `${s.id} ${c.id} 静态行非空`, `rows=${(req.staticData.rows || []).length}`)
      for (const f of [...dims, ...mets]) {
        assert(keys.has(f), `${s.id} ${c.id} 点名字段 ${f} 在 staticData.columns 里`)
        const first = (req.staticData.rows || [])[0]
        if (first && !Array.isArray(first)) {
          assert(Object.prototype.hasOwnProperty.call(first, f), `${s.id} ${c.id} 首行真的带 ${f}`)
        }
      }
    } else {
      assert(req.sourceType === 'dataset' && !req.staticData, `${s.id} ${c.id} 走数据集真链路`, `sourceType=${req.sourceType}`)
      const ds = datasetById[cfgx.datasetId]
      assert(!!ds, `${s.id} ${c.id} datasetId ${cfgx.datasetId} 在 BD4 声明面里`)
      if (!ds) continue
      const fields = fieldsOfDataset(cfgx.datasetId)
      for (const f of [...dims, ...mets]) assert(fields.has(f), `${s.id} ${c.id} 字段 ${f} 在数据集 ${ds.id} 声明里`, [...fields].join(','))
      for (const m of cfgx.measures || []) {
        const agg = String(m.aggregation || 'sum').toLowerCase()
        assert(AGGREGATIONS.includes(agg), `${s.id} ${c.id} 聚合 ${agg} 在 SqlBuilder 白名单里`)
      }
      for (const o of cfgx.sorts || []) {
        assert(fields.has(o.field), `${s.id} ${c.id} 排序字段 ${o.field} 属于该数据集`)
        assert(['asc', 'desc'].includes(String(o.order).toLowerCase()), `${s.id} ${c.id} 排序方向 ${o.order}`)
      }
    }
  }
}

// ---------------------------------------------------------------- 6 行列权限不许遮到 911 用的列

const live = declared.screens.find((s) => s.id === 911)
const usedByLive = new Map()
for (const c of (live ? live.components : [])) {
  if (!c.chartConfig || !c.chartConfig.datasetId) continue
  const set = usedByLive.get(c.chartConfig.datasetId) || new Set()
  for (const f of [...(c.chartConfig.dimensions || []), ...(c.chartConfig.measures || [])]) set.add(f.field)
  usedByLive.set(c.chartConfig.datasetId, set)
}
for (const r of bd4.rules.filter((x) => x.enabled)) {
  const touched = usedByLive.get(r.datasetId)
  if (!touched) { note(`权限规则 ${r.id}（数据集 ${r.datasetId}）本轮 911 没用到 —— 演示时不构成遮列风险`); continue }
  const hidden = String(r.restrictedColumns || '').split(',').map((x) => x.trim()).filter(Boolean)
  const hit = hidden.filter((f) => touched.has(f))
  assert(hit.length === 0, `权限规则 ${r.id} 遮的列 [${hit}] 被 911 组件点名引用（登录态下会 403 或空图）`, `数据集 ${r.datasetId}`)
  if ((r.conditions || []).length) {
    note(`权限规则 ${r.id} 对数据集 ${r.datasetId} 有行条件（${r.conditions.map((c) => `${c.field}${c.operator}${c.value}`).join(',')}）：`
      + `演示账号若非 super_admin 只看得到子集，911 直连图表在 user 角色下会变窄 —— 这是设计而非故障`)
  }
}

// ---------------------------------------------------------------- 7 撞色（白底前景必须留得住）

let inkChecked = 0
for (const { screen, c } of all) {
  for (const k of INK_KEYS) {
    const v = (c.props || {})[k]
    if (v === undefined || v === null) continue
    inkChecked++
    assert(!clashes(v), `${screen} ${c.id} props.${k}=${v} 不与白底撞色`, '会被 normalizeScreenInk 改写成品牌蓝')
  }
  // chartConfig.options.title.textStyle.color 也在归一范围内（渲染时同样是前景）
  const tcol = c.chartConfig && c.chartConfig.options && c.chartConfig.options.title && c.chartConfig.options.title.textStyle
    ? c.chartConfig.options.title.textStyle.color : undefined
  if (tcol !== undefined) { inkChecked++; assert(!clashes(tcol), `${screen} ${c.id} 图表标题色 ${tcol} 不与白底撞色`) }
}
assert(inkChecked > 0, '确实核到了颜色属性（核到 0 个说明这条检查是空的）', `${inkChecked}`)

// ---------------------------------------------------------------- 8 非图组件的必需 props

let widgetChecked = 0
for (const { screen, c } of all) {
  const p = c.props || {}
  const num = (v) => typeof v === 'number' && Number.isFinite(v)
  switch (c.type) {
    case 'statCard':
      widgetChecked++
      assert(num(p.value), `${screen} ${c.id} statCard 的 props.value 必须是数字`, `现在是 ${JSON.stringify(p.value)}`)
      break
    case 'progress':
      widgetChecked++
      // percent 可以 >100：达成率这类指标本来就会超额。ScreenProgress 把填充条按 100 截断，
      // 但数字照原样显示 —— 这是本轮顺手改的（改前超额显示成 100%，等于把亮点数据抹平）
      assert(num(p.percent) && p.percent >= 0 && p.percent <= 1000,
        `${screen} ${c.id} progress 的 percent 是 0~1000 的数字`, `现在是 ${JSON.stringify(p.percent)}`)
      break
    case 'numberFlop':
      widgetChecked++
      assert(num(p.value), `${screen} ${c.id} numberFlop 的 value 是数字`)
      break
    case 'ranking':
      widgetChecked++
      assert(Array.isArray(p.items) && p.items.length > 0 && p.items.every((i) => num(i.value)),
        `${screen} ${c.id} ranking 的 items 每项要带数值`)
      break
    case 'table':
      widgetChecked++
      assert(Array.isArray(p.columns) && p.columns.length > 0 && Array.isArray(p.rows) && p.rows.length > 0,
        `${screen} ${c.id} table 需要 columns(表头) + rows(表体)`)
      assert(p.rows.every((r) => r.length === p.columns.length), `${screen} ${c.id} table 每行格数与表头一致`)
      break
    case 'scrollBoard':
      widgetChecked++
      assert(Array.isArray(p.columns) && p.columns.length > 0 && Array.isArray(p.rows) && p.rows.length > 0,
        `${screen} ${c.id} scrollBoard 需要 head + body`)
      assert(p.rows.every((r) => r.length === p.columns.length), `${screen} ${c.id} scrollBoard 每行格数与表头一致`)
      break
    case 'marquee':
      widgetChecked++
      assert(Array.isArray(p.texts) && p.texts.length > 0 && p.texts.every((t) => String(t).trim()),
        `${screen} ${c.id} marquee 的 texts 非空`)
      break
    case 'clock':
      widgetChecked++
      assert(DATE_FORMATS.includes(p.dateFormat), `${screen} ${c.id} clock 的 dateFormat '${p.dateFormat}' 在 DATE_FORMATS 里`,
        '认不了会静默退回 YYYY-MM-DD')
      break
    case 'text':
      widgetChecked++
      assert(typeof p.text === 'string' && p.text.trim(), `${screen} ${c.id} text 要有内容`)
      assert(!p.text.includes('\n'), `${screen} ${c.id} text 内容不带换行`, '模板插值会把 \\n 折成一行')
      break
    case 'decoration':
      widgetChecked++
      assert(['flow', 'bars', 'corner', 'divider'].includes(p.variant), `${screen} ${c.id} decoration 变体 '${p.variant}' 存在`)
      break
    default:
      break
  }
}
assert(widgetChecked >= 10, `核到了足够多的非图组件（${widgetChecked} 个）`)

// ---------------------------------------------------------------- 9 移动端变体只放可投类型

for (const s of declared.screens) {
  const mobile = s.variants && s.variants.mobile
  if (!mobile) continue
  for (const c of mobile.components) {
    assert((platformsOf[c.type] || []).includes('mobile'),
      `${s.id}/mobile ${c.id} 的类型 ${c.type} 允许投放到 mobile`, 'isRenderableOnPlatform 会直接跳过')
  }
  assert(mobile.width > 0 && mobile.height > 0, `${s.id}/mobile 带画布尺寸`)
}

// ---------------------------------------------------------------- 10 数字对账：屏上的数 == 数据层的数

/**
 * 屏面上真实出现的数：指标卡/计数器取 props，跑马灯与文本从字符串里抠数字。
 * 对账必须对"看得到数的那些地方"，只核 statCard 会把 18.7% 这种写在跑马灯里的口径判成没数。
 */
function displayedNumbers(screen) {
  const nums = []
  for (const c of screen.components) {
    const p = c.props || {}
    for (const k of ['value', 'percent']) if (typeof p[k] === 'number') nums.push(p[k])
    const texts = Array.isArray(p.texts) ? p.texts : [p.text, p.label].filter((x) => typeof x === 'string')
    for (const t of texts) for (const m of String(t).matchAll(/-?\d[\d,]*(?:\.\d+)?/g)) nums.push(Number(m[0].replace(/,/g, '')))
    const rows = c.request && c.request.staticData && c.request.staticData.rows
    for (const r of rows || []) for (const v of Object.values(r)) if (typeof v === 'number') nums.push(v)
  }
  return nums
}

const meta = declared.meta
const n = (v) => Number(String(v).replace(/,/g, ''))
const nearest = (list, want) => {
  if (!list.length) return '（屏面上一个数都没有）'
  return String(list.reduce((a, b) => (Math.abs(b - want) < Math.abs(a - want) ? b : a)))
}
const s901 = declared.screens.find((s) => s.id === 901)
const cards = s901.components.filter((c) => c.type === 'statCard')
const shown901 = displayedNumbers(s901)
for (const key of ['amount', 'orders', 'profit', 'avgPay', 'achievement', 'margin', 'stores']) {
  if (meta.kpi[key] === undefined) continue
  const want = n(meta.kpi[key])
  // 显示口径允许"探到的数"或"探到的数四舍五入到整"，别的不认：
  // 用 ±0.5 容差会把 18.2 也放行成 18.7 的对账，那是假通过
  const hit = shown901.find((v) => v === want || v === Math.round(want))
  assert(hit !== undefined, `901 屏面上有 ${key}=${meta.kpi[key]} 的数（对账用）`,
    `屏面 ${shown901.length} 个数里最近的是 ${nearest(shown901, want)}`)
}
const gauge901 = s901.components.find((c) => c.type === 'gaugeChart')
const gaugeValue = gauge901.request.staticData.rows[0].value
assert(Math.abs(n(gaugeValue) - n(meta.rate.avg)) <= 0.05,
  `901 仪表盘(${gaugeValue}) 与全量门店平均达成率(${meta.rate.avg}) 同口径`,
  '两块屏同名指标给两个数，看的人只会怀疑数据')
const liveGauge = live.components.find((c) => c.type === 'gaugeChart')
assert(liveGauge && liveGauge.chartConfig.measures[0].field === 'achievement_rate'
  && String(liveGauge.chartConfig.measures[0].aggregation).toLowerCase() === 'avg',
  '911 仪表盘用 AVG(achievement_rate) 且不带维度（走 SqlBuilder 无 GROUP BY 分支）')
assert(dbRows[liveGauge.id] === 1, `911 仪表盘等价查询返回 1 行`, `rows=${dbRows[liveGauge.id]}`)

for (const c of live.components) {
  if (!chartPresetTypes.includes(c.type)) continue
  assert(dbRows[c.id] > 0, `911 ${c.id}(${c.name}) 等价查询有行`, `rows=${dbRows[c.id] === undefined ? '无记录' : dbRows[c.id]}`)
}

// 环比：trend 用真实计算值，且必须出现至少一个负数（全绿是假数据的味道）
const trends = cards.map((c) => c.props.trend).filter((t) => t !== undefined && t !== null)
assert(trends.length >= 3, `指标卡带环比 trend 的有 ${trends.length} 张`)
assert(trends.some((t) => n(t) < 0), '演示数据里存在负增长（不是每条都绿）', trends.join('/'))

// ---------------------------------------------------------------- 11 等价探针的 SQL 形状自己站得住

/**
 * 第 5 节核的是 chartConfig 的字段名，第 10 节核的是探针返回几行 —— 都核不到探针把 SQL 拼成了什么形状。
 * 这里的判据是本地自洽：一条 SQL 的 ORDER BY 目标，必须是它自己外层投影出来的别名或 GROUP BY 列。
 * 为什么必须加 —— 2026-10-19 探针把 SUM(amount) 命名成 `amount`（SqlBuilder 命名成 `sum_amount`），
 * 于是 ORDER BY `amount` 在探针里命中别名跑通、在真实 SQL 里落回裸列被 only_full_group_by 拒掉：
 * 探针报 6/4/21 行全绿，屏上三张图空着。探针替后端说谎比没有探针更糟，因为它把失败洗成了通过。
 */
{
  const blocks = {}
  let pending = null
  for (const line of probeSql.split(/\r?\n/)) {
    const cm = /^-- (\S+)\s/.exec(line)
    if (cm) { pending = cm[1]; continue }
    if (pending && line.indexOf('@@ROWS') >= 0) { blocks[pending] = line; pending = null }
  }
  assert(Object.keys(blocks).length >= 9,
    `探针里解析出 ${Object.keys(blocks).length} 条带 @@ROWS 的 SQL`, '格式变了的话下面的检查会集体空转')

  let orderChecked = 0
  for (const c of live.components) {
    const line = blocks[c.id]
    if (!line || !c.chartConfig || !c.chartConfig.datasetId) continue
    const open = line.indexOf('FROM (')
    const close = line.lastIndexOf(') wrapped;')
    if (open < 0 || close < 0) { note(`探针 ${c.id} 不是预期形状，跳过`); continue }
    const inner = line.slice(open + 6, close)
    const fromIdx = inner.indexOf(' FROM ')
    const outerSelect = fromIdx < 0 ? inner : inner.slice(0, fromIdx)
    const projected = new Set(
      [...outerSelect.matchAll(/ AS `([a-zA-Z0-9_]+)`/g)].map((m) => m[1]),
    )
    const grouped = new Set(
      [...(/GROUP BY (.*?)(?= ORDER BY | LIMIT |$)/.exec(inner) || ['', ''])[1]
        .matchAll(/`([a-zA-Z0-9_]+)`/g)].map((m) => m[1]),
    )
    const ob = (/ORDER BY (.*?)(?= LIMIT |$)/.exec(inner) || [null, ''])[1]
    if (!ob) continue
    for (const term of ob.split(', ')) {
      const m = /^`([a-zA-Z0-9_]+)` (ASC|DESC)$/.exec(term.trim())
      orderChecked++
      assert(m, `探针 ${c.id} 的排序项写法可解析：'${term}'`, '不是 `列名 ASC|DESC` 的形状')
      if (!m) continue
      assert(projected.has(m[1]) || grouped.has(m[1]),
        `探针 ${c.id} 的 ORDER BY ${m[1]} 落在外层投影或 GROUP BY 里`,
        `projected=[${[...projected].join(',')}] grouped=[${[...grouped].join(',')}] —— `
        + '裸列在 only_full_group_by 下会报 1140，接口仍是 200，页面只剩空图')
    }
  }
  assert(orderChecked >= 6, `确实核到了排序项（${orderChecked} 个）`, '核到 0 个说明这条检查是空的')
}

// ---------------------------------------------------------------- 出报告

console.log(`通过 ${passes.length} 项`)
if (notes.length) {
  console.log('说明（不判失败，但演示时会看见）：')
  for (const x of notes) console.log('  · ' + x)
}
if (fails.length) {
  console.log(`失败 ${fails.length} 项：`)
  for (const x of fails) console.log('  ✗ ' + x)
  process.exit(1)
}
console.log('BD5 组件契约全部通过（判据取自前端源码 + SqlBuilder + BD4 声明面）')
