import type { ChartConfig } from '@dataviz/shared-types'
import type { ChartData } from '../renderer/BaseRenderer'
import { formatNumber, formatPercent } from '@dataviz/shared-utils'

/**
 * KPI 卡片渲染 (非 ECharts)
 */
export function renderKpiCard(
  ctx: CanvasRenderingContext2D,
  width: number,
  height: number,
  config: ChartConfig,
  data: ChartData,
): void {
  const value = data.series[0]?.data[0]
  const measure = config.measures[0]
  const isPercent = measure?.format === 'percent'
  const displayValue =
    value === null || value === undefined
      ? '-'
      : isPercent
        ? formatPercent(value as number)
        : formatNumber(value as number)

  // 背景
  const gradient = ctx.createLinearGradient(0, 0, 0, height)
  gradient.addColorStop(0, '#ffffff')
  gradient.addColorStop(1, '#f7f8fa')
  ctx.fillStyle = gradient
  ctx.fillRect(0, 0, width, height)

  // 标题
  ctx.fillStyle = '#666'
  ctx.font = '14px PingFang SC, Microsoft YaHei, sans-serif'
  ctx.textAlign = 'center'
  ctx.fillText(config.title || measure?.alias || measure?.field || '数值', width / 2, 40)

  // 主数值
  ctx.fillStyle = '#222'
  ctx.font = `bold ${Math.min(48, height / 3)}px PingFang SC, Microsoft YaHei, sans-serif`
  ctx.fillText(String(displayValue), width / 2, height / 2 + 10)

  // 副标题
  const sub = config.options?.subTitle as string | undefined
  if (sub) {
    ctx.fillStyle = '#999'
    ctx.font = '12px PingFang SC, Microsoft YaHei, sans-serif'
    ctx.fillText(sub, width / 2, height - 20)
  }
}
