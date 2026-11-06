/**
 * 防抖节流工具
 */

export type Fn = (...args: unknown[]) => unknown

/**
 * 防抖
 */
export function debounce<T extends Fn>(fn: T, delay = 300): (...args: Parameters<T>) => void {
  let timer: ReturnType<typeof setTimeout> | null = null
  return function (this: unknown, ...args: Parameters<T>) {
    if (timer) clearTimeout(timer)
    timer = setTimeout(() => {
      fn.apply(this, args)
      timer = null
    }, delay)
  }
}

/**
 * 节流
 */
export function throttle<T extends Fn>(fn: T, interval = 200): (...args: Parameters<T>) => void {
  let last = 0
  let timer: ReturnType<typeof setTimeout> | null = null
  // 记录静默期内最后一次收到的入参，补发定时器触发时用它，而不是挂定时器那一次的（API-147）。
  let pendingArgs: Parameters<T> | null = null
  let pendingThis: unknown
  return function (this: unknown, ...args: Parameters<T>) {
    const now = Date.now()
    const remaining = interval - (now - last)
    if (remaining <= 0) {
      if (timer) {
        clearTimeout(timer)
        timer = null
        pendingArgs = null
      }
      last = now
      fn.apply(this, args)
    } else {
      pendingArgs = args
      pendingThis = this
      if (!timer) {
        timer = setTimeout(() => {
          last = Date.now()
          timer = null
          const trailing = pendingArgs ?? args
          const trailingThis = pendingThis
          pendingArgs = null
          pendingThis = undefined
          fn.apply(trailingThis, trailing)
        }, remaining)
      }
    }
  }
}

/**
 * 延迟执行 (Promise)
 */
export function delay(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms))
}
