import dayjs from 'dayjs'
import relativeTime from 'dayjs/plugin/relativeTime'
import 'dayjs/locale/zh-cn'

dayjs.extend(relativeTime)
dayjs.locale('zh-cn')

export type DateFormat =
  | 'YYYY-MM-DD'
  | 'YYYY-MM-DD HH:mm:ss'
  | 'YYYY/MM/DD'
  | 'YYYY-MM-DD HH:mm'
  | 'MM-DD HH:mm'
  | 'HH:mm:ss'

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

/**
 * 判断是否是合法日期
 */
export function isValidDate(date: unknown): boolean {
  return dayjs(date as string).isValid()
}

export { dayjs }
