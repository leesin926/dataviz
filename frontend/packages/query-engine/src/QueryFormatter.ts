import type { QueryResult } from '@dataviz/shared-types'
import { formatNumber, formatPercent } from '@dataviz/shared-utils'

export interface FormatOptions {
  numberDecimals?: number
  percentDecimals?: number
  dateFormat?: string
}

/**
 * 查询结果格式化
 */
export class QueryFormatter {
  private options: FormatOptions

  constructor(options: FormatOptions = {}) {
    this.options = {
      numberDecimals: options.numberDecimals ?? 2,
      percentDecimals: options.percentDecimals ?? 2,
      dateFormat: options.dateFormat ?? 'YYYY-MM-DD',
    }
  }

  /**
   * 按列类型格式化一行数据
   */
  formatRow(row: Record<string, unknown>, result: QueryResult): Record<string, string> {
    const formatted: Record<string, string> = {}
    for (const col of result.columns) {
      const val = row[col.field]
      switch (col.type) {
        case 'number':
          formatted[col.field] = formatNumber(val as number, this.options.numberDecimals)
          break
        case 'date':
          formatted[col.field] = String(val ?? '')
          break
        default:
          formatted[col.field] = String(val ?? '')
      }
    }
    return formatted
  }

  /**
   * 转换为 CSV 字符串
   */
  toCsv(result: QueryResult): string {
    const headers = result.columns.map((c) => c.displayName || c.field)
    const lines = [headers.join(',')]
    for (const row of result.rows) {
      const line = result.columns.map((c) => {
        const v = row[c.field]
        const s = v === null || v === undefined ? '' : String(v)
        if (s.includes(',') || s.includes('"')) return `"${s.replace(/"/g, '""')}"`
        return s
      })
      lines.push(line.join(','))
    }
    return '\ufeff' + lines.join('\n')
  }

  /**
   * 计算汇总 (针对数值列)
   */
  summarize(result: QueryResult): Record<string, { sum: number; avg: number; max: number; min: number }> {
    const summary: Record<string, { sum: number; avg: number; max: number; min: number }> = {}
    for (const col of result.columns) {
      if (col.type !== 'number') continue
      const values = result.rows.map((r) => Number(r[col.field] ?? 0)).filter((v) => !isNaN(v))
      if (values.length === 0) continue
      const sum = values.reduce((a, b) => a + b, 0)
      summary[col.field] = {
        sum,
        avg: sum / values.length,
        max: Math.max(...values),
        min: Math.min(...values),
      }
    }
    return summary
  }

  /**
   * 转为百分比形式 (相对于总和)
   */
  toPercent(result: QueryResult, field: string): number[] {
    const values = result.rows.map((r) => Number(r[field] ?? 0))
    const total = values.reduce((a, b) => a + b, 0)
    return values.map((v) => formatPercent(total > 0 ? v / total : 0, this.options.percentDecimals) as unknown as number)
  }
}
