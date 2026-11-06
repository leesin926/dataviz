import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { clearLocal, getSession, getLocal, removeLocal, removeSession, setLocal, setSession } from './storage'

/** 可控的 localStorage/sessionStorage 替身：能看落盘原文，也能按需抛异常 */
class MemoryStorage {
  store = new Map<string, string>()
  throwOnSet = false

  get length(): number {
    return this.store.size
  }

  key(index: number): string | null {
    return Array.from(this.store.keys())[index] ?? null
  }

  getItem(key: string): string | null {
    return this.store.has(key) ? (this.store.get(key) as string) : null
  }

  setItem(key: string, value: string): void {
    if (this.throwOnSet) throw new Error('QuotaExceededError')
    this.store.set(key, String(value))
  }

  removeItem(key: string): void {
    this.store.delete(key)
  }

  clear(): void {
    this.store.clear()
  }
}

let local: MemoryStorage
let session: MemoryStorage

function install() {
  local = new MemoryStorage()
  session = new MemoryStorage()
  Object.defineProperty(window, 'localStorage', { value: local, configurable: true, writable: true })
  Object.defineProperty(window, 'sessionStorage', { value: session, configurable: true, writable: true })
}

describe('shared-utils/storage —— localStorage 读写', () => {
  beforeEach(() => {
    install()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('setLocal 落盘的键带 dataviz_ 前缀，值包在 { value } 信封里', () => {
    setLocal('user', { id: 7, name: '钟帷幄' })

    expect(local.store.get('dataviz_user')).toBe('{"value":{"id":7,"name":"钟帷幄"}}')
    expect(getLocal<{ id: number; name: string }>('user')).toEqual({ id: 7, name: '钟帷幄' })
  })

  it('数字、空串与 null 都能原样往返，不会把 0 变成 null', () => {
    setLocal('n', 0)
    setLocal('s', '')
    setLocal('z', null)

    expect(getLocal<number>('n')).toBe(0)
    expect(getLocal<string>('s')).toBe('')
    expect(getLocal<null>('z')).toBeNull()
  })

  it('未过期的 ttl 条目照常返回原值', () => {
    vi.useFakeTimers({ now: 1_700_000_000_000 })
    setLocal('token', 'abc', 60_000)

    expect(getLocal<string>('token')).toBe('abc')
    expect(local.store.get('dataviz_token')).toContain('"expireAt":1700000060000')
  })

  it('ttl 到期后返回 null 并把键顺手删掉（不留过期数据）', () => {
    vi.useFakeTimers({ now: 1_700_000_000_000 })
    setLocal('token', 'abc', 60_000)
    vi.advanceTimersByTime(60_001)

    expect(getLocal<string>('token')).toBeNull()
    expect(local.store.has('dataviz_token')).toBe(false)
  })

  it('落盘原文不是 JSON 时返回 null，而不是把异常抛给调用方', () => {
    local.store.set('dataviz_broken', '{')

    expect(getLocal('broken')).toBeNull()
  })

  it('裸值（不带 value 信封）的历史数据按 corrupt 处理返回 null，而不是 undefined（API-149）', () => {
    // given：老版本或其他系统直接 setItem('dataviz_x', '"abc"')
    local.store.set('dataviz_legacy', JSON.stringify('abc'))

    expect(getLocal('legacy')).toBeNull()
  })

  it('setItem 抛异常（配额超限）时 setLocal 只打一条 console.error，读回来仍是 null', () => {
    const err = vi.spyOn(console, 'error').mockImplementation(() => undefined)
    local.throwOnSet = true

    setLocal('big', { a: 1 })

    expect(getLocal('big')).toBeNull()
    expect(err).toHaveBeenCalledTimes(1)
    expect(err.mock.calls[0][0]).toBe('[storage] localStorage set failed: key=big')
  })

  it('removeLocal 只删指定键，clearLocal 只清 dataviz_ 前缀键、保留第三方键', () => {
    setLocal('a', 1)
    setLocal('b', 2)
    local.store.set('other_vendor_key', 'keep me')

    removeLocal('a')
    expect(getLocal('a')).toBeNull()
    expect(getLocal('b')).toBe(2)

    clearLocal()
    expect(getLocal('b')).toBeNull()
    expect(local.store.get('other_vendor_key')).toBe('keep me')
  })
})

describe('shared-utils/storage —— sessionStorage 读写', () => {
  beforeEach(() => {
    install()
  })

  it('setSession 直接 JSON 化业务值（不套 value 信封），getSession 原样取回', () => {
    setSession('wizard', { step: 3 })

    expect(session.store.get('dataviz_wizard')).toBe('{"step":3}')
    expect(getSession<{ step: number }>('wizard')).toEqual({ step: 3 })
  })

  it('getSession 缺键返回 null，坏 JSON 也返回 null；removeSession 能定位带前缀的键', () => {
    expect(getSession('missing')).toBeNull()

    setSession('x', 1)
    removeSession('x')
    expect(getSession('x')).toBeNull()

    session.store.set('dataviz_y', 'not json')
    expect(getSession('y')).toBeNull()
  })
})
