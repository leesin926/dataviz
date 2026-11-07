import { describe, expect, it } from 'vitest'

import type { PageResult } from './api'
import { isTruncated, isWindowDefaulted, rowsOf, shardsOf, totalOf } from './api'

/**
 * 分页形状的三个取值器是"后端两种口径 + 分片三种新字段"的唯一收口点。
 * 这里钉的是读错字段的代价：三个 flag 缺失时必须解释成"没截断 / 没补窗口 / 没分片"，
 * 因为单库路径压根不下发它们。
 */
describe('PageResult 取值器', () => {
  it('records 与 list 两种口径都吃，records 优先', () => {
    expect(rowsOf({ records: [1, 2], list: [3], total: 2 })).toEqual([1, 2])
    expect(rowsOf({ list: [3], total: 1 })).toEqual([3])
    expect(rowsOf({ total: 0 })).toEqual([])
  })

  it('直接给数组或空值时不炸', () => {
    expect(rowsOf([7, 8])).toEqual([7, 8])
    expect(rowsOf(null)).toEqual([])
    expect(rowsOf(undefined)).toEqual([])
    expect(totalOf(null)).toBe(0)
    expect(totalOf([1, 2, 3])).toBe(3)
    expect(totalOf({ records: [], total: 42 })).toBe(42)
  })

  it('只有显式 true 才算截断；缺字段与 false 都是"这一页是完整的"', () => {
    const base: PageResult<number> = { records: [1], total: 1 }
    expect(isTruncated({ ...base, truncated: true })).toBe(true)
    expect(isTruncated({ ...base, truncated: false })).toBe(false)
    // 单库路径（shard.enabled=false）压根不下发这个字段，读成截断就是凭空报警
    expect(isTruncated(base)).toBe(false)
    expect(isTruncated(null)).toBe(false)
  })

  it('时间窗由服务端补的时候要显式说出来', () => {
    const base: PageResult<number> = { records: [1], total: 1 }
    expect(isWindowDefaulted({ ...base, windowDefaulted: true })).toBe(true)
    expect(isWindowDefaulted({ ...base, windowDefaulted: false })).toBe(false)
    expect(isWindowDefaulted(base)).toBe(false)
  })

  it('分片数缺失给 0，绝不返回 undefined 让页面画出"共 undefined 片"', () => {
    const base: PageResult<number> = { records: [1], total: 1 }
    expect(shardsOf({ ...base, shards: 3 })).toBe(3)
    expect(shardsOf(base)).toBe(0)
    expect(shardsOf(null)).toBe(0)
  })
})
