import { describe, expect, it } from 'vitest'
import { ChartType } from '@dataviz/shared-types'
import type { ChartConfig } from '@dataviz/shared-types'
import { DataAdapter } from './DataAdapter'

/**
 * DataAdapter.transform 的契约来源是后端 SqlBuilder（analysis-service/engine/SqlBuilder.java）：
 * - 度量投影一律带别名：`SUM(amount) AS `gross``，别名清洗后为空时回落 `<聚合>_<字段>`（`sum_amount`）
 * - 维度投影用清洗后的物理列名做键（dimensionExpressions 的 map key），不用前端 alias
 * 于是"按 m.field 取值"必然取空 —— API-108 的症状（HTTP 200、rows 有数、图是空的）就是这么来的。
 * 这组用例把候选回落链的顺序与各种脏别名钉死。
 */

interface DimSpec {
  field: string
  alias?: string
}

interface MeasureSpec {
  field: string
  alias?: string
  aggregation?: string
}

/** 造 config：aggregation 允许缺省，用来覆盖 `m.aggregation ?? 'sum'` 这条回落分支 */
function cfg(dimensions: DimSpec[], measures: MeasureSpec[]): ChartConfig {
  return {
    chartType: ChartType.BAR,
    dimensions,
    measures,
    options: {},
  } as unknown as ChartConfig
}

function result(rows: Record<string, unknown>[]) {
  return { columns: [], rows }
}

describe('DataAdapter.transform —— 度量列键解析（后端别名投影）', () => {
  it('行里只有 sum_amount 时也必须取到值，不能因为 row["amount"] 为空而画空图（API-108）', () => {
    // given：后端把 SUM(amount) 投影成 sum_amount
    const config = cfg([{ field: 'day' }], [{ field: 'amount', aggregation: 'sum' }])
    const rows = [
      { day: '2026-01-01', sum_amount: 100 },
      { day: '2026-01-02', sum_amount: 250 },
    ]

    // when
    const out = DataAdapter.transform(config, result(rows))

    // then
    expect(out.series).toEqual([{ name: 'amount', data: [100, 250] }])
    expect(out.dimensions).toEqual(['2026-01-01', '2026-01-02'])
  })

  it('avg 聚合的回落键是 avg_amount（聚合名参与键名，不能一律按 sum 找）', () => {
    const config = cfg([{ field: 'day' }], [{ field: 'amount', aggregation: 'avg' }])
    const out = DataAdapter.transform(config, result([{ day: '2026-01-01', sum_amount: 100, avg_amount: 4 }]))

    expect(out.series[0].data).toEqual([4])
  })

  it('aggregation 缺省时按 sum 回落，仍能取到 sum_amount', () => {
    const config = cfg([{ field: 'day' }], [{ field: 'amount' }])
    const out = DataAdapter.transform(config, result([{ day: '2026-01-01', sum_amount: 3 }]))

    expect(out.series[0].data).toEqual([3])
  })

  it('显式 alias 命中时取别名列，系列名用 alias 而不是物理列名', () => {
    const config = cfg([{ field: 'day' }], [{ field: 'amount', alias: 'gross', aggregation: 'sum' }])
    const out = DataAdapter.transform(config, result([{ day: '2026-01-01', gross: 9 }]))

    expect(out.series[0].name).toBe('gross')
    expect(out.series[0].data).toEqual([9])
  })

  it('候选链是 field 优先：行里同时有 amount 与 gross 时取的是 amount（alias 只用于命名）', () => {
    // given：物理列名恰好也在结果行里（裸 SQL 查询会这样）
    const config = cfg([{ field: 'day' }], [{ field: 'amount', alias: 'gross', aggregation: 'sum' }])
    const out = DataAdapter.transform(config, result([{ day: '2026-01-01', amount: 1, gross: 9 }]))

    expect(out.series[0].data).toEqual([1])
    expect(out.series[0].name).toBe('gross')
  })

  it('别名含非法字符时按 SqlBuilder 的清洗规则命中：「毛 额/GMV」→ 列键 GMV，系列名仍是原别名', () => {
    const config = cfg([{ field: 'day' }], [{ field: 'gmv', alias: '毛 额/GMV', aggregation: 'sum' }])
    const out = DataAdapter.transform(config, result([{ day: '2026-01-01', GMV: 7 }]))

    expect(out.series[0].data).toEqual([7])
    expect(out.series[0].name).toBe('毛 额/GMV')
  })

  it('alias 清洗后为空（纯中文别名）时回落 `<聚合>_<字段>`，仍然取到值', () => {
    const config = cfg([{ field: 'day' }], [{ field: 'amount', alias: '毛额', aggregation: 'sum' }])
    const out = DataAdapter.transform(config, result([{ day: '2026-01-01', sum_amount: 11 }]))

    expect(out.series[0].data).toEqual([11])
    expect(out.series[0].name).toBe('毛额')
  })

  it('同一字段聚合两次（sum_amount + avg_amount）在简单模式下各取各的，不得串行', () => {
    const config = cfg(
      [{ field: 'day' }],
      [
        { field: 'amount', aggregation: 'sum' },
        { field: 'amount', aggregation: 'avg' },
      ],
    )
    const rows = [
      { day: '2026-01-01', sum_amount: 10, avg_amount: 2 },
      { day: '2026-01-02', sum_amount: 30, avg_amount: 6 },
    ]

    const out = DataAdapter.transform(config, result(rows))

    expect(out.measures).toEqual(['amount', 'amount'])
    expect(out.series[0].data).toEqual([10, 30])
    expect(out.series[1].data).toEqual([2, 6])
  })

  it('measures 输出的是物理字段名（图表内部按字段对齐），不是别名', () => {
    const config = cfg([{ field: 'day' }], [{ field: 'amount', alias: '销售额', aggregation: 'sum' }])
    const out = DataAdapter.transform(config, result([{ day: '2026-01-01', sum_amount: 1 }]))

    expect(out.measures).toEqual(['amount'])
    expect(out.series[0].name).toBe('销售额')
  })

  it('raw 原样带出后端行集，供表格类图表直接使用', () => {
    const config = cfg([{ field: 'day' }], [{ field: 'amount', aggregation: 'sum' }])
    const rows = [{ day: '2026-01-01', sum_amount: 1 }]

    expect(DataAdapter.transform(config, result(rows)).raw).toEqual([{ day: '2026-01-01', sum_amount: 1 }])
  })

  it('候选链全部落空时按 m.field 取值，缺列即转成 null 而不是把 undefined 塞进系列', () => {
    const config = cfg([{ field: 'day' }], [{ field: 'amount', aggregation: 'sum' }])
    const out = DataAdapter.transform(config, result([{ day: '2026-01-01', other_col: 5 }]))

    expect(out.series[0].data).toEqual([null])
  })
})

describe('DataAdapter.transform —— 简单模式类目轴', () => {
  it('单维度时取第一个维度的 field 作为类目，且忽略前端 alias', () => {
    // given：SqlBuilder 的维度投影键是清洗后的物理列名，不是前端 alias
    const config = cfg([{ field: 'month', alias: '月份' }], [{ field: 'amount', aggregation: 'sum' }])
    const out = DataAdapter.transform(config, result([{ month: '2026-01', sum_amount: 5 }, { month: '2026-02', sum_amount: 6 }]))

    expect(out.dimensions).toEqual(['2026-01', '2026-02'])
    expect(out.series[0].data).toEqual([5, 6])
  })

  it('维度值为 null 时类目落成空串（走 String(r[dim] ?? "")），不影响数值对齐', () => {
    const config = cfg([{ field: 'day' }], [{ field: 'amount', aggregation: 'sum' }])
    const out = DataAdapter.transform(config, result([{ day: null, sum_amount: 5 }, { day: '2026-01-02', sum_amount: 6 }]))

    expect(out.dimensions).toEqual(['', '2026-01-02'])
  })

  it('没有维度时类目回落 1 起始的行号字符串，而不是 0 起始', () => {
    const config = cfg([], [{ field: 'amount', aggregation: 'sum' }])
    const rows = [{ sum_amount: 1 }, { sum_amount: 2 }, { sum_amount: 3 }]

    const out = DataAdapter.transform(config, result(rows))

    expect(out.dimensions).toEqual(['1', '2', '3'])
    expect(out.series[0].data).toEqual([1, 2, 3])
  })

  it('空 rows 时 dimensions 为空数组，且每个度量的 data 是 []（保留系列骨架）', () => {
    const config = cfg(
      [{ field: 'day' }],
      [
        { field: 'amount', aggregation: 'sum' },
        { field: 'qty', aggregation: 'count' },
      ],
    )

    const out = DataAdapter.transform(config, result([]))

    expect(out.dimensions).toEqual([])
    expect(out.series).toEqual([
      { name: 'amount', data: [] },
      { name: 'qty', data: [] },
    ])
  })

  it('rows 为 undefined 时按空集处理，不在入口就崩掉', () => {
    const config = cfg([{ field: 'day' }], [{ field: 'amount', aggregation: 'sum' }])

    const out = DataAdapter.transform(config, { columns: [], rows: undefined } as unknown as ReturnType<typeof result>)

    expect(out.dimensions).toEqual([])
    expect(out.raw).toEqual([])
  })

  it('度量单元格是字符串/NaN 时原样透传（不做数值化），只有 nullish 才转 null', () => {
    const config = cfg([{ field: 'day' }], [{ field: 'amount', aggregation: 'sum' }])
    const rows = [
      { day: 'a', sum_amount: '12' },
      { day: 'b', sum_amount: NaN },
      { day: 'c', sum_amount: null },
    ]

    expect(DataAdapter.transform(config, result(rows)).series[0].data).toEqual(['12', NaN, null])
  })
})

describe('DataAdapter.transform —— 透视模式（两个及以上维度）', () => {
  const pivotConfig = () =>
    cfg(
      [{ field: 'region' }, { field: 'channel' }],
      [{ field: 'amount', alias: '销售额', aggregation: 'sum' }],
    )

  const pivotRows = [
    { region: '华东', channel: '线上', sum_amount: 10 },
    { region: '华东', channel: '线下', sum_amount: 20 },
    { region: '华南', channel: '线上', sum_amount: 30 },
  ]

  it('最后一个维度作为系列维度，系列名格式是「<alias> - <系列值>」', () => {
    const out = DataAdapter.transform(pivotConfig(), result(pivotRows))

    expect(out.series.map((s) => s.name)).toEqual(['销售额 - 线上', '销售额 - 线下'])
    expect(out.dimensions).toEqual(['华东', '华南'])
  })

  it('类目/系列缺失的交叉格补 null 而不是 undefined，ECharts 才会断线而不是画 0', () => {
    const out = DataAdapter.transform(pivotConfig(), result(pivotRows))
    const offline = out.series[1].data

    expect(offline).toEqual([20, null])
    expect(offline[1]).toBeNull()
  })

  it('交叉格的 0 必须保留为 0，不能被 ?? 当成缺失', () => {
    const rows = [
      { region: '华东', channel: '线上', sum_amount: 0 },
      { region: '华东', channel: '线下', sum_amount: 5 },
    ]

    const out = DataAdapter.transform(pivotConfig(), result(rows))

    expect(out.series[0].data).toEqual([0])
    expect(out.series[1].data).toEqual([5])
  })

  it('多度量 × 多系列时按「度量优先」展开，每系列的 data 对齐类目轴', () => {
    const config = cfg(
      [{ field: 'region' }, { field: 'channel' }],
      [
        { field: 'amount', aggregation: 'sum' },
        { field: 'qty', aggregation: 'sum' },
      ],
    )
    const rows = [
      { region: '华东', channel: '线上', sum_amount: 10, sum_qty: 1 },
      { region: '华南', channel: '线下', sum_amount: 20, sum_qty: 2 },
    ]

    const out = DataAdapter.transform(config, result(rows))

    expect(out.series.map((s) => s.name)).toEqual(['amount - 线上', 'amount - 线下', 'qty - 线上', 'qty - 线下'])
    expect(out.series[0].data).toEqual([10, null])
    expect(out.series[3].data).toEqual([null, 2])
  })

  it('空 rows 的透视模式与简单模式给出同一种骨架：系列在、数据为空', () => {
    const out = DataAdapter.transform(pivotConfig(), result([]))

    // 修 API-138：原先空结果在两条路上分别是 series: [] 与 series: [{data: []}]，
    // "无数据"有两种形状等于让组件自己去猜，现在统一成"系列骨架在、数据为空"
    expect(out.dimensions).toEqual([])
    expect(out.series.length).toBeGreaterThan(0)
    out.series.forEach((s) => expect(s.data).toEqual([]))
  })

  it('透视模式下同一字段两种聚合各走各的桶键，不再互相覆盖（API-139 已修）', () => {
    // given：桶键原先是 `${物理字段}_${系列值}`，两个度量的物理字段相同就会撞车
    const config = cfg(
      [{ field: 'region' }, { field: 'channel' }],
      [
        { field: 'amount', aggregation: 'sum' },
        { field: 'amount', aggregation: 'avg' },
      ],
    )
    const rows = [{ region: '华东', channel: '线上', sum_amount: 10, avg_amount: 2 }]

    const out = DataAdapter.transform(config, result(rows))

    // then：与简单模式同形 —— sum 读 sum_amount、avg 读 avg_amount，且图例可区分
    expect(out.series.map((s) => s.name)).toEqual(['amount(sum) - 线上', 'amount(avg) - 线上'])
    expect(out.series[0].data).toEqual([10])
    expect(out.series[1].data).toEqual([2])
  })
})
