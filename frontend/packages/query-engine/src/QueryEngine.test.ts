import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { AnalysisQuery, QueryResult } from '@dataviz/shared-types'
import { executeQuery } from '@dataviz/api-client'
import { QueryEngine } from './QueryEngine'

vi.mock('@dataviz/api-client', () => ({ executeQuery: vi.fn() }))

const query = vi.mocked(executeQuery)

function q(over: Partial<AnalysisQuery> = {}): AnalysisQuery {
  return {
    datasetId: 1,
    dimensions: [{ field: 'day' }],
    measures: [{ field: 'amount', aggregation: 'sum' }],
    ...over,
  }
}

function res(tag: string): QueryResult {
  return { columns: [{ field: 'day', type: 'string' }], rows: [{ tag }], elapsed: 3, truncated: false }
}

describe('query-engine/QueryEngine —— 缓存与后端调用的对应关系', () => {
  beforeEach(() => {
    query.mockReset()
  })

  it('同参二次调用命中 LRU，executeQuery 只打一次且两次拿到同一份结果', async () => {
    query.mockResolvedValue(res('A'))
    const engine = new QueryEngine()

    const first = await engine.execute(q())
    const second = await engine.execute(q())

    expect(query).toHaveBeenCalledTimes(1)
    expect(first).toBe(second)
  })

  it('useCache=false 既不发缓存请求也不写缓存，后续默认调用仍要打后端', async () => {
    query.mockResolvedValue(res('A'))
    const engine = new QueryEngine()

    await engine.execute(q(), false)
    expect(query).toHaveBeenCalledTimes(1)

    await engine.execute(q(), false)
    expect(query).toHaveBeenCalledTimes(2)

    await engine.execute(q())
    expect(query).toHaveBeenCalledTimes(3)
  })

  it('limit 不同就是两个查询，不会串用缓存（分页/取前 N 的图最常见）', async () => {
    query.mockResolvedValue(res('A'))
    const engine = new QueryEngine()

    await engine.execute(q({ limit: 100 }))
    await engine.execute(q({ limit: 500 }))

    expect(query).toHaveBeenCalledTimes(2)
  })

  it('clearCache 之后同一查询重新打后端', async () => {
    query.mockResolvedValue(res('A'))
    const engine = new QueryEngine()

    await engine.execute(q())
    engine.clearCache()
    await engine.execute(q())

    expect(query).toHaveBeenCalledTimes(2)
  })

  it('executeBatch 里同参查询不做 in-flight 去重，几条并发就打后端几次（缺陷，按实际行为钉住）', async () => {
    query.mockResolvedValue(res('A'))
    const engine = new QueryEngine()

    const out = await engine.executeBatch([q(), q(), q({ datasetId: 2 })])

    // then：缓存要等首次请求 resolve 后才写入，并发批次全部落到后端
    expect(query).toHaveBeenCalledTimes(3)
    expect(out).toHaveLength(3)
  })

  it('串行执行完一次的查询再进批次时能命中缓存，批次只补打未缓存的那条', async () => {
    query.mockResolvedValue(res('A'))
    const engine = new QueryEngine()

    await engine.execute(q())
    query.mockClear()

    await engine.executeBatch([q(), q({ datasetId: 2 })])

    expect(query).toHaveBeenCalledTimes(1)
  })

  it('后端返回的 truncated 标志原样透传到结果上，前端才不会把截断当全量', async () => {
    query.mockResolvedValue({ columns: [], rows: [{ a: 1 }], elapsed: 1, truncated: true })
    const engine = new QueryEngine()

    expect((await engine.execute(q())).truncated).toBe(true)
  })

  it('mergeFilters 是追加而不是覆盖，且不改动传入的原查询对象', () => {
    const engine = new QueryEngine()
    const base = q({ filters: [{ field: 'region', operator: '=', value: '华东' }] })

    const merged = engine.mergeFilters(base, [{ field: 'day', operator: '>=', value: '2026-01-01' }])

    expect(merged.filters).toEqual([
      { field: 'region', operator: '=', value: '华东' },
      { field: 'day', operator: '>=', value: '2026-01-01' },
    ])
    expect(base.filters).toHaveLength(1)
  })

  it('parseFromVisualConfig 复用 QueryParser 的缺省口径（limit 1000、聚合 sum）', () => {
    const engine = new QueryEngine()

    expect(engine.parseFromVisualConfig({ datasetId: 7, dimensions: [{ field: 'day' }], measures: [{ field: 'qty' }] })).toEqual({
      datasetId: 7,
      dimensions: [{ field: 'day', alias: 'day' }],
      measures: [{ field: 'qty', alias: 'qty', aggregation: 'sum' }],
      filters: [],
      sorts: [],
      limit: 1000,
    })
  })
})
