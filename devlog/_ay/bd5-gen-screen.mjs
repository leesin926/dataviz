/**
 * BD5 大屏生成器：devlog/bd5-data.out（MySQL 回读）→ deploy/sql/patch/2026-10-18-bd-demo-screen.sql
 *
 * 为什么走"回读 → 生成 → 自检"而不是手写一段 JSON：
 *  1) 大屏上每个数字都必须是 BD2 造的演示数据层实查值，手写"好看的数"就是给全流程打通拍了张假照片；
 *  2) 组件契约（props 键名、chartConfig 字段、图层引用）由 bd5-contract.cjs 对着前端渲染器逐条核，
 *     手写的 JSON 没人核得动；
 *  3) 补丁要能重放。
 *
 * 两块屏分工：
 *  - 901 销售实时监控大屏：全部组件带 request.staticData ⇒ 免登链接 /s/{token} 就能整屏渲染，
 *    这是"看得见的好看"的证据，不依赖登录态；
 *  - 911 经营分析看板（数据集直连）：全部图表组件只带 chartConfig.datasetId ⇒ 走 /analysis/query 真链路，
 *    这是"数据模型 → 分析 → 大屏"打通的证据，需要登录后访问（免登面拿不到查询接口）。
 *
 * 画布锁白（CANVAS_BG_COLOR='#ffffff'，W1 决策）⇒ 配色只能取深色墨值：
 *  normalizeScreenInk 会把与白底亮度差 <90 的前景属性改写成品牌蓝，浅色描边在这套代码里存不住。
 * 因此下面所有 color/accent/barColor/trackColor 都取 CANVAS_INK 浅色档（其亮度差由契约自检逐个数过）。
 */
import fs from 'node:fs'
import path from 'node:path'
import crypto from 'node:crypto'

const ROOT = process.cwd()
const OUT_SQL = path.join(ROOT, 'deploy/sql/patch/2026-10-18-bd-demo-screen.sql')
const OUT_JSON = path.join(ROOT, 'devlog/_ay/bd5-contract.json')
const OUT_READBACK = path.join(ROOT, 'devlog/_ay/bd5-readback.sql')
const OUT_QUERY = path.join(ROOT, 'devlog/_ay/bd5-query.sql')

/**
 * 会走 ScreenChart 分支的类型（isChartPreset 白名单，见 shared-types/src/chart.ts）。
 * 自检 5/6b 用它做条件：只有这些组件必须有 chartConfig / datasetId，
 * text、decoration 本来两者都没有，不加类型条件会把它们误报成 BAD（本轮真遇过）。
 */
const CHART_TYPES = [
  'chart', 'barChart', 'stackBarChart', 'hBarChart', 'lineChart', 'areaChart', 'pieChart', 'donutChart',
  'roseChart', 'radarChart', 'gaugeChart', 'treemapChart', 'sunburstChart', 'heatmapChart',
]

// ---------------------------------------------------------------- 回读

/** 分隔符必须是 ~|~ ：mysql -B 会把真 tab 转义成字面 \t，读出来带反斜杠 */
function readout(file) {
  const text = fs.readFileSync(path.join(ROOT, file), 'utf8')
  const blocks = {}
  for (const line of text.split(/\r?\n/)) {
    if (!line.startsWith('@@')) continue
    const parts = line.split(' ~|~ ')
    const tag = parts[0].slice(2)
    ;(blocks[tag] || (blocks[tag] = [])).push(parts.slice(1).map((s) => s.trim()))
  }
  return blocks
}

const D = readout('devlog/bd5-data.out')
const need = (tag) => {
  const rows = D[tag]
  if (!rows || !rows.length) throw new Error(`回读缺块 @@${tag} —— 先跑 devlog/_ay/bd5-data.sql`)
  return rows
}
const n = (v) => Number(v)
const money = (v) => Math.round(Number(v))

const KPI = {
  amount: need('KPI')[0][0],
  orders: need('KPI')[0][1],
  profit: need('KPI')[0][2],
  avgPay: need('KPI')[0][3],
  achievement: need('KPI')[0][4],
  margin: need('KPI')[0][5],
  stores: need('KPI')[0][6],
}
const WOW = Object.fromEntries(need('WOW').map((r) => [r[0], n(r[1])]))
const WINDOW = need('WINDOW')[0] // [min, max, days]
const TREND = need('TREND') // [label, amount, profit]
const REGION = need('REGION') // [region, amount, profit, orders]
const CAT = need('CAT') // [category, amount]
const CC = need('CC') // [channel, category, amount]
const STORE = need('STORE') // [store_name, amount, achievement_rate]
const RATE = need('RATE') // [avg_achievement_rate, store_count] 全量门店简单平均
const HOUR = need('HOUR') // [stat_hour, orders, amount]
const MEMBER = need('MEMBER') // [member_level, amount, members, repurchase_rate]
const FEED = need('FEED') // [time, store, category, channel, amount]
const PROG = need('PROG') // [region, amount, target, rate]

// ---------------------------------------------------------------- 墨色（白底可读）

const INK = {
  strong: '#16202f',
  normal: '#263243',
  muted: '#5b6a80',
  line: '#dfe5ef',
  faint: 'rgba(22,32,47,0.06)',
  accent: '#2f7cff',
  warm: '#b7791f',
  ok: '#0f8a5f',
  danger: '#d4380d',
}
/** 系列色取自 CHART_PALETTE 浅色档（chart-engine 的 brand 主题已按画布亮度选档，这里只用于少量手写取色） */
const SERIES = { blue: INK.accent, green: INK.ok, gold: INK.warm, purple: '#7a5af8', orange: '#e8590c' }

/** echarts 原生标题：ScreenChart 不渲染 props.title，标题只能挂在 option 上才会真的出现在图上 */
const chartTitle = (text) => ({
  text,
  left: 14,
  top: 8,
  textStyle: { color: INK.strong, fontSize: 15, fontWeight: 600 },
})

// ---------------------------------------------------------------- 组件工厂

let seq = 0
function base(type, name, geo, props) {
  seq += 1
  return {
    id: `c${seq}`,
    type,
    name,
    x: geo[0],
    y: geo[1],
    w: geo[2],
    h: geo[3],
    zIndex: seq,
    visible: true,
    locked: false,
    props,
  }
}

/** 静态数据图表：chartConfig（语义）+ request.staticData（宽表行），两者字段名必须一字不差对上 */
function staticChart(presetType, chartType, name, geo, dims, mets, rows, options, extraProps) {
  const c = base(presetType, name, geo, { ...extraProps })
  c.chartConfig = {
    chartType,
    datasetId: null,
    dimensions: dims,
    measures: mets,
    options: { title: chartTitle(name), ...options },
    theme: 'brand',
    animation: true,
  }
  c.request = { sourceType: 'static', staticData: { columns: columnsOf(dims, mets), rows } }
  return c
}

/** 数据集直连图表：不给 request ⇒ ScreenChart 落到 datasetId 分支，POST /analysis/query/execute 真查 */
function datasetChart(presetType, chartType, name, geo, datasetId, dims, mets, sorts, options) {
  const c = base(presetType, name, geo, {})
  c.chartConfig = {
    chartType,
    datasetId,
    dimensions: dims,
    measures: mets,
    sorts,
    options: { title: chartTitle(name), ...options },
    theme: 'brand',
    animation: true,
  }
  c.request = { sourceType: 'dataset' }
  return c
}

/** staticData 的 columns 由维度+度量字段拼出：行键必须与 chartConfig 点名的字段同名 */
function columnsOf(dims, mets) {
  return [
    ...dims.map((d) => ({ field: d.field, type: d.type === 'time' ? 'date' : 'string' })),
    ...mets.map((m) => ({ field: m.alias || m.field, type: 'number' })),
  ]
}

const dim = (field, type) => ({ field, ...(type ? { type } : {}) })
const met = (field, aggregation, alias) => ({ field, aggregation, ...(alias ? { alias } : {}) })

// ---------------------------------------------------------------- 901：静态保证渲染的实时监控大屏
//
// 1920×1080，四段：头部（标题/跑马灯/时钟）→ 5 张指标卡 → 三行内容区（趋势/对比 → 结构/排行 → 时段/流水/明细）。
// 指标卡的 trend（环比）来自 @@WOW，正负号是真实涨跌（近 7 日订单是 -6.2%，演示时正好能说明"没有全绿"）。

function build901() {
  const cs = []
  const layer = { 头部: [], 指标卡: [], 趋势对比: [], 结构排行: [], 明细实时: [], 页脚: [] }
  const put = (comp, groupName) => {
    cs.push(comp)
    layer[groupName].push(comp.id)
    return comp
  }

  put(base('decoration', '顶部流动线', [30, 104, 1860, 8], { variant: 'flow', color: INK.accent, colorSecond: INK.ok }), '头部')
  put(base('text', '主标题', [30, 22, 640, 46], {
    text: '销售实时监控中心',
    fontSize: '32px',
    fontWeight: '700',
    color: INK.strong,
    textAlign: 'left',
    lineHeight: '1.2',
  }), '头部')
  put(base('text', '副标题', [30, 66, 640, 28], {
    text: `演示数据 · 统计窗口 ${WINDOW[0]} ~ ${WINDOW[1]}（${WINDOW[2]} 天）· 口径 DWD 日汇总`,
    fontSize: '13px',
    color: INK.muted,
    textAlign: 'left',
    lineHeight: '1.2',
  }), '头部')
  put(base('marquee', '运行状态滚动', [700, 30, 630, 40], {
    texts: [
      `门店 ${KPI.stores} 家 / 订单 ${KPI.orders} 笔`,
      `覆盖 ${REGION.length} 大区 · ${new Set(CC.map((r) => r[0])).size} 渠道 · ${CAT.length} 类目`,
      `目标达成率 ${KPI.achievement}%`,
      `毛利率 ${KPI.margin}%`,
      `数据窗口 ${WINDOW[2]} 天`,
    ],
    speed: 32,
    color: INK.accent,
    textColor: INK.normal,
    fontSize: '15px',
  }), '头部')
  put(base('clock', '时钟', [1490, 20, 400, 72], {
    fontSize: '26px',
    color: INK.normal,
    dateFormat: 'YYYY-MM-DD',
  }), '头部')

  // —— 指标卡：5 张等宽 360，间距 15，左右各留 30 ——
  const kpiX = [30, 405, 780, 1155, 1530]
  const cards = [
    ['累计销售额', money(KPI.amount), '元', SERIES.blue, WOW.amount],
    ['订单总量', n(KPI.orders), '单', SERIES.green, WOW.orders],
    ['累计毛利', money(KPI.profit), '元', SERIES.purple, WOW.profit],
    ['客单价', money(KPI.avgPay), '元', SERIES.orange, undefined],
    ['目标达成率', n(KPI.achievement), '%', SERIES.gold, Math.round((n(KPI.achievement) - 100) * 10) / 10],
  ]
  cards.forEach(([title, value, unit, accent, trend], i) => {
    const props = {
      title,
      value,
      unit,
      color: INK.strong,
      accent,
      bgColor: INK.faint,
      fontSize: '28px',
    }
    if (typeof trend === 'number') props.trend = trend
    put(base('statCard', title, [kpiX[i], 118, 360, 96], props), '指标卡')
  })

  // —— 第二行：趋势（左 920）+ 大区对比（右 920）——
  put(staticChart('areaChart', 'line', '近 14 日销售额与毛利', [30, 228, 920, 296],
    [dim('stat_date', 'time')],
    [met('amount', 'sum'), met('profit', 'sum')],
    TREND.map(([stat_date, amount, profit]) => ({ stat_date, amount: n(amount), profit: n(profit) })),
    { area: true, smooth: true },
    { precision: 0 }), '趋势对比')
  put(staticChart('barChart', 'bar', '大区销售额与毛利', [970, 228, 920, 296],
    [dim('region')],
    [met('amount', 'sum'), met('profit', 'sum')],
    REGION.map(([region, amount, profit]) => ({ region, amount: n(amount), profit: n(profit) })),
    {},
    {}), '趋势对比')

  // —— 第三行：类目环图 + 渠道×类目堆叠 + 门店 TOP 排行 ——
  put(staticChart('donutChart', 'pie', '类目销售额结构', [30, 538, 520, 288],
    [dim('category')],
    [met('amount', 'sum')],
    CAT.map(([category, amount]) => ({ category, amount: n(amount) })),
    { donut: true },
    {}), '结构排行')
  put(staticChart('stackBarChart', 'bar', '渠道 × 类目销售额', [565, 538, 640, 288],
    [dim('channel'), dim('category')],
    [met('amount', 'sum')],
    CC.map(([channel, category, amount]) => ({ channel, category, amount: n(amount) })),
    { stack: true },
    {}), '结构排行')
  put(base('ranking', '门店销售额 TOP10', [1220, 538, 670, 288], {
    items: STORE.map(([store_name, amount]) => ({ name: store_name, value: n(amount) })),
    color: INK.normal,
    barColor: SERIES.blue,
    showValue: true,
    fontSize: '13px',
  }), '结构排行')

  // —— 第四行：时段柱 + 门店平均达成仪表 + 实时流水 + 会员价值表 ——
  put(staticChart('barChart', 'bar', '时段订单分布', [30, 840, 470, 204],
    [dim('stat_hour')],
    [met('orders', 'sum')],
    HOUR.map(([stat_hour, orders]) => ({ stat_hour, orders: n(orders) })),
    {},
    {}), '明细实时')

  const gauge = base('gaugeChart', '门店平均达成率', [515, 840, 300, 204], {})
  gauge.chartConfig = {
    chartType: 'gauge',
    datasetId: null,
    title: '达成率 %',
    dimensions: [dim('name')],
    measures: [met('value', 'sum')],
    options: { max: 150, min: 0 },
    theme: 'brand',
    animation: true,
  }
  // 全量门店的简单平均（@@RATE 探针），与 911 那台直连 915 的 AVG(achievement_rate) 同口径：
  // 同一块屏上"门店平均达成率"出现两个数，看的人只会怀疑数据而不是怀疑定义。
  const avgStoreRate = n(RATE[0][0])
  gauge.request = {
    sourceType: 'static',
    staticData: { columns: [{ field: 'name', type: 'string' }, { field: 'value', type: 'number' }], rows: [{ name: '达成率', value: avgStoreRate }] },
  }
  put(gauge, '明细实时')

  put(base('scrollBoard', '实时成交流水', [830, 840, 470, 204], {
    columns: ['时间', '门店', '类目', '金额'],
    rows: FEED.map(([t, store, cat, , amount]) => [t, store, cat, amount]),
    interval: 2,
    fontSize: '13px',
    color: INK.normal,
  }), '明细实时')

  put(base('table', '会员等级价值', [1315, 840, 575, 204], {
    columns: ['等级', '消费额', '人数', '复购率%'],
    rows: MEMBER.map(([level, amount, members, repurchase]) => [level, amount, members, repurchase]),
    fontSize: '13px',
    color: INK.normal,
  }), '明细实时')

  put(base('text', '页脚血缘', [30, 1052, 1560, 24], {
    text: '血缘：db_model.demo_orders → ods_order_detail → dwd_sales_daily → ads_*（BD2 造数 / BD3 ETL 装载）→ 数据集 912·915·916·917·918 → 本大屏静态回读',
    fontSize: '13px',
    color: INK.muted,
    textAlign: 'left',
    lineHeight: '1.2',
  }), '页脚')
  put(base('decoration', '右下角律动柱', [1620, 1044, 270, 30], {
    variant: 'bars',
    color: SERIES.blue,
    colorSecond: SERIES.green,
  }), '页脚')

  // 大区目标达成：进度条一行一条，label 用真实大区名，percent 用实查达成率
  PROG.forEach(([region, , , rate], i) => {
    put(base('progress', `${region}目标达成`, [30 + i * 312, 1000, 300, 40], {
      label: `${region} 达成`,
      percent: n(rate),
      color: n(rate) >= 100 ? SERIES.green : SERIES.gold,
      trackColor: INK.faint,
      textColor: INK.normal,
      height: 10,
      showValue: true,
    }), '页脚')
  })

  const layers = Object.entries(layer)
    .filter(([, ids]) => ids.length)
    .map(([groupName, ids], i) => ({
      id: `ly_${i + 1}`,
      name: groupName,
      visible: true,
      locked: false,
      componentIds: ids,
    }))

  return {
    id: 901,
    name: '销售实时监控大屏',
    description: 'BD5 演示屏①：21 个组件全部带 request.staticData，数值来自 devlog/_ay/bd5-data.sql 对 db_analysis 演示层的实查回读，免登链接即可整屏渲染（不依赖登录态与查询接口）。',
    width: 1920,
    height: 1080,
    status: 1,
    adaptMode: 'scale',
    shareToken: 'smoke0921aaaa',
    layers,
    config: {
      width: 1920,
      height: 1080,
      layers,
      adaptationMode: 'scale',
      backgroundColor: '#ffffff',
      backgroundSize: 'cover',
      backgroundRepeat: 'no-repeat',
      gridSnap: true,
      gridSize: 10,
      globalRefreshInterval: 0,
    },
    components: cs,
    variants: { mobile: build901Mobile(cs) },
  }
}

/**
 * 901 的移动端变体：只放 ALL_ENDS 且 uni 端可原生渲染的类型（statCard/marquee/progress/ranking 是 PC_ONLY，
 * 挂上去会被 isRenderableOnPlatform 静默跳过 —— 与其留一堆"这条端看不见"的组件，不如按端重新摆一屏）。
 */
function build901Mobile(pcComponents) {
  const pick = (name) => pcComponents.find((c) => c.name === name)
  const rows = []
  const title = pick('主标题')
  rows.push({ ...title, x: 12, y: 16, w: 366, h: 34, zIndex: 1, props: { ...title.props, text: '销售监控（移动版）', fontSize: '20px' } })
  const clock = pick('时钟')
  rows.push({ ...clock, x: 12, y: 54, w: 366, h: 40, zIndex: 2, props: { ...clock.props, fontSize: '16px' } })
  const trend = pick('近 14 日销售额与毛利')
  rows.push({ ...trend, x: 12, y: 104, w: 366, h: 240, zIndex: 3 })
  const region = pick('大区销售额与毛利')
  rows.push({ ...region, x: 12, y: 354, w: 366, h: 240, zIndex: 4 })
  const cat = pick('类目销售额结构')
  rows.push({ ...cat, x: 12, y: 604, w: 366, h: 220, zIndex: 5 })
  const feed = pick('实时成交流水')
  rows.push({ ...feed, x: 12, y: 834, w: 366, h: 220, zIndex: 6 })
  rows.forEach((c) => {
    delete c.platforms
    c.locked = false
    c.visible = true
  })
  const layers = [{ id: 'ly_m1', name: '移动一组', visible: true, locked: false, componentIds: rows.map((r) => r.id) }]
  return {
    width: 390,
    height: 1064,
    config: {
      width: 390,
      height: 1064,
      layers,
      adaptationMode: 'scale',
      backgroundColor: '#ffffff',
      backgroundSize: 'cover',
      backgroundRepeat: 'no-repeat',
      gridSnap: true,
      gridSize: 10,
      globalRefreshInterval: 0,
    },
    components: rows,
    layers,
  }
}

// ---------------------------------------------------------------- 911：数据集直连的分析看板
//
// 组件不写 request.staticData —— 拿不到真查询就是一片空，正是要用登录态去验的那半区。
// 选数据集时避开两条行列权限规则挂住的 913（行：role user 只看华东）与 915（列：遮 profit/margin_rate）；
// 915 只被用来画"门店平均达成率"仪表，引用的是未受限列，正好演示"遮列不影响别的指标"。

function build911(datasets) {
  const cs = []
  const seqNo = { v: 0 }
  const add = (c) => {
    seqNo.v += 1
    c.id = `d${seqNo.v}`
    c.zIndex = seqNo.v
    cs.push(c)
    return c
  }

  add(base('text', '主标题', [30, 20, 900, 42], {
    text: '经营分析看板（数据集直连）',
    fontSize: '30px',
    fontWeight: '700',
    color: INK.strong,
    textAlign: 'left',
    lineHeight: '1.2',
  }))
  add(base('text', '链路说明', [30, 64, 1860, 26], {
    text: '本屏 8 个图表组件只绑 model_dataset（912/915/916/917/919/920/921），渲染时经 ScreenChart → executeQuery → POST /api/analysis/query/execute 现取现画；该查询接口需登录，免登链接打开时组件会显式报"取数失败"（不再是静默空屏），登录态下同一链接出真数据 —— 这一屏的验收必须在登录态下做。',
    fontSize: '13px',
    color: INK.muted,
    textAlign: 'left',
    lineHeight: '1.2',
  }))

  const W = 600
  const H = 292
  const X = [30, 650, 1270]
  const XW = [W, W, 620]
  const Y = [104, 412, 720]

  add(datasetChart('areaChart', 'line', '每日销售趋势（数据集 921）', [X[0], Y[0], XW[0], H], 921,
    [dim('stat_date', 'time')], [met('amount', 'sum')], [{ field: 'stat_date', order: 'asc' }], { area: true }))
  add(datasetChart('barChart', 'bar', '大区销售与毛利（数据集 912）', [X[1], Y[0], XW[1], H], 912,
    [dim('region')], [met('amount', 'sum'), met('profit', 'sum')], [{ field: 'amount', order: 'desc' }], {}))
  add(datasetChart('donutChart', 'pie', '会员等级价值（数据集 917）', [X[2], Y[0], XW[2], H], 917,
    [dim('member_level')], [met('amount', 'sum')], [{ field: 'amount', order: 'desc' }], { donut: true }))

  add(datasetChart('barChart', 'bar', '时段成交分布（数据集 916）', [X[0], Y[1], XW[0], H], 916,
    [dim('stat_hour')], [met('amount', 'sum'), met('orders', 'sum')], [{ field: 'stat_hour', order: 'asc' }], {}))
  add(datasetChart('stackBarChart', 'bar', '渠道×品类毛利矩阵（数据集 920）', [X[1], Y[1], XW[1], H], 920,
    [dim('channel'), dim('category')], [met('amount', 'sum')], [], { stack: true }))
  add(datasetChart('pieChart', 'pie', '渠道销售额占比（数据集 912）', [X[2], Y[1], XW[2], H], 912,
    [dim('channel')], [met('amount', 'sum')], [{ field: 'amount', order: 'desc' }], {}))

  add(datasetChart('lineChart', 'line', '每日毛利趋势（数据集 921）', [X[0], Y[2], XW[0], H], 921,
    [dim('stat_date', 'time')], [met('profit', 'sum')], [{ field: 'stat_date', order: 'asc' }], {}))
  add(datasetChart('hBarChart', 'bar', '门店销售额排行（数据集 919 跨库SQL）', [X[1], Y[2], XW[1], H], 919,
    [dim('store_name')], [met('amount', 'sum')], [{ field: 'amount', order: 'desc' }], { horizontal: true }))

  // 0 维度聚合：SqlBuilder 走 "无 GROUP BY 的整表聚合"，仪表只要一个数 —— 顺带把这条口径也验一遍
  const gauge = datasetChart('gaugeChart', 'gauge', '门店平均达成率（数据集 915 未受限列）', [X[2], Y[2], XW[2], H], 915,
    [], [met('achievement_rate', 'avg')], [], { max: 160, min: 0 })
  gauge.chartConfig.title = '平均达成率 %'
  add(gauge)

  const groups = [
    ['标题与说明', cs.slice(0, 2)],
    ['趋势与结构', cs.slice(2, 5)],
    ['渠道与品类', cs.slice(5, 7)],
    ['门店与达成', cs.slice(7)],
  ]
  const layers = groups.map(([groupName, list], i) => ({
    id: `ly_${i + 1}`,
    name: groupName,
    visible: true,
    locked: false,
    componentIds: list.map((c) => c.id),
  }))

  return {
    id: 911,
    name: '经营分析看板（数据集直连）',
    description: 'BD5 演示屏②：9 个图表组件绑 BD4 的数据集，不写静态数据；登录态下每个组件发一次 /analysis/query/execute。datasetId 避开行级规则(913)与列级规则(915 遮 profit/margin_rate)会影响的字段，915 只取未受限的 achievement_rate。',
    width: 1920,
    height: 1080,
    status: 1,
    adaptMode: 'scale',
    shareToken: crypto.createHash('md5').update('dataviz-bd5-demo-screen-911').digest('hex'),
    layers,
    config: {
      width: 1920,
      height: 1080,
      layers,
      adaptationMode: 'scale',
      backgroundColor: '#ffffff',
      backgroundSize: 'cover',
      backgroundRepeat: 'no-repeat',
      gridSnap: true,
      gridSize: 10,
      globalRefreshInterval: 0,
    },
    components: cs,
    variants: null,
    _datasets: datasets,
  }
}

// ---------------------------------------------------------------- 出 SQL

const str = (v) => `'${String(v).replace(/\\/g, '\\\\').replace(/'/g, "''")}'`
const json = (v) => str(JSON.stringify(v))

const datasets = JSON.parse(fs.readFileSync(path.join(ROOT, 'devlog/_ay/bd4-contract.json'), 'utf8')).datasets

const screen901 = build901()
const screen911 = build911(datasets)
const SCREENS = [screen901, screen911]

// screen.create_by 是 bigint（存 user id），早期草稿按字符串塞过 'admin' → 1366。admin 的 id 是 1。
const CREATE_BY = 1
for (const s of SCREENS) s.createBy = CREATE_BY

const lines = []
lines.push('-- ============================================================================')
lines.push('-- BD5 演示大屏补丁：db_screen.screen 两块（901 静态回读 / 911 数据集直连）')
lines.push('-- 由 devlog/_ay/bd5-gen-screen.mjs 生成，数据来自 devlog/_ay/bd5-data.out（对 db_analysis 演示层的实查回读）')
lines.push('-- 改布局请改生成器再重跑，不要手改本文件：组件契约由 devlog/_ay/bd5-contract.cjs 对着渲染器核。')
lines.push('--')
lines.push('-- 为什么是两块而不是一块：')
lines.push('--   901 每个组件都带 request.staticData ⇒ 免登链接 /s/{token} 就能渲染，这是"看得见"的那半；')
lines.push('--   911 只带 chartConfig.datasetId ⇒ ScreenChart 调 executeQuery（POST /api/analysis/query/execute，')
lines.push('--   需登录）⇒ 它是"数据模型→分析→大屏"打通的那半，只能在登录态验收。')
lines.push('--   合在一起的屏没法分辨"配好了但查不出来"和"根本没配"。')
lines.push('--')
lines.push('-- 画布锁白（shared-types CANVAS_BG_COLOR）⇒ 前景一律深色墨值；浅色描边会被 normalizeScreenInk')
lines.push('--   改写成品牌蓝，所以这里不出现浅色 color 属性（亮度差由契约自检逐个算过）。')
lines.push('--')
lines.push('-- screen_component（旧镜像表，id 901-908）本补丁不动：渲染链只解析 screen.components_json')
lines.push('--   （ScreenServiceImpl.toVO），该表除 /api/screen/component 那组 CRUD 外没有任何读取方，')
lines.push('--   也没有任何前端调用。给它补一份镜像等于凭猜测定义一套没人消费的列语义 —— 宁可留旧行并写明。')
lines.push('-- ============================================================================')
lines.push('')
lines.push('SET NAMES utf8mb4;')
lines.push('USE db_screen;')
lines.push('')
lines.push('-- 901 原地重建：id 与分享令牌 smoke0921aaaa 保持不动，之前发出去的免登链接继续可用。')
lines.push('-- 整段包在事务里：先 DELETE 再 INSERT，INSERT 一旦失败（本轮真遇过 create_by 塞了字符串，')
lines.push('--   列是 bigint → 1366）不留"屏已删、新屏没进"的半状态 —— 免登链接会变 404。')
lines.push('START TRANSACTION;')
lines.push('DELETE FROM screen WHERE id IN (901, 911);')
lines.push('')

for (const s of SCREENS) {
  lines.push(`-- ${s.id} ${s.name}：${s.components.length} 个组件`)
  lines.push('INSERT INTO screen (id, tenant_id, name, description, config_json, components_json, variants_json,')
  lines.push('                            cover_url, status, adapt_mode, view_count, share_token, create_by, deleted)')
  lines.push(`  VALUES (${s.id}, 1, ${str(s.name)}, ${str(s.description)},`)
  lines.push(`    ${json(s.config)},`)
  lines.push(`    ${json(s.components)},`)
  lines.push(`    ${s.variants ? json(s.variants) : 'NULL'},`)
  lines.push(`    NULL, ${s.status}, ${str(s.adaptMode)}, 0, ${str(s.shareToken)}, ${s.createBy}, 0);`)
  lines.push('')
}

lines.push('COMMIT;')
lines.push('')

// ---------------------------------------------------------------- 自检

lines.push('-- ============ 自检 ============')
lines.push('-- 跑法：mysql --force（默认遇错即退，退掉的那条永远不产出 @@BAD，看起来就"全过"了）')
lines.push('-- 判据：stderr 里 ERROR 行数为 0，且 stdout 里以 @@BAD 开头的行数为 0，且最后一行是 @@CHECKS-END。')
lines.push('--   （只看"行首"：mysql -B 会把 SELECT 表达式本身当列头回显，头一行里就带着 @@BAD 字面量）')
lines.push('-- 三条缺一不可 —— "@@BAD 缺席"只有和"全部执行完毕"同时成立才是通过。')
lines.push('-- 1. 数量与发布态：两块屏都必须 status=1 且有令牌，否则免登链接回"尚未发布"')
lines.push(`SELECT CONCAT('@@COUNT screen_demo = ', COUNT(*)) FROM screen WHERE id IN (901, 911) AND deleted = 0 AND status = 1 AND share_token IS NOT NULL;`)
lines.push('')
lines.push('-- 2. 组件数：JSON_LENGTH 与声明一致（少了就是 INSERT 时被截断，典型症状是页面缺半屏）')
lines.push(`SELECT CONCAT('@@LEN ', id, ' ~|~ ', JSON_LENGTH(components_json)) FROM screen WHERE id IN (901, 911);`)
lines.push('')
lines.push('-- 3. 图层引用完整性：layers 里每个 componentId 都必须真的在 components_json 里')
lines.push(`SELECT CONCAT('@@BAD layer-ref ', s.id, ' ~|~ ', l.id, ' ~|~ ', l.cid)
FROM screen s
JOIN JSON_TABLE(s.config_json, '$.layers[*]' COLUMNS (
  id VARCHAR(64) PATH '$.id',
  NESTED PATH '$.componentIds[*]' COLUMNS (cid VARCHAR(64) PATH '$')
)) l
WHERE s.id IN (901, 911) AND s.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM JSON_TABLE(s.components_json, '$[*]' COLUMNS (
      cid VARCHAR(64) PATH '$.id')) j WHERE j.cid = l.cid);`)
lines.push('-- 3b. 反向：有组件没进任何图层（编辑器图层面板会漏项）')
lines.push(`SELECT CONCAT('@@BAD orphan ', s.id, ' ~|~ ', c.cid)
FROM screen s
JOIN JSON_TABLE(s.components_json, '$[*]' COLUMNS (cid VARCHAR(64) PATH '$.id')) c
WHERE s.id IN (901, 911) AND s.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM JSON_TABLE(s.config_json, '$.layers[*]' COLUMNS (
      NESTED PATH '$.componentIds[*]' COLUMNS (cid VARCHAR(64) PATH '$'))) t WHERE t.cid = c.cid);`)
lines.push('')
lines.push('-- 4. 组件类型必须在渲染注册表里（拼错 type 的症状是 ScreenUnknown 灰块，不是报错）')
lines.push(`SELECT CONCAT('@@BAD type ', s.id, ' ~|~ ', c.type)
FROM screen s
JOIN JSON_TABLE(s.components_json, '$[*]' COLUMNS (type VARCHAR(32) PATH '$.type')) c
WHERE s.id IN (901, 911) AND s.deleted = 0
  AND c.type NOT IN ('chart','barChart','stackBarChart','hBarChart','lineChart','areaChart','pieChart','donutChart',
                    'roseChart','radarChart','gaugeChart','text','marquee','countdown','clock','image','border',
                    'decoration','map','video','iframe','table','scrollBoard','numberFlop','statCard','progress',
                    'ranking','waterPolo','flyline');`)
lines.push('')
lines.push('-- 5. 图表组件必须真的带 chartConfig（这是原 901 种子的缺陷：只有 chartPreset 字符串，')
lines.push('--    ScreenChart 的 v-if 拿不到 chartConfig ⇒ 三张图画成"未配置图表"占位框）')
lines.push(`SELECT CONCAT('@@BAD no-chartConfig ', s.id, ' ~|~ ', c.cid, ' ~|~ ', c.type)
FROM screen s
JOIN JSON_TABLE(s.components_json, '$[*]' COLUMNS (
  cid VARCHAR(64) PATH '$.id', type VARCHAR(32) PATH '$.type',
  cfg JSON PATH '$.chartConfig'
)) c
WHERE s.id IN (901, 911) AND s.deleted = 0
  AND c.type IN (${CHART_TYPES.map((t) => `'${t}'`).join(',')})
  AND c.cfg IS NULL;`)
lines.push('')
lines.push('-- 6. 901 的每个静态组件必须有行（request.staticData.rows 非空），否则免登面是一片空框')
lines.push(`SELECT CONCAT('@@BAD empty-static ', s.id, ' ~|~ ', c.cid)
FROM screen s
JOIN JSON_TABLE(s.components_json, '$[*]' COLUMNS (
  cid VARCHAR(64) PATH '$.id',
  src VARCHAR(16) PATH '$.request.sourceType',
  rows_j JSON PATH '$.request.staticData.rows'
)) c
WHERE s.id = 901 AND s.deleted = 0
  AND (c.src = 'static' OR c.rows_j IS NOT NULL)
  AND (c.rows_j IS NULL OR JSON_LENGTH(c.rows_j) = 0);`)
lines.push('-- 6b. 911 反过来：图表组件不许带 staticData（带了就证明不了真链路）')
lines.push('--     只核图表类型：文本/装饰这类组件本来就既没有 datasetId 也没有 rows，')
lines.push('--     早先不带类型条件时把 d1/d2 两个 text 也报成了 BAD。')
lines.push(`SELECT CONCAT('@@BAD static-in-live-screen ', s.id, ' ~|~ ', c.cid)
FROM screen s
JOIN JSON_TABLE(s.components_json, '$[*]' COLUMNS (
  cid VARCHAR(64) PATH '$.id', type VARCHAR(32) PATH '$.type',
  src VARCHAR(16) PATH '$.request.sourceType',
  dsid BIGINT PATH '$.chartConfig.datasetId'
)) c
WHERE s.id = 911 AND s.deleted = 0
  AND c.type IN (${CHART_TYPES.map((t) => `'${t}'`).join(',')})
  AND (c.src = 'static' OR c.dsid IS NULL);`)
lines.push('')
lines.push('-- 7. 数据集引用完整性：911 绑的 datasetId 必须存在于 db_model.model_dataset 且未删除')
lines.push(`SELECT CONCAT('@@BAD dataset ', s.id, ' ~|~ ', c.cid, ' ~|~ ', c.dsid)
FROM db_screen.screen s
JOIN JSON_TABLE(s.components_json, '$[*]' COLUMNS (cid VARCHAR(64) PATH '$.id', dsid BIGINT PATH '$.chartConfig.datasetId')) c
LEFT JOIN db_model.model_dataset d ON d.id = c.dsid AND d.deleted = 0
WHERE s.id = 911 AND s.deleted = 0 AND c.dsid IS NOT NULL AND d.id IS NULL;`)
lines.push('')
lines.push('-- 8. 画布越界：组件必须落在画布内（超出去的部分不会滚动，是"少了一块"而不是"报错"）')
lines.push(`SELECT CONCAT('@@BAD geometry ', s.id, ' ~|~ ', c.cid, ' ~|~ ', c.x + c.w, '/', c.y + c.h)
FROM screen s
JOIN JSON_TABLE(s.components_json, '$[*]' COLUMNS (
  cid VARCHAR(64) PATH '$.id', x INT PATH '$.x', y INT PATH '$.y', w INT PATH '$.w', h INT PATH '$.h')) c
WHERE s.id IN (901, 911) AND s.deleted = 0
  AND (c.x < 0 OR c.y < 0 OR c.x + c.w > JSON_EXTRACT(s.config_json, '$.width')
                    OR c.y + c.h > JSON_EXTRACT(s.config_json, '$.height'));`)
lines.push('')
lines.push('-- 9. 令牌唯一：两个令牌指向两块屏时，后写的那块会盖掉前一块的免登入口')
lines.push(`SELECT CONCAT('@@BAD token-dup ', share_token, ' ~|~ ', COUNT(*)) FROM screen WHERE deleted = 0 AND share_token IS NOT NULL GROUP BY share_token HAVING COUNT(*) > 1;`)
lines.push('')
lines.push('-- 10. 旧种子缺陷回归：901 不许再出现"只有 chartPreset 没有 chartConfig"的组件')
lines.push(`SELECT CONCAT(IF(s.id IN (901,911), '@@BAD ', '@@NOTE '), s.id, ' ~|~ preset-only ~|~ ', c.cid, ' ~|~ ', c.preset)
FROM screen s
JOIN JSON_TABLE(s.components_json, '$[*]' COLUMNS (
  cid VARCHAR(64) PATH '$.id', preset VARCHAR(64) PATH '$.chartPreset', cfg JSON PATH '$.chartConfig')) c
WHERE s.deleted = 0 AND c.preset IS NOT NULL AND c.cfg IS NULL;`)
lines.push('')
lines.push('-- 11. statCard 的数值必须落在 props.value（渲染器读 props，不读 staticData.rows；')
lines.push('--     旧种子把数塞在 staticData.rows[0].value 里，卡片显示成 0）')
lines.push(`SELECT CONCAT(IF(s.id IN (901,911), '@@BAD ', '@@NOTE '), s.id, ' ~|~ statCard-no-props.value ~|~ ', c.cid)
FROM screen s
JOIN JSON_TABLE(s.components_json, '$[*]' COLUMNS (
  cid VARCHAR(64) PATH '$.id', type VARCHAR(32) PATH '$.type', val JSON PATH '$.props.value')) c
WHERE s.deleted = 0 AND c.type = 'statCard' AND c.val IS NULL;`)
lines.push('')
lines.push('-- 12. 三端变体：901 的 mobile 变体组件数与令牌（变体缺 components 的症状是移动端整屏空白）')
lines.push(`SELECT CONCAT('@@VARIANT ', id, ' ~|~ mobile ~|~ ', JSON_LENGTH(JSON_EXTRACT(variants_json, '$.mobile.components')))
FROM screen WHERE id = 901;`)
lines.push('')
lines.push('-- 13. 字段核对交给 Node：图表点名的字段是否真的存在于数据集/静态行里，SQL 层核不动（渲染器语义在前端）')
lines.push(`SELECT CONCAT('@@NOTE 组件契约核对请跑 node devlog/_ay/bd5-contract.cjs') AS n;`)
lines.push(`SELECT '@@CHECKS-END' AS end_marker;`)
lines.push('')
lines.push('-- ============================================================================')
lines.push('-- rollback（人工执行）：901 退回本补丁之前的种子（6 个组件、无变体）不在本文件职责内，')
lines.push('--   需要回退时按 devlog/bd5-dump.out 里留档的原 components_json 逐字 UPDATE 回去。')
lines.push('--   USE db_screen;')
lines.push('--   DELETE FROM screen WHERE id = 911;')
lines.push('--   -- 901 用留档 JSON 还原，别直接删（删了免登链接就 404，而链接已经发出去了）')
lines.push('-- ============================================================================')

fs.writeFileSync(OUT_SQL, lines.join('\n') + '\n', 'utf8')

// ---------------------------------------------------------------- 契约 JSON

const contract = {
  screens: SCREENS.map((s) => ({
    id: s.id,
    name: s.name,
    width: s.config.width,
    height: s.config.height,
    status: s.status,
    shareToken: s.shareToken,
    layers: s.layers,
    components: s.components,
    variants: s.variants,
  })),
  meta: {
    window: WINDOW,
    kpi: KPI,
    wow: WOW,
    // 仪表盘读数：全量门店简单平均（与 911 直连 915 的 AVG 同口径）
    rate: { avg: RATE[0][0], stores: RATE[0][1] },
    rowCount: {
      TREND: TREND.length, REGION: REGION.length, CAT: CAT.length, CC: CC.length,
      STORE: STORE.length, HOUR: HOUR.length, MEMBER: MEMBER.length, FEED: FEED.length, PROG: PROG.length,
    },
  },
}
fs.writeFileSync(OUT_JSON, JSON.stringify(contract, null, 2) + '\n', 'utf8')

// ---------------------------------------------------------------- 回读 / 真链路探针

fs.writeFileSync(OUT_READBACK, [
  'SET NAMES utf8mb4;',
  '-- 读回两块屏的 JSON（供 bd5-contract.cjs 与声明逐字比对）；分隔符 ~|~ 的理由见 bd4-gen-model.mjs',
  '-- 跑法必须带 --raw：mysql -B 默认把 \\\\ 折成 \\，JSON 里的转义引号被吃掉一层后',
  '--   JSON.parse 直接炸，症状是"库里与声明不一致"——其实库里是好的，是探针自己改了字节。',
  '--   本轮真实踩过（画布文案里一个英文引号就足够），所以判据写在跑法里而不是靠人记。',
  'USE db_screen;',
  `SELECT CONCAT('@@SCREEN ', id, ' ~|~ ', name, ' ~|~ ', status, ' ~|~ ', IFNULL(share_token,\'NULL\')) FROM screen WHERE id IN (901, 911) ORDER BY id;`,
  `SELECT CONCAT('@@CFG ', id, ' ~|~ ', config_json) FROM screen WHERE id IN (901, 911) ORDER BY id;`,
  `SELECT CONCAT('@@CMP ', id, ' ~|~ ', components_json) FROM screen WHERE id IN (901, 911) ORDER BY id;`,
  `SELECT CONCAT('@@VAR ', id, ' ~|~ ', IFNULL(variants_json,\'NULL\')) FROM screen WHERE id IN (901, 911) ORDER BY id;`,
].join('\n') + '\n', 'utf8')

// 911 每个数据集组件：按 SqlBuilder 会拼的形状就地重跑一遍（等价探针，证明"绑得上去"）
const q = []
q.push('SET NAMES utf8mb4;')
q.push('-- 911 直连组件的等价查询：把 chartConfig 按 SqlBuilder 的规则重写成 SQL 就地跑，')
q.push('-- 行数>0 且列名含图表点名的字段 = 数据集侧契约成立（渲染还需要登录，这一半只能这样先证）。')
q.push('-- 注意：这里不带行级权限谓词（探针是超管口径），也不含 limit 之外的收口。')
const sourceOf = (dsId) => {
  const d = datasets.find((x) => x.id === dsId)
  if (!d) throw new Error(`数据集 ${dsId} 不在 bd4-contract.json 里`)
  return d.table ? `\`db_analysis\`.\`${d.table}\`` : `(${d.sqlQuery}) t_source`
}
const BACKTICK = (v) => '`' + String(v).replace(/[^a-zA-Z0-9_]/g, '') + '`'
/**
 * 别名与排序的写法必须和 SqlBuilder 逐字同规则，否则这个探针会替后端说谎。
 * 2026-10-19 就是这么漏的：探针把 SUM(amount) 命名成 `amount`，于是 ORDER BY `amount` 命中别名跑通了；
 * 真实 SQL 命名成 `sum_amount`，ORDER BY `amount` 落回裸列，only_full_group_by 直接 1140 ——
 * 探针 6/4/21 行全绿，屏上三张图空着。（API-109）
 */
const METRIC_ALIAS = (m, func) => {
  const cleaned = String(m.alias || '').replace(/[^a-zA-Z0-9_]/g, '')
  // 与 SqlBuilder.aliasColumn 同式：func.toLowerCase().replace("_","") + "_" + field
  return cleaned || func.toLowerCase().replace(/_/g, '') + '_' + String(m.field).replace(/[^a-zA-Z0-9_]/g, '')
}
q.push('USE db_analysis;')
for (const c of screen911.components) {
  const cfg = c.chartConfig
  if (!cfg || !cfg.datasetId) continue
  const src = sourceOf(cfg.datasetId)
  const aliases = {}
  cfg.measures.forEach((m) => {
    const func = (m.aggregation || 'sum').trim().toUpperCase()
    const key = String(m.field).trim().toLowerCase()
    if (!aliases[key]) aliases[key] = METRIC_ALIAS(m, func)
  })
  const select = [
    ...cfg.dimensions.map((d) => BACKTICK(d.field)),
    ...cfg.measures.map((m) => {
      const func = (m.aggregation || 'sum').trim().toUpperCase()
      return `${func}(${BACKTICK(m.field)}) AS ${BACKTICK(aliases[String(m.field).trim().toLowerCase()])}`
    }),
  ]
  const groupBy = cfg.dimensions.map((d) => BACKTICK(d.field)).join(', ')
  const orderBy = (cfg.sorts || []).map((s) => {
    const key = String(s.field).trim().toLowerCase()
    const col = aliases[key] ? BACKTICK(aliases[key]) : BACKTICK(s.field)
    return `${col} ${s.order === 'desc' ? 'DESC' : 'ASC'}`
  }).join(', ')
  const sql = `SELECT ${select.join(', ')} FROM ${src}`
    + (groupBy ? ` GROUP BY ${groupBy}` : '')
    + (orderBy ? ` ORDER BY ${orderBy}` : '')
    + ' LIMIT 1000'
  q.push(`-- ${c.id} ${c.name}`)
  q.push(`SELECT CONCAT('@@ROWS ~|~ ${c.id} ~|~ ', COUNT(*)) FROM (${sql}) wrapped;`)
  q.push(`SELECT * FROM (${sql}) sample LIMIT 3;`)
}
fs.writeFileSync(OUT_QUERY, q.join('\n') + '\n', 'utf8')

const total = SCREENS.reduce((s, x) => s + x.components.length, 0)
console.log(`已生成 ${path.relative(ROOT, OUT_SQL)}`)
console.log(`  901 静态屏 ${screen901.components.length} 个组件（+ mobile 变体 ${screen901.variants.mobile.components.length} 个）`)
console.log(`  911 直连屏 ${screen911.components.length} 个组件（图表 ${screen911.components.filter((c) => c.chartConfig).length} 个）`)
console.log(`  合计 ${total} 个组件；契约 ${path.relative(ROOT, OUT_JSON)}；回读 ${path.relative(ROOT, OUT_READBACK)}；等价探针 ${path.relative(ROOT, OUT_QUERY)}`)
