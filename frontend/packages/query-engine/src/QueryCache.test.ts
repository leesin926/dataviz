import { describe, expect, it } from 'vitest'
import type { AnalysisQuery, QueryResult } from '@dataviz/shared-types'
import { QueryCache } from './QueryCache'

function q(over: Partial<AnalysisQuery> = {}): AnalysisQuery {
  return {
    datasetId: 1,
    dimensions: [{ field: 'day' }],
    measures: [{ field: 'amount', aggregation: 'sum' }],
    ...over,
  }
}

function r(tag: string): QueryResult {
  return { columns: [{ field: 'day', type: 'string' }], rows: [{ tag }], elapsed: 1 }
}

describe('query-engine/QueryCache —— 键与 LRU 行为', () => {
  it('buildKey 覆盖 AnalysisQuery 的全部口径字段，limit/offset 变化不会撞键', () => {
    const cache = new QueryCache()

    expect(cache.buildKey(q({ limit: 100 }))).not.toBe(cache.buildKey(q({ limit: 200 })))
    expect(cache.buildKey(q({ datasetId: 2 }))).not.toBe(cache.buildKey(q({ datasetId: 1 })))
    expect(cache.buildKey(q({ offset: 50 }))).not.toBe(cache.buildKey(q()))
  })

  it('buildKey 与属性书写顺序无关，内容同形即同键', () => {
    const cache = new QueryCache()
    const a = { datasetId: 1, dimensions: [{ field: 'day' }], measures: [{ field: 'amount', aggregation: 'sum' as const }] }
    const b = { measures: [{ field: 'amount', aggregation: 'sum' as const }], dimensions: [{ field: 'day' }], datasetId: 1 }

    expect(cache.buildKey(a as AnalysisQuery)).toBe(cache.buildKey(b as AnalysisQuery))
  })

  it('get 命中返回原结果对象，未命中返回 null 而不是 undefined', () => {
    const cache = new QueryCache()
    const hit = r('A')

    cache.set('k1', hit)

    expect(cache.get('k1')).toBe(hit)
    expect(cache.get('nope')).toBeNull()
  })

  it('同键重复 set 覆盖值且不涨 size', () => {
    const cache = new QueryCache()

    cache.set('k1', r('old'))
    cache.set('k1', r('new'))

    expect(cache.size()).toBe(1)
    expect(cache.get('k1')).toEqual(r('new'))
  })

  it('满员时淘汰最久未用的条目，而 get 命中会给条目续命', () => {
    // given：容量 2
    const cache = new QueryCache(2)
    cache.set('a', r('A'))
    cache.set('b', r('B'))

    // when：读一次 a 再塞第三条
    cache.get('a')
    cache.set('c', r('C'))

    // then：被淘汰的是 b
    expect(cache.size()).toBe(2)
    expect(cache.has('a')).toBe(true)
    expect(cache.has('b')).toBe(false)
    expect(cache.has('c')).toBe(true)
  })

  it('delete 摘掉单条，clear 清空并让 size 归零', () => {
    const cache = new QueryCache()
    cache.set('a', r('A'))
    cache.set('b', r('B'))

    cache.delete('a')
    expect(cache.has('a')).toBe(false)
    expect(cache.size()).toBe(1)

    cache.clear()
    expect(cache.size()).toBe(0)
    expect(cache.get('b')).toBeNull()
  })
})
