import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { AnalysisQuery } from '@dataviz/shared-types'
import { exportQueryResult, executeQuery } from './analysis'

const { mockPost } = vi.hoisted(() => ({ mockPost: vi.fn() }))

/** axios 实例整体替掉：这里只关心前端字段口径 → 后端 DTO 的翻译与响应换形 */
vi.mock('../request', () => ({ request: { post: mockPost, get: vi.fn() } }))

const QUERY: AnalysisQuery = {
  datasetId: '7',
  dimensions: [{ field: 'day', alias: '日期' }],
  measures: [{ field: 'amount', alias: '销售额', aggregation: 'sum' }],
  filters: [{ field: 'region', operator: 'in', value: ['华东'] }],
  sorts: [{ field: 'day', order: 'desc' }],
  limit: 1000,
}

function envelope<T>(data: T) {
  return { code: 0, message: 'ok', data }
}

function mockVo(vo: unknown) {
  mockPost.mockResolvedValue({ data: envelope(vo) })
}

describe('api-client/analysis.executeQuery —— 前端口径译成后端 DTO', () => {
  beforeEach(() => {
    mockPost.mockReset()
  })

  it('走 /analysis/query/execute，measures→metrics、aggregation→aggFunction、sorts→orders/direction', async () => {
    mockVo({ columns: [], rows: [] })

    await executeQuery(QUERY)

    expect(mockPost.mock.calls[0][0]).toBe('/analysis/query/execute')
    expect(mockPost.mock.calls[0][1]).toEqual({
      datasetId: '7',
      dimensions: [{ field: 'day' }],
      metrics: [{ field: 'amount', aggFunction: 'sum', alias: '销售额' }],
      filters: [{ field: 'region', operator: 'in', value: ['华东'] }],
      orders: [{ field: 'day', direction: 'desc' }],
      limit: 1000,
    })
  })

  it('日期粒度必须透传给后端：带 dateFormat 的下发该键，没带的不补空（API-140 已修）', async () => {
    mockVo({ columns: [], rows: [] })

    await executeQuery({
      ...QUERY,
      dimensions: [
        { field: 'created_at', dateFormat: 'month' },
        { field: 'region' },
        { field: 'day', alias: '日期' },
      ],
    })

    // then：后端 SqlBuilder 靠 dateFormat 白名单做 DATE_FORMAT 分桶，
    // 这一段此前只发 {field}，等于界面选了粒度也当没选（DTO 类型早就声明了这个字段）
    expect(mockPost.mock.calls[0][1].dimensions).toEqual([
      { field: 'created_at', dateFormat: 'month' },
      { field: 'region' },
      { field: 'day' },
    ])
  })

  it('columns 是字符串数组时按首行样本推类型：date / number / boolean / string', async () => {
    mockVo({
      columns: ['day', 'amount', 'flag', 'region'],
      rows: [{ day: '2026-01-01', amount: 5, flag: true, region: '华东' }],
      rowCount: 1,
      executionTime: 12,
      sql: 'SELECT 1',
    })

    expect(await executeQuery(QUERY)).toEqual({
      columns: [
        { field: 'day', type: 'date' },
        { field: 'amount', type: 'number' },
        { field: 'flag', type: 'boolean' },
        { field: 'region', type: 'string' },
      ],
      rows: [{ day: '2026-01-01', amount: 5, flag: true, region: '华东' }],
      sql: 'SELECT 1',
      elapsed: 12,
      total: 1,
      truncated: false,
    })
  })

  it('truncated 缺省时补成 false，而不是 undefined（下游 if (res.truncated) 才有确定语义）', async () => {
    mockVo({ columns: ['a'], rows: [{ a: 1 }] })

    const out = await executeQuery(QUERY)

    expect(out.truncated).toBe(false)
    expect('elapsed' in out).toBe(true)
  })

  it('后端标记 truncated 时如实透传，前端才不会把前 N 行当全量', async () => {
    mockVo({ columns: ['a'], rows: [{ a: 1 }], truncated: true })

    expect((await executeQuery(QUERY)).truncated).toBe(true)
  })

  it('columns/rows 为 null 时回落成空数组，elapsed 缺省 0、total 缺省 undefined', async () => {
    mockVo({ columns: null, rows: null })

    expect(await executeQuery(QUERY)).toEqual({ columns: [], rows: [], sql: undefined, elapsed: 0, total: undefined, truncated: false })
  })
})

describe('api-client/analysis.exportQueryResult —— 导出行数与截断可见', () => {
  beforeEach(() => {
    mockPost.mockReset()
  })

  it('成功导出时按响应头给出 rowCount 与 truncated，文件名按格式兜底', async () => {
    const blob = { type: '' } as unknown as Blob
    mockPost.mockResolvedValue({ data: blob, headers: { 'x-export-rows': '1203', 'x-export-truncated': 'true' } })

    const out = await exportQueryResult(QUERY, { format: 'csv' })

    expect(out.fileName).toBe('export.csv')
    expect(out.rowCount).toBe(1203)
    expect(out.truncated).toBe(true)
    expect(out.blob).toBe(blob)
    expect(mockPost.mock.calls[0][0]).toBe('/analysis/export/csv')
  })

  it('响应头缺省时 rowCount 是 0、truncated 是 false，不写文件名则回落 xlsx', async () => {
    mockPost.mockResolvedValue({ data: { type: '' } as unknown as Blob, headers: {} })

    const out = await exportQueryResult(QUERY)

    expect(out.fileName).toBe('export.xlsx')
    expect(out.rowCount).toBe(0)
    expect(out.truncated).toBe(false)
  })

  it('失败时 blob 里装的是 JSON 错误体，直接把后端 message 原文抛出而不是回一个坏文件', async () => {
    const body = JSON.stringify({ message: '权限不足：缺少数据集 7 的读权限' })
    mockPost.mockResolvedValue({ data: { type: 'application/json', text: () => Promise.resolve(body) } as unknown as Blob, headers: {} })

    await expect(exportQueryResult(QUERY)).rejects.toThrow('权限不足：缺少数据集 7 的读权限')
  })

  it('错误体里没有 message 时抛出空串，文案交给界面按自己的 locale 兜底', async () => {
    mockPost.mockResolvedValue({
      data: { type: 'application/json', text: () => Promise.resolve('<html>502</html>') } as unknown as Blob,
      headers: {},
    })

    await expect(exportQueryResult(QUERY).catch((e: Error) => e.message)).resolves.toBe('')
  })
})
