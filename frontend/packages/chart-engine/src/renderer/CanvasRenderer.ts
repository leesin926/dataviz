import { BaseRenderer, type ChartData } from './BaseRenderer'
import type { ChartConfig } from '@dataviz/shared-types'
import { ChartType } from '@dataviz/shared-types'
import { renderKpiCard } from '../charts/kpi-card'
import { renderTable } from '../charts/table'

/**
 * 自定义 Canvas 渲染器 (用于非 ECharts 图表)
 */
export class CanvasRenderer extends BaseRenderer {
  private canvas: HTMLCanvasElement | null = null
  private ctx: CanvasRenderingContext2D | null = null
  private observer: ResizeObserver | null = null

  constructor(container: HTMLElement) {
    super(container)
    this.canvas = document.createElement('canvas')
    this.canvas.style.width = '100%'
    this.canvas.style.height = '100%'
    container.appendChild(this.canvas)
    this.ctx = this.canvas.getContext('2d')
    this.observer = new ResizeObserver(() => this.resize())
    this.observer.observe(container)
  }

  render(config: ChartConfig, data: ChartData): void {
    if (!this.ctx || !this.canvas) return
    this.syncSize()
    this.ctx.clearRect(0, 0, this.canvas.width, this.canvas.height)
    if (config.chartType === ChartType.KPI_CARD) {
      renderKpiCard(this.ctx, this.canvas.width, this.canvas.height, config, data)
    } else if (config.chartType === ChartType.TABLE) {
      renderTable(this.ctx, this.canvas.width, this.canvas.height, config, data)
    }
  }

  resize(): void {
    this.syncSize()
    // 重新渲染由调用方通过 render 触发
  }

  private syncSize(): void {
    if (!this.canvas || !this.container) return
    const rect = this.container.getBoundingClientRect()
    const dpr = window.devicePixelRatio || 1
    this.canvas.width = rect.width * dpr
    this.canvas.height = rect.height * dpr
    if (this.ctx) this.ctx.scale(dpr, dpr)
  }

  exportImage(type = 'png'): string | null {
    return this.canvas?.toDataURL(`image/${type}`) ?? null
  }

  dispose(): void {
    this.observer?.disconnect()
    this.canvas?.remove()
    this.canvas = null
    this.ctx = null
    this.clear()
  }
}
