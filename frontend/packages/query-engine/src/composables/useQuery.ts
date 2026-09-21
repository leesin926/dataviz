/**
 * useQuery - SQL 查询执行状态 composable
 */
import { ref, computed } from 'vue'
import type { QueryResult, QueryColumn } from '@dataviz/shared-types'

export interface QueryState {
  sql: string
  status: 'idle' | 'running' | 'success' | 'error'
  result: QueryResult | null
  error: string | null
  elapsed: number
  history: QueryHistoryItem[]
}

export interface QueryHistoryItem {
  id: string
  sql: string
  status: 'success' | 'error'
  elapsed: number
  rowCount?: number
  errorMessage?: string
  executedAt: string
}

export interface UseQueryOptions {
  executeFn?: (sql: string) => Promise<QueryResult>
  maxHistory?: number
}

export function useQuery(options: UseQueryOptions = {}) {
  const { maxHistory = 50 } = options

  const sql = ref('')
  const status = ref<'idle' | 'running' | 'success' | 'error'>('idle')
  const result = ref<QueryResult | null>(null)
  const error = ref<string | null>(null)
  const elapsed = ref(0)
  const history = ref<QueryHistoryItem[]>([])

  const isRunning = computed(() => status.value === 'running')
  const isSuccess = computed(() => status.value === 'success')
  const isError = computed(() => status.value === 'error')
  const resultColumns = computed<QueryColumn[]>(() => result.value?.columns || [])
  const resultRows = computed(() => result.value?.rows || [])
  const resultTotal = computed(() => result.value?.total || resultRows.value.length)

  /**
   * 执行 SQL 查询
   */
  async function executeQuery(customSql?: string): Promise<void> {
    const querySql = customSql || sql.value
    if (!querySql.trim()) return

    if (!options.executeFn) {
      error.value = '查询执行函数未配置'
      status.value = 'error'
      return
    }

    status.value = 'running'
    error.value = null
    result.value = null
    const startTime = Date.now()

    try {
      const queryResult = await options.executeFn(querySql)
      elapsed.value = Date.now() - startTime
      result.value = queryResult
      status.value = 'success'

      addToHistory({
        sql: querySql,
        status: 'success',
        elapsed: elapsed.value,
        rowCount: queryResult.rows?.length || 0,
        executedAt: new Date().toISOString(),
      })
    } catch (err) {
      elapsed.value = Date.now() - startTime
      const errorMessage = err instanceof Error ? err.message : String(err)
      error.value = errorMessage
      status.value = 'error'

      addToHistory({
        sql: querySql,
        status: 'error',
        elapsed: elapsed.value,
        errorMessage,
        executedAt: new Date().toISOString(),
      })
    }
  }

  /**
   * 添加到历史记录
   */
  function addToHistory(item: Omit<QueryHistoryItem, 'id'>): void {
    const historyItem: QueryHistoryItem = {
      ...item,
      id: `h_${Date.now()}_${Math.random().toString(36).slice(2, 7)}`,
    }
    history.value.unshift(historyItem)
    if (history.value.length > maxHistory) {
      history.value = history.value.slice(0, maxHistory)
    }
  }

  /**
   * 清除历史
   */
  function clearHistory(): void {
    history.value = []
  }

  /**
   * 从历史中加载 SQL
   */
  function loadFromHistory(item: QueryHistoryItem): void {
    sql.value = item.sql
  }

  /**
   * 取消查询（如果支持）
   */
  function cancelQuery(): void {
    if (status.value === 'running') {
      status.value = 'idle'
    }
  }

  /**
   * 重置状态
   */
  function reset(): void {
    sql.value = ''
    status.value = 'idle'
    result.value = null
    error.value = null
    elapsed.value = 0
  }

  return {
    sql,
    status,
    result,
    error,
    elapsed,
    history,
    isRunning,
    isSuccess,
    isError,
    resultColumns,
    resultRows,
    resultTotal,
    executeQuery,
    clearHistory,
    loadFromHistory,
    cancelQuery,
    reset,
  }
}
