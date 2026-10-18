/**
 * `@qiun/ucharts` 是纯 JS 包（无自带类型），这里声明本包用到的最小面。
 * 只声明实际用到的成员：构造 + updateData + showData；其余 API 不臆造。
 */
declare module '@qiun/ucharts' {
  export interface UChartsOptions {
    type?: string
    context?: unknown
    width?: number
    height?: number
    pixelRatio?: number
    background?: string
    padding?: number[]
    fontSize?: number
    legendFontSize?: number
    dataLabel?: boolean
    categories?: unknown
    series?: unknown
    color?: string[]
    extra?: Record<string, unknown>
    [key: string]: unknown
  }

  export default class uCharts {
    constructor(options: UChartsOptions)
    updateData(options: UChartsOptions): void
    showData(options?: UChartsOptions): void
  }
}
