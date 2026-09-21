import { onBeforeUnmount, onMounted } from 'vue'

/** 组件 props 取值：尺寸统一按 px 输出（历史配置可能存 '18px'，新默认为数字） */
export function pxOf(value: unknown, fallback: number): string {
  if (typeof value === 'number' && Number.isFinite(value)) return `${value}px`
  if (typeof value === 'string' && value.trim()) return value.trim()
  return `${fallback}px`
}

export function numOf(value: unknown, fallback: number): number {
  if (typeof value === 'number' && Number.isFinite(value)) return value
  const n = Number(value)
  return Number.isFinite(n) ? n : fallback
}

export function strOf(value: unknown, fallback = ''): string {
  return typeof value === 'string' && value.trim() ? value.trim() : fallback
}

export function boolOf(value: unknown, fallback: boolean): boolean {
  return typeof value === 'boolean' ? value : fallback
}

/** 数值千分位（非数字原样返回） */
export function groupNumber(n: number, group: boolean): string {
  if (!group || !Number.isFinite(n)) return String(n)
  const [int, dec] = String(n).split('.')
  const withSep = int.replace(/\B(?=(\d{3})+(?!\d))/g, ',')
  return dec ? `${withSep}.${dec}` : withSep
}

/** 秒级定时器：组件卸载即回收 */
export function useInterval(fn: () => void, ms: number): void {
  let timer: ReturnType<typeof setInterval> | null = null
  onMounted(() => {
    fn()
    timer = setInterval(fn, ms)
  })
  onBeforeUnmount(() => {
    if (timer) clearInterval(timer)
    timer = null
  })
}
