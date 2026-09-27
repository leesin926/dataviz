/**
 * 大屏相关类型
 */

import type { AggregationType, ChartConfig } from './chart'
import { ChartType } from './chart'

/** 大屏投放端 */
export type ScreenPlatform = 'pc' | 'mobile' | 'tablet'

/**
 * 组件渲染框架。
 * internal: 走 datasetId OLAP 查询 + chart-engine 生成配置（现有链路）
 * echarts:  PC 端完整原生 ECharts option（chartConfig.options 即完整配置，借鉴 GoView chartFrame 思路）
 * ucharts:  uni 端原生 canvas 图表，仅消费 chartConfig 的语义字段（chartType/dimensions/measures/filters）
 */
export type ScreenChartFrame = 'internal' | 'echarts' | 'ucharts'

/**
 * 组件级数据源配置（借鉴 GoView RequestConfig）。
 * dataset: 缺省，走 datasetId；http: 直接请求外部接口；static: 内联静态数据
 */
export interface ScreenRequestConfig {
  sourceType: 'static' | 'dataset' | 'http'
  staticData?: unknown
  requestUrl?: string
  requestMethod?: 'GET' | 'POST'
  requestParams?: Record<string, unknown>
  requestHeaders?: Record<string, unknown>
  /** 轮询间隔（秒），0/缺省 = 不轮询 */
  interval?: number
}

/** uni 端可原生渲染的图表类型（PC 专属图表暂不开放） */
export const UNI_NATIVE_CHART_TYPES: ChartType[] = [
  ChartType.BAR,
  ChartType.LINE,
  ChartType.PIE,
  ChartType.SCATTER,
  ChartType.RADAR,
  ChartType.GAUGE,
  ChartType.FUNNEL,
]

export interface Screen {
  id: string | number
  name: string
  description?: string
  cover?: string
  width: number
  height: number
  components: ScreenComponent[]
  layers: ScreenLayer[]
  status: 'draft' | 'published' | 'archived'
  config: ScreenConfig
  /** 三端配置变体；某端缺省时回退顶层 width/height/config/components（视为该端的 pc 同源默认） */
  variants?: Partial<Record<ScreenPlatform, ScreenVariant>>
  /**
   * 列表接口专用：端 -> 该端单独配置的画布尺寸（未单独设定则为 null，表示沿用 pc 尺寸）。
   * 列表不带完整 variants，避免把三端组件一并传出去；详情接口仍然只有 variants。
   */
  variantSizes?: Partial<Record<ScreenPlatform, { width: number | null; height: number | null }>>
  isTemplate?: boolean
  /** 分享标识：发布后生成，用于免登录链接 /s/:shareToken */
  shareToken?: string
  createdBy?: string | number
  createdAt?: string
  updatedAt?: string
  publishedAt?: string
}

/** 单端变体：完整自洽的一份大屏配置 */
export interface ScreenVariant {
  width: number
  height: number
  config: ScreenConfig
  components: ScreenComponent[]
  layers?: ScreenLayer[]
}

export interface ScreenComponent {
  id: string
  /** 内置类型取 ScreenComponentType；自定义组件取注册的 definition.type */
  type: ScreenComponentType | (string & {})
  name: string
  x: number
  y: number
  w: number
  h: number
  zIndex: number
  visible: boolean
  locked: boolean
  props: Record<string, unknown>
  chartConfig?: ChartConfig
  chartFrame?: ScreenChartFrame
  request?: ScreenRequestConfig
  /** 组件投放端白名单；缺省 = 全端可用。编辑器在移动/平板页签据此过滤面板 */
  platforms?: ScreenPlatform[]
  animations?: ScreenAnimation[]
  interactions?: ScreenInteraction[]
}

export type ScreenComponentType =
  | 'chart'
  | 'barChart'
  | 'stackBarChart'
  | 'hBarChart'
  | 'lineChart'
  | 'areaChart'
  | 'pieChart'
  | 'donutChart'
  | 'roseChart'
  | 'radarChart'
  | 'gaugeChart'
  | 'treemapChart'
  | 'sunburstChart'
  | 'heatmapChart'
  | 'text'
  | 'marquee'
  | 'countdown'
  | 'image'
  | 'border'
  | 'decoration'
  | 'clock'
  | 'map'
  | 'video'
  | 'iframe'
  | 'table'
  | 'scrollBoard'
  | 'numberFlop'
  | 'statCard'
  | 'progress'
  | 'ranking'
  | 'waterPolo'
  | 'flyline'

export interface ScreenLayer {
  id: string
  name: string
  visible: boolean
  locked: boolean
  componentIds: string[]
}

/** 编辑器组件面板分类 */
export type ScreenComponentCategory = 'chart' | 'text' | 'media' | 'decoration' | 'data' | 'custom'

/**
 * 组件契约（GoView plugin 思路）：所有组件——内置与自定义——都必须注册一份定义。
 * 自定义组件开发 = 实现本接口声明元数据与投放端，再在各端渲染包中
 * 以 registerRenderer(type, impl) 绑定具体渲染器（web: screen-engine；uni: uni-screen-engine）。
 */
export interface ScreenComponentDefinition {
  /** 全局唯一，与 ScreenComponent.type 对应 */
  type: string
  label: string
  category: ScreenComponentCategory
  /** 可投放端；自定义组件按各端渲染器实际实现情况声明 */
  platforms: ScreenPlatform[]
  chartFrame?: ScreenChartFrame
  /** type='chart' 类自定义图表的语义默认图型 */
  defaultChartType?: ChartType
  /** 图表预设：存在即由 ScreenChart 渲染，创建时注入 chartConfig 与演示数据 */
  chartPreset?: ScreenChartPreset
  /** 属性面板「样式」分组的快捷字段（数据驱动） */
  styleFields?: ScreenStyleField[]
  defaultSize: { w: number; h: number }
  defaultProps?: Record<string, unknown>
}

/** 大屏演示数据集（宽表，列/行结构对齐 chart-engine DataAdapter） */
export interface ScreenDemoDataset {
  columns: Array<{ field: string; type: string }>
  rows: Record<string, unknown>[]
}

/** 内置组件通用演示数据（编辑器放置组件即可见效果） */
export const DEFAULT_DEMO_DATASET: ScreenDemoDataset = {
  columns: [
    { field: 'category', type: 'string' },
    { field: 'sales', type: 'number' },
    { field: 'profit', type: 'number' },
  ],
  rows: [
    { category: '一月', sales: 120, profit: 32 },
    { category: '二月', sales: 200, profit: 54 },
    { category: '三月', sales: 150, profit: 40 },
    { category: '四月', sales: 260, profit: 78 },
    { category: '五月', sales: 190, profit: 50 },
    { category: '六月', sales: 310, profit: 96 },
  ],
}

/** 占比类演示数据（饼/环/玫瑰/矩形树） */
export const DEMO_PIE_DATASET: ScreenDemoDataset = {
  columns: [
    { field: 'name', type: 'string' },
    { field: 'value', type: 'number' },
  ],
  rows: [
    { name: '华东', value: 420 },
    { name: '华北', value: 330 },
    { name: '华南', value: 280 },
    { name: '华中', value: 190 },
    { name: '西南', value: 120 },
  ],
}

/** 多维对比演示数据（雷达） */
export const DEMO_RADAR_DATASET: ScreenDemoDataset = {
  columns: [
    { field: 'category', type: 'string' },
    { field: 'sales', type: 'number' },
    { field: 'profit', type: 'number' },
  ],
  rows: [
    { category: '拉新', sales: 86, profit: 64 },
    { category: '留存', sales: 72, profit: 88 },
    { category: '转化', sales: 58, profit: 76 },
    { category: '复购', sales: 91, profit: 55 },
    { category: '口碑', sales: 66, profit: 80 },
  ],
}

/** 单值演示数据（仪表盘） */
export const DEMO_GAUGE_DATASET: ScreenDemoDataset = {
  columns: [
    { field: 'name', type: 'string' },
    { field: 'value', type: 'number' },
  ],
  rows: [{ name: '完成度', value: 72 }],
}

/** 层级演示数据（旭日） */
export const DEMO_TREE_DATASET: ScreenDemoDataset = {
  columns: [
    { field: 'category', type: 'string' },
    { field: 'sub', type: 'string' },
    { field: 'value', type: 'number' },
  ],
  rows: [
    { category: '硬件', sub: '服务器', value: 320 },
    { category: '硬件', sub: '网络设备', value: 210 },
    { category: '软件', sub: '数据平台', value: 260 },
    { category: '软件', sub: '可视化', value: 150 },
    { category: '服务', sub: '实施', value: 130 },
    { category: '服务', sub: '运维', value: 95 },
  ],
}

/** 矩阵演示数据（热力） */
export const DEMO_HEAT_DATASET: ScreenDemoDataset = {
  columns: [
    { field: 'hour', type: 'string' },
    { field: 'day', type: 'string' },
    { field: 'val', type: 'number' },
  ],
  rows: (['00', '04', '08', '12', '16', '20'] as string[]).flatMap((hour, hi) =>
    (['周一', '周二', '周三', '周四'] as string[]).map((day, di) => ({
      hour: `${hour}:00`,
      day,
      val: ((hi * 3 + di * 7 + 5) % 11) * 10 + 10,
    })),
  ),
}

/** 图表语义字段默认模板（配合 DEFAULT_DEMO_DATASET 可渲染全部白名单图型） */
export function defaultChartConfig(chartType: ChartType): ChartConfig {
  return {
    chartType,
    dimensions: [{ field: 'category' }],
    measures: [
      { field: 'sales', aggregation: 'sum' },
      { field: 'profit', aggregation: 'sum' },
    ],
    options: {},
  }
}

/**
 * 图表预设：组件类型一经声明即由 ScreenChart 渲染，
 * 创建时按此注入 chartConfig 与配套演示数据（放置即可见）。
 */
export interface ScreenChartPreset {
  chartType: ChartType
  /** 维度字段，缺省 ['category'] */
  dimensions?: string[]
  /** 度量字段，缺省 ['sales','profit'] */
  measures?: string[]
  /** 语义开关（stack/horizontal/donut/rose/area/max…），落到 chartConfig.options */
  options?: Record<string, unknown>
  /** 演示数据，缺省 DEFAULT_DEMO_DATASET */
  data?: ScreenDemoDataset
}

/** 属性面板快捷字段类型 */
export type ScreenStyleFieldKind = 'text' | 'number' | 'color' | 'switch' | 'select'

/**
 * 组件样式的快捷编辑字段（数据驱动）。
 * 新组件只需在定义里声明，属性面板「样式」分组自动生成表单，无需手写模板。
 */
export interface ScreenStyleField {
  /** props 内的键 */
  key: string
  /** i18n 文案键（screen.editor.f_*） */
  labelKey: string
  kind: ScreenStyleFieldKind
  min?: number
  max?: number
  step?: number
  /** select 候选值，取语言中立枚举 token 直接展示 */
  options?: string[]
}

/** 地图演示数据：省份排行 */
export const DEMO_MAP_PROVINCES: Array<{ name: string; value: number }> = [
  { name: '广东', value: 98 },
  { name: '江苏', value: 88 },
  { name: '浙江', value: 79 },
  { name: '山东', value: 66 },
  { name: '北京', value: 58 },
  { name: '上海', value: 52 },
  { name: '四川', value: 41 },
  { name: '湖北', value: 35 },
]

/** 地图演示打点（含坐标，供水球/飞线复用） */
export const DEMO_MAP_POINTS: Array<{ name: string; lon: number; lat: number; value: number }> = [
  { name: '北京', lon: 116.4, lat: 39.9, value: 58 },
  { name: '上海', lon: 121.47, lat: 31.23, value: 52 },
  { name: '广州', lon: 113.28, lat: 23.13, value: 98 },
  { name: '深圳', lon: 114.06, lat: 22.55, value: 92 },
  { name: '成都', lon: 104.06, lat: 30.67, value: 41 },
  { name: '武汉', lon: 114.31, lat: 30.52, value: 35 },
]

/** 飞线演示路径（[起点, 终点] 取 DEMO_MAP_POINTS 名称） */
export const DEMO_FLY_ROUTES: Array<{ from: string; to: string; value: number }> = [
  { from: '北京', to: '广州', value: 86 },
  { from: '北京', to: '上海', value: 74 },
  { from: '广州', to: '成都', value: 52 },
  { from: '上海', to: '武汉', value: 43 },
  { from: '深圳', to: '北京', value: 92 },
]

/** 公共地理数据地址（阿里云 DataV 可视化空间公开 GeoJSON） */
export const DEFAULT_GEO_URL = 'https://geo.datav.aliyun.com/areas_v3/bound/100000_full.json'

/** 画布底色固定白色：设计端不再开放取色，视觉底由上传的背景图承载（设计端与渲染端共用） */
export const CANVAS_BG_COLOR = '#ffffff'

/** 颜色亮度（0~255）；解析不了的颜色（transparent、CSS 变量、命名色）返回 null */
export function colorLuma(color?: string): number | null {
  if (!color) return null
  const value = color.trim()
  const m = /^#?([0-9a-f]{3}|[0-9a-f]{6})$/i.exec(value)
  if (m) {
    const hex = m[1].length === 3 ? m[1].split('').map((c) => c + c).join('') : m[1]
    const r = parseInt(hex.slice(0, 2), 16)
    const g = parseInt(hex.slice(2, 4), 16)
    const b = parseInt(hex.slice(4, 6), 16)
    return 0.299 * r + 0.587 * g + 0.114 * b
  }
  const rgb = /rgba?\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)/i.exec(value)
  if (rgb) {
    return 0.299 * +rgb[1] + 0.587 * +rgb[2] + 0.114 * +rgb[3]
  }
  return null
}

/** 颜色明暗判定（支持 #rgb / #rrggbb / rgb() / rgba()），失败时按浅色处理 */
export function isDarkColor(color?: string): boolean {
  const luma = colorLuma(color)
  return luma === null ? false : luma < 150
}

/** 与画布底色亮度差小于该值即视为撞色（浅底浅字看不见，深底深字同理） */
const CANVAS_CONTRAST_MIN = 90
const CANVAS_BASE_LUMA = colorLuma(CANVAS_BG_COLOR) ?? 255

/** 该颜色是否与画布底色撞色；解析不了的颜色不算撞色，保持原值 */
export function clashesWithCanvas(color: unknown): boolean {
  if (typeof color !== 'string') return false
  const luma = colorLuma(color)
  if (luma === null) return false
  return Math.abs(luma - CANVAS_BASE_LUMA) < CANVAS_CONTRAST_MIN
}

/**
 * 画布墨色：组件默认前景一律与画布底色取反差（白底深字、深底浅字）。
 * 组件 defaultProps、渲染器兜底与图表主题共用这一组常量，避免三处各写一套深浅色。
 */
const inkDark = isDarkColor(CANVAS_BG_COLOR)
export const CANVAS_INK = {
  /** 标题、数值等强前景 */
  strong: inkDark ? '#ffffff' : '#16202f',
  /** 正文 */
  normal: inkDark ? '#e0e6ed' : '#263243',
  /** 次要文字、轴标签 */
  muted: inkDark ? '#cfd8e3' : '#5b6a80',
  /** 分割线 / 网格线 */
  line: inkDark ? '#2a4a6a' : '#dfe5ef',
  /** 轨道、斑马纹等弱填充 */
  faint: inkDark ? 'rgba(255,255,255,0.08)' : 'rgba(22,32,47,0.06)',
  /** 强调色（青在深底上更亮，白底改用品牌蓝保证可读） */
  accent: inkDark ? '#3fdaff' : '#2f7cff',
  /** 警示/倒计时类暖色：浅底上必须压暗，否则黄字白底不可读 */
  warm: inkDark ? '#ffb547' : '#b7791f',
  /** 强调色底上的文字（品牌色块是深色，配白字；深底强调块配深色字） */
  onAccent: inkDark ? '#0a1929' : '#ffffff',
  /** 涨跌/成败语义色 */
  ok: inkDark ? '#4dd6a8' : '#0f8a5f',
  danger: inkDark ? '#ff6b6b' : '#d4380d',
  /** 气泡/浮层：浅底用白卡深字，深底用深蓝卡浅字 */
  surface: inkDark ? 'rgba(10,25,41,0.96)' : 'rgba(255,255,255,0.97)',
} as const

/** 图表系列配色：浅底用高饱和深色系，深底用高亮霓虹系，保证系列色都不与画布底色糊在一起 */
export const CHART_PALETTE: readonly string[] = inkDark
  ? ['#3aa2ff', '#4dd6a8', '#ffb547', '#ff6b6b', '#9d7cff', '#3ad3e6', '#ff9f43', '#e84393', '#00cec9']
  : ['#2f7cff', '#0f8a5f', '#b7791f', '#d4380d', '#7a5af8', '#0f8f9d', '#e8590c', '#c2255c', '#5c7cfa']

/** 组件颜色类属性 → 撞色时应换成的墨色（键是 props 字段名，值语义按前景/强调/弱填充分档） */
const INK_PROP_TARGET: Record<string, string> = {
  color: CANVAS_INK.normal,
  textColor: CANVAS_INK.normal,
  titleColor: CANVAS_INK.strong,
  valueColor: CANVAS_INK.strong,
  unitColor: CANVAS_INK.muted,
  labelColor: CANVAS_INK.normal,
  barColor: CANVAS_INK.accent,
  accent: CANVAS_INK.accent,
  lineColor: CANVAS_INK.accent,
  borderColor: CANVAS_INK.accent,
  activeColor: CANVAS_INK.accent,
  colorSecond: CANVAS_INK.muted,
  bgColor: CANVAS_INK.faint,
  boxColor: CANVAS_INK.faint,
  trackColor: CANVAS_INK.faint,
}

/**
 * 历史大屏存的是“深色底 + 浅色字”，画布锁白后这些浅色前景就成了白底白字（整块看着是空的）。
 * 读取时把与底色撞色的前景属性归一到画布墨色，transparent / CSS 变量等解析不了的值原样保留。
 */
export function normalizeScreenInk<T extends { components?: ScreenComponent[] }>(screen: T): T {
  for (const comp of screen?.components || []) {
    const pr = comp.props
    for (const key of Object.keys(INK_PROP_TARGET)) {
      if (clashesWithCanvas(pr[key])) pr[key] = INK_PROP_TARGET[key]
    }
  }
  return screen
}

/** 图片组件内置演示图（离线 data-uri，放置即可见） */
export const DEMO_IMAGE_DATA_URI =
  'data:image/svg+xml;charset=utf-8,' +
  encodeURIComponent(
    `<svg xmlns="http://www.w3.org/2000/svg" width="300" height="200" viewBox="0 0 300 200"><defs><linearGradient id="g" x1="0" y1="0" x2="1" y2="1"><stop offset="0%" stop-color="#12325a"/><stop offset="100%" stop-color="#0a1929"/></linearGradient></defs><rect width="300" height="200" fill="url(#g)"/><g fill="none" stroke="#3fdaff" stroke-opacity=".55"><path d="M0 150 L60 110 L110 130 L170 78 L230 100 L300 46"/><path d="M0 172 L60 146 L110 158 L170 126 L230 140 L300 96"/></g><g fill="#3fdaff"><circle cx="60" cy="110" r="3"/><circle cx="170" cy="78" r="3"/><circle cx="230" cy="100" r="3"/></g><text x="16" y="30" fill="#cfd8e3" font-family="sans-serif" font-size="14">DEMO IMAGE</text></svg>`,
  )

const DEMO_TABLE_PROPS = {
  columns: ['项目', '销量', '利润'],
  rows: [
    ['产品A', 120, 32],
    ['产品B', 200, 54],
    ['产品C', 150, 40],
    ['产品D', 260, 78],
  ],
}

const PC_ONLY: ScreenPlatform[] = ['pc']
const ALL_ENDS: ScreenPlatform[] = ['pc', 'mobile', 'tablet']

/** 通用样式字段（多数组件复用） */
const SF = {
  color: { key: 'color', labelKey: 'screen.editor.fColor', kind: 'color' } as ScreenStyleField,
  fontSize: { key: 'fontSize', labelKey: 'screen.editor.fFontSize', kind: 'number', min: 8, max: 200 } as ScreenStyleField,
  bgColor: { key: 'bgColor', labelKey: 'screen.editor.fBgColor', kind: 'color' } as ScreenStyleField,
  text: { key: 'text', labelKey: 'screen.editor.fText', kind: 'text' } as ScreenStyleField,
}

/**
 * 内置组件定义（map/flyline/waterPolo/video/iframe 与 treemap/sunburst/heatmap 等 PC 专属，见决策 D6）
 * 图表预设 = 一键投放的语义化图表（都走 ScreenChart，属性面板仍可改图表类型/字段/数据）
 */
export const BUILTIN_COMPONENT_DEFINITIONS: ScreenComponentDefinition[] = [
  // —— 图表（通用 + 预设） ——
  {
    type: 'chart',
    label: '图表',
    category: 'chart',
    platforms: ALL_ENDS,
    defaultSize: { w: 400, h: 300 },
    chartPreset: { chartType: ChartType.BAR },
  },
  { type: 'barChart', label: '柱状图', category: 'chart', platforms: ALL_ENDS, defaultSize: { w: 400, h: 300 }, chartPreset: { chartType: ChartType.BAR } },
  { type: 'stackBarChart', label: '堆叠柱状图', category: 'chart', platforms: ALL_ENDS, defaultSize: { w: 400, h: 300 }, chartPreset: { chartType: ChartType.BAR, options: { stack: true } } },
  { type: 'hBarChart', label: '横向条形图', category: 'chart', platforms: ALL_ENDS, defaultSize: { w: 400, h: 300 }, chartPreset: { chartType: ChartType.BAR, options: { horizontal: true } } },
  { type: 'lineChart', label: '折线图', category: 'chart', platforms: ALL_ENDS, defaultSize: { w: 400, h: 300 }, chartPreset: { chartType: ChartType.LINE } },
  { type: 'areaChart', label: '面积图', category: 'chart', platforms: ALL_ENDS, defaultSize: { w: 400, h: 300 }, chartPreset: { chartType: ChartType.LINE, options: { area: true } } },
  { type: 'pieChart', label: '饼图', category: 'chart', platforms: ALL_ENDS, defaultSize: { w: 360, h: 300 }, chartPreset: { chartType: ChartType.PIE, dimensions: ['name'], measures: ['value'], data: DEMO_PIE_DATASET } },
  { type: 'donutChart', label: '环形图', category: 'chart', platforms: ALL_ENDS, defaultSize: { w: 360, h: 300 }, chartPreset: { chartType: ChartType.PIE, dimensions: ['name'], measures: ['value'], options: { donut: true }, data: DEMO_PIE_DATASET } },
  { type: 'roseChart', label: '玫瑰图', category: 'chart', platforms: ALL_ENDS, defaultSize: { w: 360, h: 300 }, chartPreset: { chartType: ChartType.PIE, dimensions: ['name'], measures: ['value'], options: { rose: true }, data: DEMO_PIE_DATASET } },
  { type: 'radarChart', label: '雷达图', category: 'chart', platforms: ALL_ENDS, defaultSize: { w: 360, h: 300 }, chartPreset: { chartType: ChartType.RADAR, data: DEMO_RADAR_DATASET } },
  { type: 'gaugeChart', label: '仪表盘', category: 'chart', platforms: ALL_ENDS, defaultSize: { w: 300, h: 260 }, chartPreset: { chartType: ChartType.GAUGE, dimensions: ['name'], measures: ['value'], options: { max: 100 }, data: DEMO_GAUGE_DATASET } },
  { type: 'treemapChart', label: '矩形树图', category: 'chart', platforms: PC_ONLY, defaultSize: { w: 400, h: 300 }, chartPreset: { chartType: ChartType.TREEMAP, dimensions: ['name'], measures: ['value'], data: DEMO_PIE_DATASET } },
  { type: 'sunburstChart', label: '旭日图', category: 'chart', platforms: PC_ONLY, defaultSize: { w: 400, h: 360 }, chartPreset: { chartType: ChartType.SUNBURST, dimensions: ['category', 'sub'], measures: ['value'], data: DEMO_TREE_DATASET } },
  { type: 'heatmapChart', label: '热力图', category: 'chart', platforms: PC_ONLY, defaultSize: { w: 460, h: 320 }, chartPreset: { chartType: ChartType.HEATMAP, dimensions: ['hour'], measures: ['hour', 'day', 'val'], data: DEMO_HEAT_DATASET } },

  // —— 文本 ——
  {
    type: 'text',
    label: '文本',
    category: 'text',
    platforms: ALL_ENDS,
    defaultSize: { w: 200, h: 40 },
    defaultProps: { text: '数据标题', fontSize: 18, color: CANVAS_INK.strong },
    styleFields: [SF.text, SF.color, SF.fontSize],
  },
  { type: 'marquee', label: '跑马灯', category: 'text', platforms: PC_ONLY, defaultSize: { w: 460, h: 40 },
    defaultProps: { texts: ['系统运行正常', '数据同步延迟 < 1s', '今日新增设备 1,284 台', '大屏渲染链路健康'], speed: 24, color: CANVAS_INK.accent, fontSize: 16 },
    styleFields: [SF.color, SF.fontSize, { key: 'speed', labelKey: 'screen.editor.fSpeed', kind: 'number', min: 5, max: 120 }] },
  { type: 'countdown', label: '倒计时', category: 'text', platforms: PC_ONLY, defaultSize: { w: 320, h: 70 },
    defaultProps: { label: '距活动结束还剩', seconds: 93600, color: CANVAS_INK.warm, boxColor: CANVAS_INK.faint },
    styleFields: [SF.color, { key: 'seconds', labelKey: 'screen.editor.fSeconds', kind: 'number', min: 1, max: 999999, step: 60 }] },
  { type: 'clock', label: '时钟', category: 'text', platforms: ALL_ENDS, defaultSize: { w: 200, h: 60 },
    defaultProps: { fontSize: 32, color: CANVAS_INK.normal, dateFormat: 'YYYY-MM-DD' },
    styleFields: [SF.color, SF.fontSize] },

  // —— 数据 ——
  { type: 'table', label: '数据表格', category: 'data', platforms: ALL_ENDS, defaultSize: { w: 500, h: 300 }, defaultProps: { ...DEMO_TABLE_PROPS, fontSize: 14, color: CANVAS_INK.normal },
    styleFields: [SF.color, SF.fontSize] },
  { type: 'scrollBoard', label: '轮播列表', category: 'data', platforms: ALL_ENDS, defaultSize: { w: 400, h: 200 }, defaultProps: { ...DEMO_TABLE_PROPS, interval: 3, fontSize: 14, color: CANVAS_INK.normal },
    styleFields: [SF.color, SF.fontSize, { key: 'interval', labelKey: 'screen.editor.fInterval', kind: 'number', min: 1, max: 20 }] },
  { type: 'numberFlop', label: '数字翻牌器', category: 'data', platforms: PC_ONLY, defaultSize: { w: 280, h: 80 },
    defaultProps: { value: 102486, unit: '台', color: CANVAS_INK.accent, bgColor: CANVAS_INK.faint, fontSize: 30, group: true },
    styleFields: [{ key: 'value', labelKey: 'screen.editor.fValue', kind: 'number', min: 0, max: 999999999 }, { key: 'unit', labelKey: 'screen.editor.fUnit', kind: 'text' }, SF.color, { key: 'fontSize', labelKey: 'screen.editor.fFontSize', kind: 'number', min: 12, max: 120 }] },
  { type: 'statCard', label: '指标卡', category: 'data', platforms: PC_ONLY, defaultSize: { w: 260, h: 120 },
    defaultProps: { title: '今日销售额', value: 1284560, unit: '元', trend: 12.6, color: CANVAS_INK.strong, accent: CANVAS_INK.accent, bgColor: CANVAS_INK.faint },
    styleFields: [{ key: 'title', labelKey: 'screen.editor.fTitle', kind: 'text' }, { key: 'value', labelKey: 'screen.editor.fValue', kind: 'number', min: 0, max: 999999999 }, { key: 'unit', labelKey: 'screen.editor.fUnit', kind: 'text' }, { key: 'trend', labelKey: 'screen.editor.fTrend', kind: 'number', min: -999, max: 999, step: 0.1 }, SF.color, { key: 'accent', labelKey: 'screen.editor.fAccent', kind: 'color' }] },
  { type: 'progress', label: '进度条', category: 'data', platforms: PC_ONLY, defaultSize: { w: 420, h: 60 },
    defaultProps: { label: '任务完成率', percent: 68, color: CANVAS_INK.accent, trackColor: CANVAS_INK.faint, height: 12, showValue: true },
    styleFields: [{ key: 'label', labelKey: 'screen.editor.fLabel', kind: 'text' }, { key: 'percent', labelKey: 'screen.editor.fPercent', kind: 'number', min: 0, max: 100 }, SF.color, { key: 'height', labelKey: 'screen.editor.fBarHeight', kind: 'number', min: 4, max: 60 }] },
  { type: 'ranking', label: '排名列表', category: 'data', platforms: PC_ONLY, defaultSize: { w: 400, h: 260 },
    defaultProps: {
      items: [
        { name: '华东区', value: 420 },
        { name: '华南区', value: 330 },
        { name: '华北区', value: 286 },
        { name: '西南区', value: 190 },
        { name: '东北区', value: 128 },
      ],
      color: CANVAS_INK.normal,
      barColor: CANVAS_INK.accent,
      showValue: true,
    },
    styleFields: [SF.color, { key: 'barColor', labelKey: 'screen.editor.fAccent', kind: 'color' }] },

  // —— 装饰 ——
  { type: 'border', label: '边框', category: 'decoration', platforms: ALL_ENDS, defaultSize: { w: 400, h: 300 },
    defaultProps: { variant: 'glow', borderColor: CANVAS_INK.accent, borderWidth: 2, radius: 8 },
    styleFields: [{ key: 'variant', labelKey: 'screen.editor.fVariant', kind: 'select', options: ['line', 'glow', 'corner', 'dash'] }, { key: 'borderColor', labelKey: 'screen.editor.fBorderColor', kind: 'color' }, { key: 'borderWidth', labelKey: 'screen.editor.fBorderWidth', kind: 'number', min: 1, max: 12 }, { key: 'radius', labelKey: 'screen.editor.fRadius', kind: 'number', min: 0, max: 40 }] },
  { type: 'decoration', label: '装饰', category: 'decoration', platforms: PC_ONLY, defaultSize: { w: 300, h: 24 },
    defaultProps: { variant: 'flow', color: CANVAS_INK.accent, colorSecond: CANVAS_INK.muted },
    styleFields: [{ key: 'variant', labelKey: 'screen.editor.fVariant', kind: 'select', options: ['flow', 'bars', 'corner', 'divider'] }, SF.color, { key: 'colorSecond', labelKey: 'screen.editor.fColorSecond', kind: 'color' }] },

  // —— 媒体 / 地图（PC 专属） ——
  { type: 'image', label: '图片', category: 'media', platforms: ALL_ENDS, defaultSize: { w: 300, h: 200 },
    defaultProps: { src: DEMO_IMAGE_DATA_URI, alt: '图片', objectFit: 'cover' },
    styleFields: [{ key: 'src', labelKey: 'screen.editor.fImageUrl', kind: 'text' }] },
  { type: 'video', label: '视频', category: 'media', platforms: PC_ONLY, defaultSize: { w: 640, h: 360 },
    defaultProps: { src: '', autoplay: true, muted: true, loop: true },
    styleFields: [{ key: 'src', labelKey: 'screen.editor.fVideoUrl', kind: 'text' }] },
  { type: 'iframe', label: '网页嵌入', category: 'media', platforms: PC_ONLY, defaultSize: { w: 600, h: 400 },
    defaultProps: { src: '' },
    styleFields: [{ key: 'src', labelKey: 'screen.editor.fFrameUrl', kind: 'text' }] },
  { type: 'map', label: '地图', category: 'chart', platforms: PC_ONLY, defaultSize: { w: 600, h: 400 },
    defaultProps: { mapName: 'china', geoUrl: DEFAULT_GEO_URL, color: '#1a3a5f', borderColor: CANVAS_INK.accent, activeColor: CANVAS_INK.accent, data: DEMO_MAP_PROVINCES },
    styleFields: [{ key: 'geoUrl', labelKey: 'screen.editor.fGeoUrl', kind: 'text' }, { key: 'color', labelKey: 'screen.editor.fMapColor', kind: 'color' }, { key: 'borderColor', labelKey: 'screen.editor.fBorderColor', kind: 'color' }] },
  { type: 'flyline', label: '飞线地图', category: 'chart', platforms: PC_ONLY, defaultSize: { w: 600, h: 400 },
    defaultProps: { mapName: 'china', geoUrl: DEFAULT_GEO_URL, color: '#123152', borderColor: '#2a6fa8', lineColor: CANVAS_INK.accent, points: DEMO_MAP_POINTS, routes: DEMO_FLY_ROUTES },
    styleFields: [{ key: 'geoUrl', labelKey: 'screen.editor.fGeoUrl', kind: 'text' }, { key: 'lineColor', labelKey: 'screen.editor.fLineColor', kind: 'color' }] },
  { type: 'waterPolo', label: '水球图', category: 'chart', platforms: PC_ONLY, defaultSize: { w: 200, h: 200 },
    defaultProps: { percent: 66, color: CANVAS_INK.accent, bgColor: CANVAS_INK.faint, textColor: CANVAS_INK.normal, label: '水位' },
    styleFields: [{ key: 'percent', labelKey: 'screen.editor.fPercent', kind: 'number', min: 0, max: 100 }, SF.color, { key: 'label', labelKey: 'screen.editor.fLabel', kind: 'text' }] },
]

const definitionRegistry = new Map<string, ScreenComponentDefinition>()

export function registerComponentDefinition(def: ScreenComponentDefinition): void {
  if (definitionRegistry.has(def.type)) {
    throw new Error(`组件定义已存在: ${def.type}，请换用其他 type 或先注销`)
  }
  definitionRegistry.set(def.type, def)
}

export function getComponentDefinition(type: string): ScreenComponentDefinition | undefined {
  return definitionRegistry.get(type)
}

/** 列出组件定义；传 platform 时只返回该端可投放的组件（编辑器组件面板数据源） */
export function listComponentDefinitions(platform?: ScreenPlatform): ScreenComponentDefinition[] {
  const all = Array.from(definitionRegistry.values())
  return platform ? all.filter((d) => d.platforms.includes(platform)) : all
}

BUILTIN_COMPONENT_DEFINITIONS.forEach(registerComponentDefinition)

/** 取类型的图表预设（自定义组件声明后同样由 ScreenChart 渲染） */
export function chartPresetOf(type: string): ScreenChartPreset | undefined {
  return definitionRegistry.get(type)?.chartPreset
}

/** 是否图表预设类组件（编辑器与展示端共用渲染判定，避免两处硬编码类型表） */
export function isChartPreset(type: string): boolean {
  return !!chartPresetOf(type)
}

/** 组件属性面板「样式」分组的快捷字段 */
export function styleFieldsOf(type: string): ScreenStyleField[] {
  return definitionRegistry.get(type)?.styleFields ?? []
}

/** 组件创建默认 props（含图表预设的 chartConfig / 静态演示数据所需的元信息） */
export function defaultComponentProps(type: string): Record<string, unknown> {
  const def = definitionRegistry.get(type)
  return def?.defaultProps ? (JSON.parse(JSON.stringify(def.defaultProps)) as Record<string, unknown>) : {}
}

/** 由预设构建语义 chartConfig */
export function chartConfigFromPreset(preset: ScreenChartPreset): ChartConfig {
  const dims = preset.dimensions ?? ['category']
  const measures = preset.measures ?? ['sales', 'profit']
  return {
    chartType: preset.chartType,
    dimensions: dims.map((field) => ({ field })),
    measures: measures.map((field) => ({ field, aggregation: 'sum' as AggregationType })),
    options: { ...(preset.options ?? {}) },
  }
}

/** 预设配套演示数据（图表放置即可见） */
export function presetDemoData(preset: ScreenChartPreset): ScreenDemoDataset {
  return JSON.parse(JSON.stringify(preset.data ?? DEFAULT_DEMO_DATASET)) as ScreenDemoDataset
}

/** 组件是否可在 uni 端（mobile/tablet）原生渲染 */
export function isUniNativeRenderable(component: ScreenComponent): boolean {
  return isRenderableOnPlatform(component, 'mobile')
}

/** 组件是否可在指定端渲染（编辑器白名单过滤 + 渲染器跳过不可渲染组件共用） */
export function isRenderableOnPlatform(component: ScreenComponent, platform: ScreenPlatform): boolean {
  if (component.platforms && !component.platforms.includes(platform)) return false
  const def = definitionRegistry.get(component.type)
  if (def && !def.platforms.includes(platform)) return false
  if (platform === 'pc') return true
  const preset = definitionRegistry.get(component.type)?.chartPreset
  if (component.type === 'chart' || preset) {
    if (component.chartFrame === 'echarts') return false
    const chartType = component.chartConfig?.chartType ?? preset?.chartType
    return !!chartType && UNI_NATIVE_CHART_TYPES.includes(chartType)
  }
  // uni 端只渲染已注册且声明支持该端的组件
  return !!def
}

export interface ScreenConfig {
  backgroundColor?: string
  backgroundImage?: string
  backgroundRepeat?: 'no-repeat' | 'repeat' | 'repeat-x' | 'repeat-y'
  backgroundSize?: 'cover' | 'contain' | string
  /** 哀悼模式：展示端整屏灰度 */
  /** @deprecated 旧版单屏灰度开关，已废弃（哀悼模式改为管理端全局配置驱动），保留仅为兼容历史数据 */
  grayMode?: boolean
  adaptationMode: 'scale' | 'fixed-width' | 'responsive'
  gridSize?: number
  globalRefreshInterval?: number
}

export interface ScreenAnimation {
  type: 'fade' | 'slide' | 'scale' | 'rotate' | 'bounce' | 'flash'
  duration: number // ms
  delay: number // ms
  iteration?: number | 'infinite'
  easing?: string
}

export interface ScreenInteraction {
  type: 'linkage' | 'drill' | 'link' | 'refresh' | 'fullScreen'
  sourceId: string
  targetIds: string[]
  payload?: Record<string, unknown>
}
