import type { ChartConfig } from '@dataviz/shared-types'
import type { ChartData } from '../renderer/BaseRenderer'
import { formatNumber } from '@dataviz/shared-utils'

/**
 * 数据表格渲染 (非 ECharts)
 */
export function renderTable(
  ctx: CanvasRenderingContext2D,
  width: number,
  height: number,
  config: ChartConfig,
  data: ChartData,
): void {
  const fields = [...config.dimensions.map((d) => d.alias || d.field), ...config.measures.map((m) => m.alias || m.field)]
  const rows = data.raw || []
  const colCount = fields.length
  const colWidth = width / colCount
  const rowHeight = 28

  // 背景
  ctx.fillStyle = '#fff'
  ctx.fillRect(0, 0, width, height)

  // 表头
  ctx.fillStyle = '#fafafa'
  ctx.fillRect(0, 0, width, rowHeight)
  ctx.fillStyle = '#333'
  ctx.font = 'bold 12px PingFang SC, Microsoft YaHei, sans-serif'
  fields.forEach((f, i) => {
    ctx.fillText(f, i * colWidth + 8, 18)
  })

  // 分隔线
  ctx.strokeStyle = '#eee'
  ctx.beginPath()
  ctx.moveTo(0, rowHeight)
  ctx.lineTo(width, rowHeight)
  ctx.stroke()

  // 数据行
  ctx.font = '12px PingFang SC, Microsoft YaHei, sans-serif'
  const maxRows = Math.floor((height - rowHeight) / rowHeight)
  for (let r = 0; r < Math.min(rows.length, maxRows); r++) {
    const row = rows[r]
    const y = rowHeight * (r + 1)
    // 斑马纹
    if (r % 2 === 1) {
      ctx.fillStyle = '#fafbfc'
      ctx.fillRect(0, y, width, rowHeight)
    }
    ctx.fillStyle = '#333'
    let col = 0
    for (const d of config.dimensions) {
      const val = String(row[d.field] ?? '')
      ctx.fillText(val, col * colWidth + 8, y + 18)
      col++
    }
    for (const m of config.measures) {
      const val = formatNumber(row[m.field] as number)
      ctx.fillText(val, col * colWidth + 8, y + 18)
      col++
    }
    // 下边框
    ctx.strokeStyle = '#f0f0f0'
    ctx.beginPath()
    ctx.moveTo(0, y + rowHeight)
    ctx.lineTo(width, y + rowHeight)
    ctx.stroke()
  }
}
