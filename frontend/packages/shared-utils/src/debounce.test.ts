import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { debounce, delay, throttle } from './debounce'

describe('shared-utils/debounce —— 尾部触发与重新计时', () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })
  afterEach(() => {
    vi.useRealTimers()
  })

  it('连续调用只在静默期后触发一次，且带的是最后一次入参', () => {
    // given：筛选框连敲三个字符，只应该发最后一次请求
    const spy = vi.fn()
    const run = debounce(spy, 300)

    run('a')
    run('ab')
    run('abc')
    vi.advanceTimersByTime(300)

    expect(spy).toHaveBeenCalledTimes(1)
    expect(spy.mock.calls[0]).toEqual(['abc'])
  })

  it('静默期未满不触发：299ms 时 0 次，补满 1ms 才 1 次', () => {
    const spy = vi.fn()
    const run = debounce(spy, 300)

    run(1)
    vi.advanceTimersByTime(299)
    expect(spy).toHaveBeenCalledTimes(0)

    vi.advanceTimersByTime(1)
    expect(spy).toHaveBeenCalledTimes(1)
  })

  it('每次调用都重置计时器：跨 200ms 后再调用，距第一次调用 400ms 仍未触发', () => {
    const spy = vi.fn()
    const run = debounce(spy, 300)

    run(1)
    vi.advanceTimersByTime(200)
    run(2)
    vi.advanceTimersByTime(200)
    expect(spy).toHaveBeenCalledTimes(0)

    vi.advanceTimersByTime(100)
    expect(spy).toHaveBeenCalledTimes(1)
    expect(spy.mock.calls[0]).toEqual([2])
  })

  it('delay 缺省是 300ms，多实例之间互不影响', () => {
    const a = vi.fn()
    const b = vi.fn()
    const runA = debounce(a)
    const runB = debounce(b, 100)

    runA(1)
    runB(2)
    vi.advanceTimersByTime(100)
    expect(b).toHaveBeenCalledTimes(1)
    expect(a).toHaveBeenCalledTimes(0)

    vi.advanceTimersByTime(200)
    expect(a).toHaveBeenCalledTimes(1)
    expect(a.mock.calls[0]).toEqual([1])
  })
})

describe('shared-utils/debounce —— 节流与延时', () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })
  afterEach(() => {
    vi.useRealTimers()
  })

  it('throttle 静默期内只有第一次调用能挂上补发定时器，后续入参被丢弃（缺陷，按实际行为钉住）', () => {
    const spy = vi.fn()
    const run = throttle(spy, 200)

    run('first')
    expect(spy).toHaveBeenCalledTimes(1)
    expect(spy.mock.calls[0]).toEqual(['first'])

    // when：50ms 后来的两次，第二次挂不上定时器
    vi.advanceTimersByTime(50)
    run('second')
    run('third')
    expect(spy).toHaveBeenCalledTimes(1)

    vi.advanceTimersByTime(150)
    expect(spy).toHaveBeenCalledTimes(2)
    // then：补发的是 second 而不是最后一次的 third —— 与 debounce 的尾部语义相反
    expect(spy.mock.calls[1]).toEqual(['second'])
  })

  it('throttle 超过间隔后再次调用会立刻执行，不排队', () => {
    const spy = vi.fn()
    const run = throttle(spy, 200)

    run(1)
    vi.advanceTimersByTime(250)
    run(2)

    expect(spy).toHaveBeenCalledTimes(2)
    expect(spy.mock.calls[1]).toEqual([2])
  })

  it('delay 到点才 resolve：499ms 时仍是挂起状态', async () => {
    let settled = false
    const p = delay(500).then(() => {
      settled = true
    })

    vi.advanceTimersByTime(499)
    expect(settled).toBe(false)

    vi.advanceTimersByTime(1)
    await p
    expect(settled).toBe(true)
  })
})
