import dayjs from 'dayjs'
import relativeTime from 'dayjs/plugin/relativeTime'
import 'dayjs/locale/zh-cn'

dayjs.extend(relativeTime)
dayjs.locale('zh-cn')

/** 支持的日期格式清单：类型与运行时校验共用这一份，避免设计器填了枚举外的串却过编译 */
export const DATE_FORMATS = [
  'YYYY-MM-DD',
  'YYYY-MM-DD HH:mm:ss',
  'YYYY/MM/DD',
  'YYYY-MM-DD HH:mm',
  'MM-DD HH:mm',
  'HH:mm:ss',
] as const

export type DateFormat = (typeof DATE_FORMATS)[number]

/**
 * 格式化日期时间
 */
export function formatDate(
  date: Date | string | number | dayjs.Dayjs,
  format: DateFormat = 'YYYY-MM-DD HH:mm:ss',
): string {
  return dayjs(date).format(format)
}

/**
 * 获取相对时间 (e.g. "3 小时前")
 */
export function fromNow(date: Date | string | number): string {
  return dayjs(date).fromNow()
}

/**
 * 获取日期范围 (快捷选项)
 */
export function getDateRange(type: 'today' | 'week' | 'month' | 'year' | 'last7' | 'last30'): [Date, Date] {
  const now = dayjs()
  const map = {
    today: [now.startOf('day'), now.endOf('day')],
    week: [now.startOf('week'), now.endOf('week')],
    month: [now.startOf('month'), now.endOf('month')],
    year: [now.startOf('year'), now.endOf('year')],
    last7: [now.subtract(6, 'day').startOf('day'), now.endOf('day')],
    last30: [now.subtract(29, 'day').startOf('day'), now.endOf('day')],
  } as const
  const [start, end] = map[type]
  return [start.toDate(), end.toDate()]
}

/**
 * 计算两个日期的差值 (天)
 */
export function diffDays(a: Date | string | number, b: Date | string | number): number {
  return Math.abs(dayjs(a).diff(dayjs(b), 'day'))
}

/** 某年某月的天数，含闰年二月判定（API-150） */
function daysInMonth(year: number, month: number): number {
  const lengths = [31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31]
  if (month === 2 && ((year % 4 === 0 && year % 100 !== 0) || year % 400 === 0)) return 29
  return lengths[month - 1]
}

/**
 * 判断是否是合法日期
 * dayjs 会把 '2026-13-45' 这类越界串直接进位成 2027-02-14 并判为有效（API-150）。
 * 对完整日期串改为按分量校验：月 1-12、日落在当月天数（含闰年）、时分秒在各自范围内。
 */
export function isValidDate(date: unknown): boolean {
  if (typeof date === 'string') return isValidDateString(date)
  if (typeof date === 'number') return Number.isFinite(date) && dayjs(date).isValid()
  if (date instanceof Date) return !Number.isNaN(date.getTime())
  if (date === null || date === undefined) return false
  return dayjs(date as never).isValid()
}

function isValidDateString(value: string): boolean {
  const trimmed = value.trim()
  if (!trimmed) return false
  const m = /^(\d{4})[-/](\d{1,2})[-/](\d{1,2})(?:[ T](\d{1,2}):(\d{1,2})(?::(\d{1,2}))?)?$/.exec(trimmed)
  if (!m) {
    // 非"完整日期[时间]"形态（如纯时间串、相对串）仍交回 dayjs 宽松判定，避免过度收窄合法入参
    return dayjs(trimmed).isValid()
  }
  const year = Number(m[1])
  const month = Number(m[2])
  const day = Number(m[3])
  if (month < 1 || month > 12) return false
  if (day < 1 || day > daysInMonth(year, month)) return false
  if (m[4] !== undefined) {
    const hour = Number(m[4])
    const minute = Number(m[5])
    const second = m[6] === undefined ? 0 : Number(m[6])
    if (hour < 0 || hour > 23) return false
    if (minute < 0 || minute > 59) return false
    if (second < 0 || second > 59) return false
  }
  return true
}

export { dayjs }
