/**
 * 数字格式化工具
 */

/**
 * 千分位格式化
 * formatNumber(1234567) => "1,234,567"
 * formatNumber(1234567.89, 2) => "1,234,567.89"
 */
export function formatNumber(value: number | string | null | undefined, decimals = 0): string {
  if (value === null || value === undefined || value === '') return '-'
  const num = typeof value === 'string' ? parseFloat(value) : value
  if (isNaN(num)) return '-'
  return num.toLocaleString('zh-CN', {
    minimumFractionDigits: decimals,
    maximumFractionDigits: decimals,
  })
}

/**
 * 百分比格式化
 * formatPercent(0.1234, 2) => "12.34%"
 */
export function formatPercent(value: number | null | undefined, decimals = 2): string {
  if (value === null || value === undefined || isNaN(value)) return '-'
  return (value * 100).toFixed(decimals) + '%'
}

/**
 * 单位格式化 (自动选择 万/亿)
 * compactNumber(12345) => "1.23万"
 * compactNumber(123456789) => "1.23亿"
 */
export function compactNumber(value: number | string | null | undefined, decimals = 2): string {
  if (value === null || value === undefined || value === '') return '-'
  const num = typeof value === 'string' ? parseFloat(value) : value
  if (isNaN(num)) return '-'
  const absNum = Math.abs(num)
  if (absNum >= 1e8) {
    return (num / 1e8).toFixed(decimals) + '亿'
  }
  if (absNum >= 1e4) {
    return (num / 1e4).toFixed(decimals) + '万'
  }
  return num.toString()
}

/**
 * 字节格式化
 * formatBytes(1024) => "1 KB"
 * formatBytes(1024 * 1024 * 1024) => "1 GB"
 */
export function formatBytes(bytes: number, decimals = 2): string {
  if (bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB', 'PB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(decimals)) + ' ' + sizes[i]
}

/**
 * 保留有效数字
 */
export function toFixed(value: number, digits = 2): number {
  return Number(value.toFixed(digits))
}

/**
 * 计算百分比 (带总数保护)
 */
export function calcPercent(part: number, total: number): number {
  if (total === 0) return 0
  return part / total
}
