/**
 * 仪表盘相关类型
 */

import type { ChartConfig } from './chart'

export interface Dashboard {
  id: string | number
  name: string
  description?: string
  cover?: string
  layout: DashboardLayout
  widgets: Widget[]
  status: 'draft' | 'published' | 'archived'
  isTemplate?: boolean
  createdBy?: string | number
  createdAt?: string
  updatedAt?: string
  publishedAt?: string
}

export interface DashboardLayout {
  width: number // 栅格宽度
  cols: number
  rowHeight: number
  background?: string
  padding?: number
}

export interface Widget {
  id: string
  type: 'chart' | 'text' | 'image' | 'filter' | 'container' | 'kpi'
  name: string
  x: number
  y: number
  w: number
  h: number
  config: WidgetConfig
  chartConfig?: ChartConfig
  children?: Widget[]
}

export interface WidgetConfig {
  datasetId?: string | number
  refreshInterval?: number // 秒
  padding?: number
  backgroundColor?: string
  borderColor?: string
  borderRadius?: number
  showTitle?: boolean
  title?: string
  titleStyle?: Record<string, unknown>
  [key: string]: unknown
}

export interface DashboardSnapshot {
  dashboardId: string | number
  widgets: Widget[]
  takenAt: string
}
