import { describe, expect, it } from 'vitest'
import type { QueryColumn, QueryResult } from '@dataviz/shared-types'
import { QueryFormatter } from './QueryFormatter'

function result(columns: QueryColumn[], rows: Record<string, unknown>[]): QueryResult {
  return { columns, rows, elapsed: 1 }
}

const MIXED: QueryColumn[] = [
  { field: 'amount', type: 'number' },
  { field: 'day', type: 'date' },
  { field: 'region', type: 'string' },
]

describe('query-engine/QueryFormatter.formatRow —— 按列类型格式化', () => {
  it('number 列走千分位并补齐到 numberDecimals，date/string 列原样转字符串', () => {
    const fmt = new QueryFormatter()
    const row = { amount: 1234.5, day: '2026-01-01', region: '华东' }

    expect(fmt.formatRow(row, result(MIXED, [row]))).toEqual({ amount: '1,234.50', day: '2026-01-01', region: '华东' })
  })

  it('numberDecimals=0 时按四舍五入到整数，null 数值列显示 "-"，null 文本列显示空串', () => {
    const fmt = new QueryFormatter({ numberDecimals: 0 })
    const row = { amount: 1234.5, day: null, region: null }

    expect(fmt.formatRow(row, result(MIXED, [row]))).toEqual({ amount: '1,235', day: '', region: '' })
  })

  it('结果里没有的列也会出现在格式化行上（按列定义遍历，值为空串）', () => {
    const fmt = new QueryFormatter()

    expect(fmt.formatRow({ day: '2026-01-01' }, result(MIXED, [])) ).toEqual({ amount: '-', day: '2026-01-01', region: '' })
  })
})

describe('query-engine/QueryFormatter.toCsv —— 导出文本', () => {
  it('首行带 UTF-8 BOM，表头优先用 displayName', () => {
    const fmt = new QueryFormatter()
    const csv = fmt.toCsv(
      result([{ field: 'day', type: 'date', displayName: '日期' }, { field: 'note', type: 'string' }], [
        { day: '2026-01-01', note: 'ok' },
      ]),
    )

    expect(csv).toBe('\ufeff' + '日期,note\n2026-01-01,ok')
    expect(csv.charCodeAt(0)).toBe(0xfeff)
  })

  it('含逗号与双引号的单元格按 CSV 规则加引号并转义', () => {
    const fmt = new QueryFormatter()
    const csv = fmt.toCsv(
      result([{ field: 'note', type: 'string' }], [{ note: 'a,b' }, { note: 'say "hi"' }, { note: null }]),
    )

    expect(csv).toBe('\ufeff' + 'note\n"a,b"\n"say ""hi"""\n')
  })
})

describe('query-engine/QueryFormatter.summarize / toPercent —— 汇总口径', () => {
  it('summarize 只统计 number 列，并给出 sum/avg/max/min', () => {
    const fmt = new QueryFormatter()
    const out = fmt.summarize(
      result([{ field: 'amt', type: 'number' }, { field: 'name', type: 'string' }], [
        { amt: 10, name: 'a' },
        { amt: 5, name: 'b' },
      ]),
    )

    expect(out).toEqual({ amt: { sum: 15, avg: 7.5, max: 10, min: 5 } })
  })

  it('summarize 把 null 当成 0 计入均值、把非数字串剔掉（缺陷：均值被 null 拉低，按实际行为钉住）', () => {
    // given：后端把缺失指标回成 null 是常态
    const fmt = new QueryFormatter()
    const out = fmt.summarize(result([{ field: 'amt', type: 'number' }], [{ amt: 10 }, { amt: null }, { amt: 'abc' }, { amt: 5 }]))

    expect(out.amt).toEqual({ sum: 15, avg: 5, max: 10, min: 0 })
  })

  it('数值列一行都没有时不产出该键，调用方不能假定每个 number 列都在汇总里', () => {
    const fmt = new QueryFormatter()

    expect(fmt.summarize(result([{ field: 'amt', type: 'number' }], []))).toEqual({})
  })

  it('toPercent 返回的是带百分号的字符串数组（签名写的是 number[]，类型与实际不符）', () => {
    const fmt = new QueryFormatter()

    expect(fmt.toPercent(result([{ field: 'amt', type: 'number' }], [{ amt: 1 }, { amt: 3 }]), 'amt')).toEqual([
      '25.00%',
      '75.00%',
    ])
  })

  it('toPercent 在总和为 0 时整列给 0.00%，不产生 NaN；字段不在行里时同样按 0 处理', () => {
    const fmt = new QueryFormatter()

    expect(fmt.toPercent(result([{ field: 'amt', type: 'number' }], [{ amt: 0 }, { amt: 0 }]), 'amt')).toEqual(['0.00%', '0.00%'])
    expect(fmt.toPercent(result([{ field: 'amt', type: 'number' }], [{ other: 1 }]), 'amt')).toEqual(['0.00%'])
  })
})
