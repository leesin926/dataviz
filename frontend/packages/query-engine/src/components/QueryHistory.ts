/**
 * QueryHistory - 查询历史记录展示组件
 * 注：这是 TS 组件（非 Vue SFC），渲染查询历史侧边栏
 */
import type { QueryHistoryItem } from '../composables/useQuery'

export interface QueryHistoryProps {
  items: QueryHistoryItem[]
  visible: boolean
}

/**
 * 渲染查询历史 HTML
 */
export function renderHistory(items: QueryHistoryItem[]): string {
  if (items.length === 0) {
    return '<div class="history-empty">暂无查询历史</div>'
  }

  return items
    .map(
      (item) => `
    <div class="history-item ${item.status}" data-id="${item.id}">
      <div class="history-sql">${escapeHtml(item.sql)}</div>
      <div class="history-meta">
        <span class="history-status ${item.status}">${item.status === 'success' ? '成功' : '失败'}</span>
        <span class="history-time">${item.elapsed}ms</span>
        <span class="history-date">${formatDate(item.executedAt)}</span>
      </div>
    </div>
  `
    )
    .join('')
}

function escapeHtml(text: string): string {
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
}

function formatDate(iso: string): string {
  try {
    const d = new Date(iso)
    return `${d.getMonth() + 1}/${d.getDate()} ${d.getHours()}:${String(d.getMinutes()).padStart(2, '0')}`
  } catch {
    return iso
  }
}

export { type QueryHistoryItem }
