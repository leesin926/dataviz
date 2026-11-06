import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { DATE_FORMATS, diffDays, formatDate, fromNow, getDateRange, isValidDate } from './date'

/** 用本地时间构造输入与期望值：跨 UTC 偏移的机器上结果也应一致 */
const NOW = new Date(2026, 2, 15, 12, 0, 0) // 2026-03-15 12:00，周日
const SAMPLE = new Date(2026, 2, 15, 10, 20, 30)

describe('shared-utils/date —— 格式化', () => {
  beforeEach(() => {
    vi.useFakeTimers({ now: NOW })
  })
  afterEach(() => {
    vi.useRealTimers()
  })

  it('默认格式补齐到秒，月/日/时分秒零填充', () => {
    expect(formatDate(SAMPLE)).toBe('2026-03-15 10:20:30')
    expect(formatDate(SAMPLE, 'YYYY/MM/DD')).toBe('2026/03/15')
    expect(formatDate(SAMPLE, 'MM-DD HH:mm')).toBe('03-15 10:20')
    expect(formatDate(SAMPLE, 'HH:mm:ss')).toBe('10:20:30')
  })

  it('Date / 时间戳 / 字符串三种入参格式化到同一天，枚举清单就是这 6 种', () => {
    expect(formatDate(SAMPLE, 'YYYY-MM-DD')).toBe('2026-03-15')
    expect(formatDate(SAMPLE.getTime(), 'YYYY-MM-DD')).toBe('2026-03-15')
    expect(formatDate('2026-03-15T10:20:30', 'YYYY-MM-DD HH:mm:ss')).toBe('2026-03-15 10:20:30')
    expect(DATE_FORMATS).toEqual(['YYYY-MM-DD', 'YYYY-MM-DD HH:mm:ss', 'YYYY/MM/DD', 'YYYY-MM-DD HH:mm', 'MM-DD HH:mm', 'HH:mm:ss'])
  })

  it('无法解析的输入返回字面量 "Invalid Date"，不抛异常也不回空串', () => {
    // given：日期选择器清空后可能传进来空串以外的垃圾
    expect(formatDate('not-a-date')).toBe('Invalid Date')
  })

  it('fromNow 走 zh-cn 相对时间（模块加载即 dayjs.locale("zh-cn")）', () => {
    expect(fromNow(new Date(2026, 2, 15, 9, 0, 0))).toBe('3 小时前')
    expect(fromNow(new Date(2026, 2, 13, 12, 0, 0))).toBe('2 天前')
  })
})

describe('shared-utils/date —— 区间、差值与合法性', () => {
  beforeEach(() => {
    vi.useFakeTimers({ now: NOW })
  })
  afterEach(() => {
    vi.useRealTimers()
  })

  it('getDateRange 的 today/month/year 以本地日切对齐，末日补到 .999', () => {
    const today = getDateRange('today')
    expect(today[0].getTime()).toBe(new Date(2026, 2, 15, 0, 0, 0, 0).getTime())
    expect(today[1].getTime()).toBe(new Date(2026, 2, 15, 23, 59, 59, 999).getTime())

    const month = getDateRange('month')
    expect(month[0].getTime()).toBe(new Date(2026, 2, 1, 0, 0, 0, 0).getTime())
    expect(month[1].getTime()).toBe(new Date(2026, 2, 31, 23, 59, 59, 999).getTime())

    const year = getDateRange('year')
    expect(year[0].getTime()).toBe(new Date(2026, 0, 1, 0, 0, 0, 0).getTime())
    expect(year[1].getTime()).toBe(new Date(2026, 11, 31, 23, 59, 59, 999).getTime())
  })

  it('getDateRange 的 last7/last30 含当天（减 6 / 减 29 天再 startOf("day")），week 按 zh-cn 从周一起算', () => {
    const last7 = getDateRange('last7')
    expect(last7[0].getTime()).toBe(new Date(2026, 2, 9, 0, 0, 0, 0).getTime())
    expect(last7[1].getTime()).toBe(new Date(2026, 2, 15, 23, 59, 59, 999).getTime())

    const last30 = getDateRange('last30')
    expect(last30[0].getTime()).toBe(new Date(2026, 1, 14, 0, 0, 0, 0).getTime())

    const week = getDateRange('week')
    expect(week[0].getTime()).toBe(new Date(2026, 2, 9, 0, 0, 0, 0).getTime())
  })

  it('diffDays 取绝对值，与参数顺序无关', () => {
    expect(diffDays('2026-03-15', '2026-03-10')).toBe(5)
    expect(diffDays('2026-03-10', '2026-03-15')).toBe(5)
    expect(diffDays('2026-03-15T23:59:00', '2026-03-15T00:01:00')).toBe(0)
  })

  it('diffDays 的入参不可解析时返回 NaN，调用方拿不到可用数字', () => {
    expect(diffDays('abc', '2026-03-10')).toBeNaN()
  })

  it('isValidDate 认合法日期与 0 点时间戳，拒空串与 null', () => {
    expect(isValidDate('2026-03-15')).toBe(true)
    expect(isValidDate('2026-03-15 10:20:30')).toBe(true)
    expect(isValidDate('')).toBe(false)
    expect(isValidDate(null)).toBe(false)
    expect(isValidDate(new Date(2026, 2, 15).getTime())).toBe(true)
  })

  it('isValidDate 按分量校验，越界月份/日期判 false 而不是被 dayjs 进位蒙混（API-150）', () => {
    // given：'2026-13-45' 不可能是一个真实日期
    expect(isValidDate('2026-13-45')).toBe(false)
    expect(isValidDate('2026-02-30')).toBe(false)
    expect(isValidDate('2026-04-31')).toBe(false)
    // 闰年 2/29 合法、平年 2/29 非法
    expect(isValidDate('2024-02-29')).toBe(true)
    expect(isValidDate('2023-02-29')).toBe(false)
    // 时间分量越界同样拒绝
    expect(isValidDate('2026-03-15 24:00:00')).toBe(false)
    expect(isValidDate('2026-03-15 10:60:00')).toBe(false)
    // 合法值仍放行
    expect(isValidDate('2026-03-15 10:20:30')).toBe(true)
    // dayjs 的进位行为本身没变，只是 isValidDate 不再采信它
    expect(formatDate('2026-13-45', 'YYYY-MM-DD')).toBe('2027-02-14')
  })
})
