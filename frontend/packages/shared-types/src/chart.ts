/**
 * 图表相关类型
 */

/** 图表类型 */
export enum ChartType {
  BAR = 'bar',
  LINE = 'line',
  PIE = 'pie',
  SCATTER = 'scatter',
  MAP = 'map',
  RADAR = 'radar',
  GAUGE = 'gauge',
  HEATMAP = 'heatmap',
  TREEMAP = 'treemap',
  SUNBURST = 'sunburst',
  WATERFALL = 'waterfall',
  FUNNEL = 'funnel',
  SANKEY = 'sankey',
  KPI_CARD = 'kpi-card',
  TABLE = 'table',
}

/** 维度类型 */
export type DimensionType = 'category' | 'time' | 'value'

/** 度量聚合方式 */
export type AggregationType = 'sum' | 'avg' | 'count' | 'max' | 'min' | 'distinct'

/** 图表配置 */
export interface ChartConfig {
  chartType: ChartType
  title?: string
  datasetId?: string | number
  dimensions: ChartDimension[]
  measures: ChartMeasure[]
  filters?: ChartFilter[]
  sorts?: ChartSort[]
  options: Record<string, unknown> // ECharts 选项覆盖
  theme?: 'light' | 'dark' | 'brand'
  animation?: boolean
}

/** 维度字段 */
export interface ChartDimension {
  field: string
  alias?: string
  type?: DimensionType
  format?: string
  show?: boolean
}

/** 度量字段 */
export interface ChartMeasure {
  field: string
  alias?: string
  aggregation: AggregationType
  format?: string
  show?: boolean
}

/** 过滤器 */
export interface ChartFilter {
  field: string
  operator: FilterOperator
  value: unknown
  valueType?: 'static' | 'dynamic' | 'variable'
  logic?: 'and' | 'or'
}

export type FilterOperator = '=' | '!=' | '>' | '>=' | '<' | '<=' | 'in' | 'notIn' | 'like' | 'between' | 'isNull' | 'isNotNull'

/** 排序 */
export interface ChartSort {
  field: string
  order: 'asc' | 'desc'
}

/** 图表事件 */
export interface ChartEvent {
  type: 'click' | 'dblclick' | 'mouseover' | 'mouseout' | 'brush' | 'brushEnd'
  target?: string
  payload?: Record<string, unknown>
}
