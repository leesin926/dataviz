import { describe, expect, it } from 'vitest'
import type { ChartConfig } from '@dataviz/shared-types'
import { QueryParser } from './QueryParser'

const parser = () => new QueryParser()

describe('query-engine/QueryParser.parse —— 可视化配置到 AnalysisQuery', () => {
  it('缺省口径：datasetId 落 0、聚合落 sum、维度别名落物理列名、limit 固定 1000', () => {
    const config = {
      chartType: 'bar',
      dimensions: [{ field: 'day' }],
      measures: [{ field: 'amount' }],
      options: {},
    } as unknown as ChartConfig

    expect(parser().parse(config)).toEqual({
      datasetId: 0,
      dimensions: [{ field: 'day', alias: 'day' }],
      measures: [{ field: 'amount', alias: 'amount', aggregation: 'sum' }],
      filters: [],
      sorts: [],
      limit: 1000,
    })
  })

  it('已有 alias / aggregation / filters / sorts 原样带过，不被默认值覆盖', () => {
    const config = {
      chartType: 'line',
      datasetId: 'ds-9',
      dimensions: [{ field: 'month', alias: '月份' }],
      measures: [{ field: 'amount', alias: '销售额', aggregation: 'avg' }],
      filters: [{ field: 'region', operator: 'in', value: ['华东'] }],
      sorts: [{ field: 'month', order: 'asc' }],
      options: {},
    } as unknown as ChartConfig

    const out = parser().parse(config)

    expect(out.datasetId).toBe('ds-9')
    expect(out.dimensions).toEqual([{ field: 'month', alias: '月份' }])
    expect(out.measures).toEqual([{ field: 'amount', alias: '销售额', aggregation: 'avg' }])
    expect(out.filters).toEqual([{ field: 'region', operator: 'in', value: ['华东'] }])
    expect(out.sorts).toEqual([{ field: 'month', order: 'asc' }])
  })

  it('配置整体为空时各数组回落成 []，不会在解析阶段抛错', () => {
    expect(parser().parse({})).toEqual({
      datasetId: 0,
      dimensions: [],
      measures: [],
      filters: [],
      sorts: [],
      limit: 1000,
    })
  })
})

describe('query-engine/QueryParser.parseSql —— 裸 SQL 的粗略拆解', () => {
  it('把 SELECT 列表逐字段切出来当维度，别名与字段同名、measures 恒空', () => {
    expect(parser().parseSql('SELECT region, day FROM sales_daily')).toEqual({
      dimensions: [
        { field: 'region', alias: 'region' },
        { field: 'day', alias: 'day' },
      ],
      measures: [],
    })
  })

  it('聚合表达式也被当成维度输出（示例实现不做函数识别），调用方不能直接拿去查数', () => {
    // given：这是最常见的裸 SQL 写法
    expect(parser().parseSql('SELECT region, SUM(amount) FROM t')).toEqual({
      dimensions: [
        { field: 'region', alias: 'region' },
        { field: 'SUM(amount)', alias: 'SUM(amount)' },
      ],
      measures: [],
    })
  })

  it('SQL 里没有 FROM 时整条 SELECT 列表都不解析，返回空维度', () => {
    expect(parser().parseSql('SELECT a, b')).toEqual({ dimensions: [], measures: [] })
  })
})
