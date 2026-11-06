import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { h } from 'vue'
import type { ChartConfig, QueryResult, ScreenComponent } from '@dataviz/shared-types'
import { ChartType } from '@dataviz/shared-types'
import { executeQuery } from '@dataviz/api-client'
import ScreenChart from './ScreenChart.vue'

/**
 * 取数依赖必须整体替掉：这里要验的是「失败」与「空结果」两种界面文案的区分度 ——
 * 之前两者都是静默空图，排查方向直接被带偏（后端 SQL 报错被当成"这个数据集没数据"）。
 */
vi.mock('@dataviz/api-client', () => ({ executeQuery: vi.fn() }))
vi.mock('@dataviz/chart-engine', () => ({
  ChartEngine: {
    name: 'ChartEngine',
    props: {
      config: { type: Object, default: () => ({}) },
      data: { type: Object, default: () => ({}) },
      width: { type: String, default: '' },
      height: { type: String, default: '' },
      theme: { type: String, default: '' },
      loading: { type: Boolean, default: false },
    },
    setup: (props: Record<string, unknown>) => () => h('div', { class: 'engine-stub', 'data-theme': String(props.theme) }),
  },
}))
vi.mock('../render/geo', () => ({ loadGeoMap: vi.fn().mockResolvedValue(undefined), geoAlreadyRegistered: () => false }))

const query = vi.mocked(executeQuery)

function barConfig(extra: Partial<ChartConfig> = {}): ChartConfig {
  return {
    chartType: ChartType.BAR,
    datasetId: 12,
    dimensions: [{ field: 'day', alias: '日期' }],
    measures: [{ field: 'amount', alias: '销售额', aggregation: 'sum' }],
    sorts: [{ field: 'day', order: 'desc' }],
    options: {},
    ...extra,
  } as ChartConfig
}

function makeComponent(props: Record<string, unknown> = {}, chartConfig?: ChartConfig): ScreenComponent {
  return {
    id: 'chart-1',
    type: 'chart',
    name: '图表',
    x: 0,
    y: 0,
    w: 400,
    h: 300,
    zIndex: 1,
    visible: true,
    locked: false,
    props,
    chartConfig: chartConfig ?? barConfig(),
  }
}

function okResult(rows: Record<string, unknown>[]): QueryResult {
  return { columns: [{ field: 'day', type: 'string' }, { field: 'sum_amount', type: 'number' }], rows, elapsed: 8 }
}

describe('ScreenChart —— 取数异常面与空结果面的区分', () => {
  beforeEach(() => {
    query.mockReset()
  })

  it('接口 reject 时把后端原文直接铺在图上：取数失败：<原文>', async () => {
    query.mockRejectedValue(new Error('SQL 语法错误：第 3 行 near "FORM"'))

    const wrapper = mount(ScreenChart, { props: { component: makeComponent() } })
    await flushPromises()

    expect(wrapper.find('.chart-error').text()).toBe('取数失败：SQL 语法错误：第 3 行 near "FORM"')
  })

  it('reject 的不是 Error 对象时退化成 String(e)，不出现 undefined', async () => {
    query.mockRejectedValue('后端连接超时')

    const wrapper = mount(ScreenChart, { props: { component: makeComponent() } })
    await flushPromises()

    expect(wrapper.find('.chart-error').text()).toBe('取数失败：后端连接超时')
  })

  it('HTTP 200 但 rows 为空时提示的是 0 行与行级权限，而不是取数失败', async () => {
    query.mockResolvedValue(okResult([]))

    const wrapper = mount(ScreenChart, { props: { component: makeComponent() } })
    await flushPromises()

    expect(wrapper.find('.chart-error').text()).toBe('查询返回 0 行（检查数据集与行级权限）')
  })

  it('正常返回非空 rows 时不渲染任何错误条，数据透传给 ChartEngine', async () => {
    query.mockResolvedValue(okResult([{ day: '2026-01-01', sum_amount: 12 }]))

    const wrapper = mount(ScreenChart, { props: { component: makeComponent() } })
    await flushPromises()

    expect(wrapper.find('.chart-error').exists()).toBe(false)
    expect(wrapper.find('.engine-stub').exists()).toBe(true)
  })

  it('先失败后成功（改配置重取）时上一次的错误条必须消失，不能挂着旧错误', async () => {
    query.mockRejectedValueOnce(new Error('字段不存在: amout'))

    const wrapper = mount(ScreenChart, { props: { component: makeComponent() } })
    await flushPromises()
    expect(wrapper.find('.chart-error').text()).toBe('取数失败：字段不存在: amout')

    query.mockResolvedValueOnce(okResult([{ day: '2026-01-01', sum_amount: 1 }]))
    await wrapper.setProps({ component: makeComponent({}, barConfig({ title: '改了标题' })) })
    await flushPromises()

    expect(wrapper.find('.chart-error').exists()).toBe(false)
  })

  it('请求体带上 alias/aggregation 与 sorts —— 少了 sorts 排行类图表会直接倒序', async () => {
    query.mockResolvedValue(okResult([{ day: '2026-01-01', sum_amount: 1 }]))

    mount(ScreenChart, { props: { component: makeComponent() } })
    await flushPromises()

    expect(query).toHaveBeenCalledTimes(1)
    expect(query.mock.calls[0][0]).toEqual({
      datasetId: 12,
      dimensions: [{ field: 'day', alias: '日期' }],
      measures: [{ field: 'amount', alias: '销售额', aggregation: 'sum' }],
      sorts: [{ field: 'day', order: 'desc' }],
    })
  })

  it('static 数据源不发请求，行数为 0 也不报 "查询返回 0 行"', async () => {
    const component = makeComponent()
    component.request = {
      sourceType: 'static',
      staticData: { columns: [{ field: 'day', type: 'string' }], rows: [] },
    }

    const wrapper = mount(ScreenChart, { props: { component } })
    await flushPromises()

    expect(query).toHaveBeenCalledTimes(0)
    expect(wrapper.find('.chart-error').exists()).toBe(false)
  })

  it('未配置 datasetId 时既不发请求也不报错，避免把"没配数据源"说成"取数失败"', async () => {
    query.mockResolvedValue(okResult([{ day: '2026-01-01', sum_amount: 1 }]))

    const wrapper = mount(ScreenChart, { props: { component: makeComponent({}, barConfig({ datasetId: undefined })) } })
    await flushPromises()

    expect(query).toHaveBeenCalledTimes(0)
    expect(wrapper.find('.chart-error').exists()).toBe(false)
  })
})
