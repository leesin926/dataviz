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
  summarize(
    result: QueryResult,
  ): Record<string, { sum: number | null; avg: number | null; max: number | null; min: number | null }> {
    const summary: Record<string, { sum: number | null; avg: number | null; max: number | null; min: number | null }> = {}
    for (const col of result.columns) {
      if (col.type !== 'number') continue
      // 一行都没有：不产出该键（调用方不能假定每个 number 列都在汇总里）
      if (result.rows.length === 0) continue
      const values: number[] = []
      for (const row of result.rows) {
        const v = row[col.field]
        // null/undefined/'' 是"没有数"，直接跳过、不进均值分母（API-143）；真正的非数字垃圾仍剔除
        if (v === null || v === undefined || v === '') continue
        const n = Number(v)
        if (Number.isNaN(n)) continue
        values.push(n)
      }
      if (values.length === 0) {
        // 整列都是缺失值：没有数 ≠ 等于 0，四个统计量都给 null 而不是 0
        summary[col.field] = { sum: null, avg: null, max: null, min: null }
        continue
      }
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
   * 转为百分比形式 (相对于总和)，返回数字（百分点，如 25 表示 25%）。
   * API-144：类型不再撒谎 —— 之前返回带百分号的字符串却声明 number[]。
   */
  toPercent(result: QueryResult, field: string): number[] {
    const values = result.rows.map((r) => Number(r[field] ?? 0))
    const total = values.reduce((a, b) => a + b, 0)
    const decimals = this.options.percentDecimals ?? 2
    return values.map((v) => Number(((total > 0 ? (v / total) * 100 : 0).toFixed(decimals))))
  }

  /**
   * 转为百分比形式的展示字符串（如 '25.00%'），与 toPercent 的数字口径同源（API-144）。
   */
  toPercentLabel(result: QueryResult, field: string): string[] {
    const values = result.rows.map((r) => Number(r[field] ?? 0))
    const total = values.reduce((a, b) => a + b, 0)
    return values.map((v) => formatPercent(total > 0 ? v / total : 0, this.options.percentDecimals))
  }
}
