import * as echarts from 'echarts'
import type { ECharts, EChartsOption } from 'echarts'
import { BaseRenderer, type ChartData } from './BaseRenderer'
import type { ChartConfig } from '@dataviz/shared-types'
import { ChartType } from '@dataviz/shared-types'
import { buildBarOption } from '../charts/bar'
import { buildLineOption } from '../charts/line'
import { buildPieOption } from '../charts/pie'
import { buildScatterOption } from '../charts/scatter'
import { buildMapOption } from '../charts/map'
import { buildRadarOption } from '../charts/radar'
import { buildGaugeOption } from '../charts/gauge'
import { buildHeatmapOption } from '../charts/heatmap'
import { buildTreemapOption } from '../charts/treemap'
import { buildSunburstOption } from '../charts/sunburst'
import { registerTheme, type ThemeName } from '../themes'

/**
 * ECharts 渲染器
 */
export class EChartsRenderer extends BaseRenderer {
  private chart: ECharts | null = null

  /** 主题名必须在 init 时传入：echarts 只在初始化阶段合并主题默认值 */
  constructor(container: HTMLElement, theme: ThemeName = 'light') {
    super(container)
    registerTheme()
    this.chart = echarts.init(container, theme)
  }

  render(config: ChartConfig, data: ChartData): void {
    if (!this.chart) return
    let option: EChartsOption = {}
    switch (config.chartType) {
      case ChartType.BAR:
      case ChartType.WATERFALL:
        option = buildBarOption(config, data)
        break
      case ChartType.LINE:
        option = buildLineOption(config, data)
        break
      case ChartType.PIE:
        option = buildPieOption(config, data)
        break
      case ChartType.SCATTER:
        option = buildScatterOption(config, data)
        break
      case ChartType.MAP:
        option = buildMapOption(config, data)
        break
      case ChartType.RADAR:
        option = buildRadarOption(config, data)
        break
      case ChartType.GAUGE:
        option = buildGaugeOption(config, data)
        break
      case ChartType.HEATMAP:
        option = buildHeatmapOption(config, data)
        break
      case ChartType.TREEMAP:
        option = buildTreemapOption(config, data)
        break
      case ChartType.SUNBURST:
        option = buildSunburstOption(config, data)
        break
      default:
        option = buildBarOption(config, data)
    }
    // 合并用户自定义覆盖选项（主题默认值已由 echarts.init 注入，不再展开到 option 上覆盖同名键）
    const merged: EChartsOption = {
      ...option,
      ...(config.options as EChartsOption),
    }
    try {
      this.chart.setOption(merged, { notMerge: true })
    } catch (e) {
      // 地图未注册等配置级错误不应导致整块渲染崩溃
      console.warn(`[chart-engine] ${config.chartType} 渲染失败`, e)
      return
    }
    this.bindChartEvents()
  }

  private bindChartEvents(): void {
    if (!this.chart) return
    this.chart.off('click')
    this.chart.off('dblclick')
    this.chart.on('click', (params) => this.emit('click', params))
    this.chart.on('dblclick', (params) => this.emit('dblclick', params))
  }

  resize(): void {
    this.chart?.resize()
  }

  setLoading(loading: boolean): void {
    if (!this.chart) return
    if (loading) {
      this.chart.showLoading({ text: '', color: '#5470c6', maskColor: 'rgba(255,255,255,0.6)' })
    } else {
      this.chart.hideLoading()
    }
  }

  exportImage(type = 'png'): string | null {
    return this.chart?.getDataURL({ type, pixelRatio: 2, backgroundColor: '#fff' }) ?? null
  }

  dispose(): void {
    this.chart?.dispose()
    this.chart = null
    this.clear()
  }
}
