/**
 * chartConfig + 宽表数据 → uCharts 入参（4.3 适配层）。
 *
 * uCharts 不消费 ECharts option，只吃 `{categories, series, extra}` 这一套结构化入参，
 * 所以 uni 端只支持语义字段可无损翻译的图表（白名单见 shared-types `UNI_NATIVE_CHART_TYPES`）。
 * PC 端 `options` 里的 ECharts 片段在这里**刻意被忽略**：能翻成 uCharts 语义的就翻，
 * 翻不了的不假装支持。
 */

import type { ChartConfig } from '@dataviz/shared-types'
import { ChartType, CHART_PALETTE } from '@dataviz/shared-types'

export interface ChartRow {
  [field: string]: unknown
}

export interface ChartColumnDef {
  field: string
  type?: string
}

/** 与 shared-types ScreenDemoDataset 同形，本地声明避免包耦合 */
export interface ChartData {
  columns?: ChartColumnDef[]
  rows?: ChartRow[]
}

export interface UChartsSeries {
  name: string
  /** 轴类是数值数组；占比类是 {name,value}[]；仪表盘是单个数值 */
  data: unknown
}

export interface UChartsPayload {
  /** uCharts 的 type 取值 */
  type: string
  /** 轴类是字符串维度；仪表盘是色段对象数组（`{value:0~1 累加阈值, color}`） */
  categories: unknown[]
  series: UChartsSeries[]
  extra: Record<string, unknown>
  color: string[]
  title: string
  legendShow: boolean
}

const UCHARTS_TYPE: Partial<Record<ChartType, string>> = {
  // uCharts 里 column=竖向柱状图、bar=横向条形图，与 ECharts 的叫法相反
  [ChartType.BAR]: 'column',
  [ChartType.LINE]: 'line',
  [ChartType.PIE]: 'pie',
  [ChartType.SCATTER]: 'scatter',
  [ChartType.RADAR]: 'radar',
  [ChartType.GAUGE]: 'gauge',
  [ChartType.FUNNEL]: 'funnel',
}

function numOf(value: unknown): number {
  const n = typeof value === 'number' ? value : Number(String(value ?? '').replace(/[,\s]/g, ''))
  return Number.isFinite(n) ? n : 0
}

function boolOpt(options: Record<string, unknown> | undefined, key: string): boolean {
  return options?.[key] === true
}

/** 维度字段：配置优先，否则取第一个非数值列 */
function dimensionField(cfg: ChartConfig | undefined, data: ChartData): string {
  const fromCfg = cfg?.dimensions?.[0]?.field
  if (fromCfg) return fromCfg
  const first = data.rows?.[0]
  if (!first) return ''
  const typed = (data.columns || []).find((c) => c.type && c.type !== 'number')
  if (typed) return typed.field
  const numericFields = new Set((cfg?.measures || []).map((m) => m.field))
  const keys = Object.keys(first)
  const textLike = keys.find((k) => !numericFields.has(k) && typeof first[k] === 'string')
  return textLike || keys.find((k) => !numericFields.has(k)) || keys[0] || ''
}

/** 度量字段：配置优先，否则取其余全部数值列 */
function measureFields(cfg: ChartConfig | undefined, data: ChartData, dim: string): Array<{ field: string; name: string }> {
  const fromCfg = (cfg?.measures || [])
    .filter((m) => m.show !== false)
    .map((m) => ({ field: m.field, name: m.alias || m.field }))
  if (fromCfg.length) return fromCfg
  const first = data.rows?.[0]
  if (!first) return dim ? [] : []
  return Object.keys(first)
    .filter((k) => k !== dim && typeof first[k] === 'number')
    .map((k) => ({ field: k, name: k }))
}

function aliasOf(cfg: ChartConfig | undefined, field: string): string {
  return cfg?.measures?.find((m) => m.field === field)?.alias || field
}

/** 单值图表（仪表盘）取第一行第一个度量 */
function singleValue(rows: ChartRow[], field: string): number {
  if (!rows.length) return 0
  return numOf(rows[0][field])
}

export function toUCharts(cfg: ChartConfig | undefined, data: ChartData): UChartsPayload {
  const chartType = cfg?.chartType || ChartType.BAR
  const type = UCHARTS_TYPE[chartType] || 'bar'
  const rows = Array.isArray(data.rows) ? data.rows : []
  const options = cfg?.options
  const dim = dimensionField(cfg, data)
  const measures = measureFields(cfg, data, dim)
  const color = cfg?.theme === 'dark' ? [...CHART_PALETTE].reverse() : [...CHART_PALETTE]

  // 占比/单值类：一个度量、按维度切成 {name, value}
  const isPieLike = type === 'pie' || type === 'ring' || type === 'rose' || type === 'funnel'
  if (isPieLike || chartType === ChartType.RADAR || chartType === ChartType.GAUGE) {
    const valueField = measures[0]?.field || dim
    const items = rows.map((r) => ({ name: String(r[dim] ?? ''), value: numOf(r[valueField]) }))
    if (chartType === ChartType.GAUGE) {
      const maxRaw = Number(options?.max)
      const maxCount = Number.isFinite(maxRaw) && maxRaw > 0 ? maxRaw : 100
      const ratio = Math.max(0, Math.min(1, singleValue(rows, valueField) / maxCount))
      // uCharts 仪表盘：series.data 是 **0~1 比例**（内部按角度算），categories 是同样量纲的
      // 累加色段阈值（决定指针配色），splitLine 必须整段给全 —— 它是浅合并，漏字段会变 NaN
      return {
        type: 'gauge',
        categories: [
          { name: '', value: 1 / 3, color: color[1] || color[0] },
          { name: '', value: 2 / 3, color: color[2] || color[0] },
          { name: '', value: 1, color: color[3] || color[0] },
        ],
        series: [{ name: measures[0]?.name || valueField, data: ratio }],
        extra: {
          gauge: {
            type: 'default',
            startAngle: 0.75,
            endAngle: 0.25,
            width: 12,
            labelOffset: 13,
            labelColor: '#666666',
            startNumber: 0,
            endNumber: maxCount,
            splitLine: { fixRadius: 0, splitNumber: 5, width: 12, color: '#FFFFFF', childNumber: 5, childWidth: 4 },
            pointer: { width: 12, color: 'auto' },
          },
        },
        color,
        title: cfg?.title || '',
        legendShow: false,
      }
    }
    if (chartType === ChartType.RADAR) {
      const maxValue = items.reduce((m, i) => Math.max(m, i.value), 0) || 1
      return {
        type: 'radar',
        categories: items.map((i) => i.name),
        series: [{ name: measures[0]?.name || valueField, data: items.map((i) => i.value) }],
        extra: { radar: { gridColor: '#e0e0e0', maxMax: Math.ceil(maxValue * 1.2) } },
        color,
        title: cfg?.title || '',
        legendShow: measures.length > 1,
      }
    }
    const pieType = boolOpt(options, 'donut') ? 'ring' : boolOpt(options, 'rose') ? 'rose' : 'pie'
    return {
      type: pieType,
      categories: items.map((i) => i.name),
      series: [{ name: measures[0]?.name || valueField, data: items }],
      extra:
        pieType === 'ring'
          ? { ring: { labelShow: true, lineWidth: 18, activeOpacity: 0.5, activeRadius: 10, offsetAngle: 0, border: false, customColor: color } }
          : pieType === 'rose'
            ? { rose: { customColor: color } }
            : { pie: { labelShow: true, activeOpacity: 0.5, activeRadius: 10, offsetAngle: 0, labelWidth: 15, border: false, customColor: color } },
      color,
      title: cfg?.title || '',
      legendShow: true,
    }
  }

  // 轴类：维度当 categories，每个度量一条 series
  const categories = rows.map((r) => String(r[dim] ?? ''))
  const fields = measures.length ? measures : [{ field: dim, name: dim }]
  const series: UChartsSeries[] = fields.map((m) => ({
    name: m.name || aliasOf(cfg, m.field),
    data: rows.map((r) => numOf(r[m.field])),
  }))

  const extra: Record<string, unknown> = {}
  const horizontal = chartType === ChartType.BAR && boolOpt(options, 'horizontal')
  if (chartType === ChartType.BAR) {
    const stack = boolOpt(options, 'stack')
    const barStyle = { type: stack ? 'stack' : 'group', width: 18, seriesGap: 2, categoryGap: 2, activeBgColor: '#F7F7F7' }
    // 柱/条两个类型的 extra key 不同，而且 uCharts 会**无条件**读 `opts.extra[type]` 的
    // seriesGap/categoryGap —— 少给这一整段对象就是 NaN 或直接抛错，整块画布空白
    extra[horizontal ? 'bar' : 'column'] = barStyle
  }
  if (chartType === ChartType.LINE) {
    const smooth = options?.smooth !== false
    if (boolOpt(options, 'area')) {
      extra.area = { addLine: true, type: 'curve', smooth, linearType: 'opacity', activeWidth: 0 }
    } else {
      extra.line = { type: 'straight', width: 2, activeWidth: 0 }
    }
  }
  if (chartType === ChartType.SCATTER) {
    extra.scatter = { borderWidth: 2, borderColor: '#FFFFFF', fillOpacity: 0.8, activeLineWidth: 2 }
  }

  let uType = type
  if (chartType === ChartType.LINE && boolOpt(options, 'area')) uType = 'area'
  if (horizontal) uType = 'bar'

  return {
    type: uType,
    categories,
    series,
    extra,
    color,
    title: cfg?.title || '',
    legendShow: series.length > 1 || boolOpt(options, 'legend'),
  }
}
