import { describe, expect, it } from 'vitest'
import { asPage, mapPage, unwrap } from './unwrap'

/**
 * 后端信封 R 的口径：`{ code, message, data }`，成功码是 0 和 200 两种；
 * 另有 dashboard-service 直接回裸对象/裸 Page。unwrap 只看"是不是信封形状"，
 * 不看 code 的取值 —— 业务错误码的拦截责任在 request.ts 的响应拦截器（第 60 行）。
 */
describe('api-client/unwrap —— 信封拆包', () => {
  it('code=0 与 code=200 都只取 data，业务对象原样返回', () => {
    expect(unwrap({ code: 0, message: 'ok', data: { id: 7, name: '大屏A' } })).toEqual({ id: 7, name: '大屏A' })
    expect(unwrap({ code: 200, message: 'ok', data: [1, 2, 3] })).toEqual([1, 2, 3])
  })

  it('data 为 null / 空串 / 0 时如实返回该值，不做真值判断', () => {
    expect(unwrap({ code: 0, message: 'ok', data: null })).toBeNull()
    expect(unwrap({ code: 0, message: 'ok', data: '' })).toBe('')
    expect(unwrap({ code: 0, message: 'ok', data: 0 })).toBe(0)
  })

  it('信封里没有 data 键时整包原样返回（bare 响应兼容），不会被拆成 undefined', () => {
    const bare = { code: 0, message: 'ok' }

    expect(unwrap(bare)).toBe(bare)
  })

  it('code 不是数字（网关回 "0" 字符串）时不按信封处理，整包返回', () => {
    const payload = { code: '0', message: 'ok', data: { a: 1 } }

    expect(unwrap(payload)).toBe(payload)
  })

  it('非 0/200 的业务错误码不看：只要带 data 键就照样把 data 当业务数据返回（缺陷，按实际行为钉住）', () => {
    // given：SQL 报错时后端仍可能带一个空 data 壳，绕过拦截器直连的服务会走到这里
    expect(unwrap({ code: 5000, message: 'SQL 执行失败', data: { rows: [] } })).toEqual({ rows: [] })
    expect(unwrap({ code: 500, message: '内部错误', data: null })).toBeNull()
  })

  it('非 JSON 响应体（字符串/数字）与 null / undefined 一律原样透传', () => {
    expect(unwrap('<html>502 Bad Gateway</html>')).toBe('<html>502 Bad Gateway</html>')
    expect(unwrap(42)).toBe(42)
    expect(unwrap(null)).toBeNull()
    expect(unwrap(undefined)).toBeUndefined()
  })
})

describe('api-client/unwrap.asPage —— 三套分页口径归一', () => {
  it('MyBatis-Plus Page：records/current/size/pages 映射到 records/pageNum/pageSize/pages', () => {
    const out = asPage<{ id: number }>({
      records: [{ id: 1 }, { id: 2 }],
      total: 57,
      current: 3,
      size: 20,
      pages: 3,
    })

    expect(out).toEqual({ records: [{ id: 1 }, { id: 2 }], total: 57, pageNum: 3, pageSize: 20, pages: 3 })
  })

  it('screen-service 口径 list/pages 也能读出，total 缺省时回落成行长度', () => {
    const out = asPage<{ id: number }>({ list: [{ id: 9 }], pages: 1 })

    expect(out.records).toEqual([{ id: 9 }])
    expect(out.total).toBe(1)
    expect(out.pageNum).toBe(1)
    expect(out.pageSize).toBe(1)
    expect(out.pages).toBe(1)
  })

  it('records 与 list 同时存在时 records 优先（common-core 口径胜出）', () => {
    const out = asPage<string>({ records: ['from-records'], list: ['from-list'], total: 2 })

    expect(out.records).toEqual(['from-records'])
    expect(out.total).toBe(2)
  })

  it('裸数组按整页处理：total/pageSize 都取长度，pages 固定 1', () => {
    expect(asPage<number>([5, 6, 7])).toEqual({ records: [5, 6, 7], total: 3, pageNum: 1, pageSize: 3, pages: 1 })
  })

  it('null / undefined 归一成空页而不是抛错，调用方可以直接读 records', () => {
    expect(asPage(null)).toEqual({ records: [], total: 0 })
    expect(asPage(undefined)).toEqual({ records: [], total: 0 })
  })

  it('信封包裹的分页对象先拆包再归一', () => {
    const out = asPage<number>({ code: 0, message: 'ok', data: { records: [1, 2], total: 2, current: 1, size: 10, pages: 1 } })

    expect(out).toEqual({ records: [1, 2], total: 2, pageNum: 1, pageSize: 10, pages: 1 })
  })

  it('totalPages 落到 pages，空 records 的 total 缺省为 0', () => {
    const out = asPage<number>({ records: [], total: 0, totalPages: 4 })

    expect(out.pages).toBe(4)
    expect(out.pageSize).toBe(0)
  })
})

describe('api-client/unwrap.mapPage —— 换形保留分页元信息', () => {
  it('只替换行数据，total/pageNum/pageSize/pages 原样保留', () => {
    const page = asPage<number>({ records: [1, 2, 3], total: 99, current: 2, size: 3, pages: 33 })

    expect(mapPage(page, (n) => n * 10)).toEqual({ records: [10, 20, 30], total: 99, pageNum: 2, pageSize: 3, pages: 33 })
  })

  it('list 口径换形后不再带 list 键，避免旧元素类型的行泄漏给调用方', () => {
    const page = asPage<number>({ list: [1, 2], total: 2 })
    const mapped = mapPage(page, (n) => String(n))

    expect(mapped.records).toEqual(['1', '2'])
    expect(mapped.list).toBeUndefined()
  })

  it('裸数组页面换形后仍是一个完整分页对象', () => {
    expect(mapPage(asPage<number>([1, 2]), (n) => n + 1)).toEqual({ records: [2, 3], total: 2, pageNum: 1, pageSize: 2, pages: 1 })
  })
})
