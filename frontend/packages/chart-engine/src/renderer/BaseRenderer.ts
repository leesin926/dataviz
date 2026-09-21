import type { ChartConfig } from '@dataviz/shared-types'

export type EventType = 'click' | 'dblclick' | 'mouseover' | 'mouseout' | 'brush' | 'brushEnd'
type Listener = (payload: unknown) => void

export interface ChartData {
  dimensions: string[]
  measures: string[]
  series: Array<{
    name: string
    data: unknown[]
  }>
  raw: Record<string, unknown>[]
}

/**
 * 基础渲染器类
 */
export abstract class BaseRenderer {
  protected container: HTMLElement
  protected listeners: Map<EventType, Listener[]> = new Map()
  protected isLoading = false

  constructor(container: HTMLElement) {
    this.container = container
  }

  abstract render(config: ChartConfig, data: ChartData): void
  abstract dispose(): void

  resize?(): void

  setLoading(loading: boolean): void {
    this.isLoading = loading
  }

  exportImage?(type?: string): string | null

  on(event: EventType, listener: Listener): void {
    if (!this.listeners.has(event)) {
      this.listeners.set(event, [])
    }
    this.listeners.get(event)!.push(listener)
  }

  off(event: EventType, listener: Listener): void {
    const arr = this.listeners.get(event)
    if (!arr) return
    const idx = arr.indexOf(listener)
    if (idx >= 0) arr.splice(idx, 1)
  }

  protected emit(event: EventType, payload: unknown): void {
    const arr = this.listeners.get(event)
    if (!arr) return
    for (const l of arr) {
      try {
        l(payload)
      } catch (e) {
        console.error(`[BaseRenderer] listener error: ${event}`, e)
      }
    }
  }

  clear(): void {
    this.listeners.clear()
  }
}
