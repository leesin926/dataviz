import { describe, expect, it } from 'vitest'
import { compactNumber, formatBytes, formatNumber, formatPercent, calcPercent, toFixed } from './number'

describe('shared-utils/number —— 千分位与小数位', () => {
  it('formatNumber 按 zh-CN 输出千分位，decimals=0 时不留小数', () => {
    expect(formatNumber(1234567)).toBe('1,234,567')
    expect(formatNumber(1234)).toBe('1,234')
  })

  it('formatNumber 的 decimals 同时是最小与最大小数位，不足补零', () => {
    expect(formatNumber(1234567.891, 2)).toBe('1,234,567.89')
    expect(formatNumber(5, 2)).toBe('5.00')
  })

  it('null / undefined / 空串 / 非数字字符串统一显示为占位符 "-"，不出现 NaN', () => {
    expect(formatNumber(null)).toBe('-')
    expect(formatNumber(undefined)).toBe('-')
    expect(formatNumber('')).toBe('-')
    expect(formatNumber('abc')).toBe('-')
  })

  it('字符串数字先 parseFloat 再格式化（后端把数值列回成字符串时不能显示成 "-"）', () => {
    expect(formatNumber('8500', 0)).toBe('8,500')
    expect(formatNumber('1234.50', 2)).toBe('1,234.50')
  })
})

describe('shared-utils/number —— 百分比、单位与字节', () => {
  it('formatPercent 乘 100 后按 decimals 保留，null/NaN 走占位符', () => {
    expect(formatPercent(0.1234, 2)).toBe('12.34%')
    expect(formatPercent(0.5, 0)).toBe('50%')
    expect(formatPercent(null)).toBe('-')
    expect(formatPercent(Number.NaN)).toBe('-')
  })

  it('compactNumber 在 1e4 / 1e8 切单位，未达阈值时原样输出', () => {
    expect(compactNumber(12345)).toBe('1.23万')
    expect(compactNumber(123456789)).toBe('1.23亿')
    expect(compactNumber(9999)).toBe('9999')
    expect(compactNumber(0)).toBe('0')
  })

  it('compactNumber 按绝对值判阈值，负数保留符号而不是被当成小额原样输出', () => {
    expect(compactNumber(-12345)).toBe('-1.23万')
    expect(compactNumber('-123456789')).toBe('-1.23亿')
  })

  it('formatBytes 以 1024 进位并去掉尾随零', () => {
    expect(formatBytes(0)).toBe('0 B')
    expect(formatBytes(1024)).toBe('1 KB')
    expect(formatBytes(1536)).toBe('1.5 KB')
    expect(formatBytes(1024 * 1024 * 1024)).toBe('1 GB')
  })

  it('formatBytes 超过 1024^6 后单位数组越界，输出 "1 undefined"（缺陷，按实际行为钉住）', () => {
    expect(formatBytes(1024 ** 6)).toBe('1 undefined')
  })

  it('toFixed 返回数字而非字符串，calcPercent 对 total=0 直接给 0 不产生 Infinity', () => {
    expect(toFixed(3.14159, 2)).toBe(3.14)
    expect(toFixed(3.14159)).toBe(3.14)
    expect(calcPercent(30, 100)).toBe(0.3)
    expect(calcPercent(1, 0)).toBe(0)
  })
})
