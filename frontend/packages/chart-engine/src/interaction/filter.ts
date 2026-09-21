import type { ChartFilter, FilterOperator } from '@dataviz/shared-types'

/**
 * 图表过滤器
 */
export class ChartFilterManager {
  private filters: ChartFilter[] = []
  private onChange: ((filters: ChartFilter[]) => void) | null = null

  constructor(onChange?: (filters: ChartFilter[]) => void) {
    this.onChange = onChange || null
  }

  /**
   * 设置过滤器
   */
  setFilters(filters: ChartFilter[]): void {
    this.filters = filters
    this.notify()
  }

  /**
   * 添加单个过滤器
   */
  addFilter(filter: ChartFilter): void {
    this.filters.push(filter)
    this.notify()
  }

  /**
   * 移除指定字段的过滤器
   */
  removeFilter(field: string): void {
    this.filters = this.filters.filter((f) => f.field !== field)
    this.notify()
  }

  /**
   * 清空所有过滤器
   */
  clear(): void {
    this.filters = []
    this.notify()
  }

  /**
   * 获取当前过滤器
   */
  getFilters(): ChartFilter[] {
    return [...this.filters]
  }

  /**
   * 合并多个过滤器 (按 and/or 逻辑)
   */
  static merge(filtersList: ChartFilter[][], logic: 'and' | 'or' = 'and'): ChartFilter[] {
    const merged: ChartFilter[] = []
    for (const list of filtersList) {
      for (const f of list) {
        merged.push({ ...f, logic })
      }
    }
    return merged
  }

  /**
   * 构建 SQL 条件片段 (示例)
   */
  static buildSqlWhere(filters: ChartFilter[]): string {
    if (filters.length === 0) return ''
    const parts = filters.map((f) => {
      const val = typeof f.value === 'string' ? `'${f.value}'` : f.value
      switch (f.operator as FilterOperator) {
        case 'in':
          return `${f.field} IN (${(f.value as unknown[]).map((v) => (typeof v === 'string' ? `'${v}'` : v)).join(',')})`
        case 'between': {
          const [a, b] = f.value as [unknown, unknown]
          return `${f.field} BETWEEN ${typeof a === 'string' ? `'${a}'` : a} AND ${typeof b === 'string' ? `'${b}'` : b}`
        }
        case 'isNull':
          return `${f.field} IS NULL`
        case 'isNotNull':
          return `${f.field} IS NOT NULL`
        case 'like':
          return `${f.field} LIKE '%${f.value}%'`
        default:
          return `${f.field} ${f.operator} ${val}`
      }
    })
    return `WHERE ${parts.join(' AND ')}`
  }

  private notify(): void {
    this.onChange?.([...this.filters])
  }
}
