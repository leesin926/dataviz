/**
 * 组件数据层（4.4）：static / dataset / http 三类数据源 + interval 轮询。
 *
 * dataset 走的正是 PC 展示端同一条 OLAP 通道（`POST /analysis/query/execute`，
 * 入参口径 metrics/orders 与 `api-client/modules/analysis.ts` 的 toDTO 一致）。
 * 这里**不引 api-client**：它基于 axios，小程序与 App 端没有 XHR，故统一用注入的请求函数。
 */

import { inject, onBeforeUnmount, ref, watch } from 'vue'
import type { Ref } from 'vue'
import type { ScreenComponent } from '@dataviz/shared-types'
import type { ChartData } from './chartAdapter'
import { uniRequest, type UniRequestFn } from './uniRuntime'

export interface DataEnv {
  /** 网关基址，如 `/api`（H5 走代理）或 `http://host:8080/api` */
  baseUrl: string
  /** 登录态；分享页可空 */
  accessToken?: string
  /** 覆盖默认请求实现（各端 utils/request.ts） */
  request?: UniRequestFn
}

interface R<T> {
  code?: number
  message?: string
  data?: T
}

interface QueryResultVO {
  columns?: string[] | null
  rows?: Record<string, unknown>[] | null
}

function unwrap<T>(body: unknown): T {
  const r = (body || {}) as R<T>
  if (r.code === 0 || r.code === 200) return r.data as T
  throw new Error(r.message || '接口返回失败')
}

/** 静态数据允许写成 `{columns, rows}`、数组、或单个对象 */
function asChartData(value: unknown): ChartData | null {
  if (!value) return null
  if (Array.isArray(value)) return { rows: value as ChartData['rows'] }
  const obj = value as ChartData & { data?: unknown }
  if (Array.isArray(obj.rows)) return { columns: obj.columns, rows: obj.rows }
  if (Array.isArray(obj.data)) return { rows: obj.data as ChartData['rows'] }
  return null
}

function toQueryBody(component: ScreenComponent): Record<string, unknown> {
  const cfg = component.chartConfig
  return {
    datasetId: cfg?.datasetId,
    dimensions: (cfg?.dimensions || []).map((d) => ({ field: d.field })),
    metrics: (cfg?.measures || []).map((m) => ({ field: m.field, aggFunction: m.aggregation, alias: m.alias })),
    filters: cfg?.filters,
    orders: (cfg?.sorts || []).map((s) => ({ field: s.field, direction: s.order })),
  }
}

export function useComponentData(component: Ref<ScreenComponent>, env: DataEnv): {
  data: Ref<ChartData | null>
  loading: Ref<boolean>
  error: Ref<string>
  reload: () => Promise<void>
} {
  const data = ref<ChartData | null>(null)
  const loading = ref(false)
  const error = ref('')
  const send = env.request || uniRequest

  async function load(): Promise<void> {
    const comp = component.value
    const req = comp.request
    const sourceType = req?.sourceType || (comp.chartConfig?.datasetId ? 'dataset' : 'static')
    loading.value = true
    error.value = ''
    try {
      if (sourceType === 'static') {
        const inline = asChartData(req?.staticData) || asChartData(comp.props?.data)
        if (inline) data.value = inline
        // 静态数据缺失时保留上一次结果：编辑器改 props 的过程中不该闪空
      } else if (sourceType === 'http') {
        if (!req?.requestUrl) throw new Error('未配置请求地址')
        const header: Record<string, string> = { 'Content-Type': 'application/json', ...(req.requestHeaders as Record<string, string>) }
        if (env.accessToken) header.Authorization = `Bearer ${env.accessToken}`
        const url = /^https?:\/\//i.test(req.requestUrl) ? req.requestUrl : `${env.baseUrl}${req.requestUrl}`
        const body = await send({ url, method: req.requestMethod || 'GET', data: req.requestParams, header })
        const parsed = asChartData(body) || asChartData((body as R<unknown>)?.data)
        if (!parsed) throw new Error('外部接口返回结构无法解析为数据集')
        data.value = parsed
      } else {
        if (!comp.chartConfig?.datasetId) throw new Error('数据集未绑定 datasetId')
        const body = await send({
          url: `${env.baseUrl}/analysis/query/execute`,
          method: 'POST',
          data: toQueryBody(comp),
          header: env.accessToken ? { Authorization: `Bearer ${env.accessToken}` } : undefined,
        })
        const vo = unwrap<QueryResultVO>(body)
        data.value = {
          columns: (vo.columns || []).map((field) => ({ field, type: 'number' })),
          rows: vo.rows || [],
        }
      }
    } catch (e) {
      error.value = (e as Error).message || String(e)
    } finally {
      loading.value = false
    }
  }

  let timer: ReturnType<typeof setInterval> | null = null

  function restartTimer(): void {
    if (timer) {
      clearInterval(timer)
      timer = null
    }
    const seconds = Number(component.value.request?.interval) || 0
    if (seconds > 0) timer = setInterval(() => void load(), Math.max(2, seconds) * 1000)
  }

  watch(
    () => [component.value.request, component.value.chartConfig, component.value.props],
    () => {
      void load()
      restartTimer()
    },
    { deep: true },
  )

  void load()
  restartTimer()

  // 屏级 globalRefreshInterval 由引擎 provide 的计数器驱动；组件自身的 interval 已在上面单独处理
  const globalTick = inject<Ref<number>>('uniScreenTick', ref(0))
  watch(globalTick, () => {
    void load()
  })

  onBeforeUnmount(() => {
    if (timer) clearInterval(timer)
  })

  return { data, loading, error, reload: () => load() }
}
