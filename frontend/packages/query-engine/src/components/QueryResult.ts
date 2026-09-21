<template>
  <div class="query-result">
    <div class="result-header">
      <div class="result-info">
        <span v-if="loading" class="status-badge status-running">执行中...</span>
        <span v-else-if="error" class="status-badge status-error">{{ error }}</span>
        <span v-else-if="columns.length > 0" class="status-badge status-success">
          查询成功 - {{ rows.length }} 行, 耗时 {{ elapsed }}ms
        </span>
        <span v-else class="status-badge">暂无数据</span>
      </div>
      <div class="result-actions">
        <button v-if="rows.length > 0" class="action-btn" @click="copyToClipboard">复制</button>
        <button v-if="rows.length > 0" class="action-btn" @click="exportCsv">导出 CSV</button>
      </div>
    </div>

    <div class="result-table-wrapper">
      <table v-if="columns.length > 0" class="result-table">
        <thead>
          <tr>
            <th class="row-number">#</th>
            <th v-for="col in columns" :key="col.field">
              {{ col.displayName || col.field }}
              <span class="col-type">{{ col.type }}</span>
            </th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(row, idx) in paginatedRows" :key="idx">
            <td class="row-number">{{ idx + 1 + (currentPage - 1) * pageSize }}</td>
            <td v-for="col in columns" :key="col.field" class="cell" :title="String(row[col.field] ?? '')">
              {{ formatCellValue(row[col.field], col) }}
            </td>
          </tr>
        </tbody>
      </table>
      <div v-else class="result-empty">
        <p>执行查询以查看结果</p>
      </div>
    </div>

    <div v-if="totalPages > 1" class="result-pagination">
      <button :disabled="currentPage <= 1" @click="currentPage--">上一页</button>
      <span>{{ currentPage }} / {{ totalPages }}</span>
      <button :disabled="currentPage >= totalPages" @click="currentPage++">下一页</button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import type { QueryColumn } from '@dataviz/shared-types'

interface QueryRow {
  [key: string]: unknown
}

const props = withDefaults(
  defineProps<{
    columns?: QueryColumn[]
    rows?: QueryRow[]
    loading?: boolean
    error?: string | null
    elapsed?: number
    pageSize?: number
  }>(),
  {
    columns: () => [],
    rows: () => [],
    loading: false,
    error: null,
    elapsed: 0,
    pageSize: 100,
  }
)

const currentPage = ref(1)

const totalPages = computed(() => Math.ceil(props.rows.length / props.pageSize))

const paginatedRows = computed(() => {
  const start = (currentPage.value - 1) * props.pageSize
  return props.rows.slice(start, start + props.pageSize)
})

function formatCellValue(value: unknown, col: QueryColumn): string {
  if (value === null || value === undefined) return 'NULL'
  if (col.type === 'date' && typeof value === 'string') {
    return value.replace('T', ' ').slice(0, 19)
  }
  return String(value)
}

function copyToClipboard(): void {
  const header = props.columns.map((c) => c.field).join('\t')
  const data = props.rows.map((row) =>
    props.columns.map((c) => String(row[c.field] ?? '')).join('\t')
  )
  const text = [header, ...data].join('\n')
  navigator.clipboard.writeText(text)
}

function exportCsv(): void {
  const header = props.columns.map((c) => c.field).join(',')
  const data = props.rows.map((row) =>
    props.columns.map((c) => {
      const val = String(row[c.field] ?? '')
      return val.includes(',') ? `"${val}"` : val
    }).join(',')
  )
  const csv = [header, ...data].join('\n')
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `query_result_${Date.now()}.csv`
  a.click()
  URL.revokeObjectURL(url)
}
</script>

<style scoped>
.query-result {
  display: flex;
  flex-direction: column;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  background: #fff;
  overflow: hidden;
}

.result-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  background: #f5f7fa;
  border-bottom: 1px solid #e4e7ed;
}

.status-badge {
  font-size: 13px;
  color: #909399;
}

.status-success { color: #67c23a; }
.status-error { color: #f56c6c; }
.status-running { color: #409eff; }

.action-btn {
  padding: 4px 10px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  font-size: 12px;
  background: #fff;
  cursor: pointer;
  margin-left: 6px;
}

.action-btn:hover { border-color: #409eff; color: #409eff; }

.result-table-wrapper {
  flex: 1;
  overflow: auto;
  max-height: 500px;
}

.result-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}

.result-table th,
.result-table td {
  padding: 6px 12px;
  border-bottom: 1px solid #ebeef5;
  text-align: left;
  white-space: nowrap;
  max-width: 300px;
  overflow: hidden;
  text-overflow: ellipsis;
}

.result-table th {
  background: #f5f7fa;
  font-weight: 600;
  position: sticky;
  top: 0;
  z-index: 1;
}

.col-type {
  font-size: 10px;
  color: #c0c4cc;
  margin-left: 4px;
}

.row-number {
  color: #c0c4cc;
  text-align: right;
  width: 50px;
  font-size: 12px;
}

.result-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px;
  color: #909399;
}

.result-pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 8px;
  border-top: 1px solid #e4e7ed;
}

.result-pagination button {
  padding: 4px 12px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  font-size: 12px;
  cursor: pointer;
  background: #fff;
}

.result-pagination button:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}
</style>
