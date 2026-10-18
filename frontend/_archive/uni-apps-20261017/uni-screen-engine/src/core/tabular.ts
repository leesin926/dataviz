/**
 * 表格式组件（table / scrollBoard）的取数归一（4.2）。
 *
 * 两条来源必须分开处理：
 * - 编辑器内置演示数据放在 `props.rows`，是**二维数组**（`columns` 是字符串数组）；
 * - 真数据（static/dataset/http）经数据层拿到的是**对象数组**，列顺序取 `columns`，
 *   没有 columns 时按首行键序 —— 不这么收口，两种形状会在渲染层各写一遍分支。
 */

import type { ScreenComponent } from '@dataviz/shared-types'
import type { ChartData } from './chartAdapter'

export interface Tabular {
  columns: string[]
  rows: unknown[][]
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return !!value && typeof value === 'object' && !Array.isArray(value)
}

function fromRows(rows: unknown, columns?: string[]): Tabular | null {
  if (!Array.isArray(rows) || !rows.length) return null
  const first = rows[0]
  if (Array.isArray(first)) {
    const header = Array.isArray(columns) && columns.length ? columns.map(String) : first.map((_, i) => `列${i + 1}`)
    return {
      columns: header,
      rows: rows.map((r) => (Array.isArray(r) ? r : [])),
    }
  }
  if (isRecord(first)) {
    const header = columns && columns.length ? columns : Object.keys(first)
    return {
      columns: header,
      rows: rows.filter(isRecord).map((r) => header.map((k) => r[k] ?? '')),
    }
  }
  return null
}

export function toTabular(component: ScreenComponent, data: ChartData | null): Tabular | null {
  const p = component.props || {}
  if (data && Array.isArray(data.rows) && data.rows.length) {
    const cols = (data.columns || []).map((c) => (typeof c === 'string' ? c : c.field)).filter(Boolean) as string[]
    const table = fromRows(data.rows, cols.length ? cols : undefined)
    if (table) return table
  }
  const propColumns = Array.isArray(p.columns) ? (p.columns as unknown[]).map((c) => (isRecord(c) ? String(c.field ?? c.title ?? '') : String(c))) : undefined
  return fromRows(p.rows, propColumns)
}

/** 单元格值 → 展示文本：数字保留原值，日期时间戳转本地串 */
export function cellText(value: unknown): string {
  if (value === null || value === undefined) return ''
  if (typeof value === 'number') return Number.isInteger(value) ? String(value) : value.toFixed(2)
  return String(value)
}
