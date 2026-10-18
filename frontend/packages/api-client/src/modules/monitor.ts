import type {
  MonitorMetric,
  MonitorMetricHistoryQuery,
  MonitorMetricHistoryPoint,
  MonitorMetricQuery,
  MonitorServiceHealth,
  MonitorServiceInstance,
} from '@dataviz/shared-types'
import { request } from '../request'
import { unwrap } from '../unwrap'

/**
 * 服务监控（后端 monitor-service）—— 只封装读侧 5 条端点。
 *
 * **故意不封装**的写侧：`/monitor/registry/register|deregister|heartbeat|metrics/report|
 * metrics/reportBatch` 与 `PUT /monitor/health/update`。它们的预期调用方是服务进程本身，
 * 前端一旦提供入口，等于让页面上的按钮能伪造注册、注销实例或往指标表灌点。
 *
 * 由此带来的界面约束：那组端点当前**零调用方**，所以实例清单很可能是空的。
 * "空清单 = 没人注册过"，不是"服务都挂了"；`listServiceNames()` 返回空数组时页面要这样解释。
 * 状态时间戳也天然滞后（monitor 自己每 30 秒巡检，90 秒无心跳判 DOWN）。
 */

/** 全部已注册实例（按最后心跳倒序） */
export async function listInstances(): Promise<MonitorServiceInstance[]> {
  const res = await request.get('/monitor/services')
  return unwrap<MonitorServiceInstance[]>(res.data) ?? []
}

/** 某个服务的实例列表 */
export async function listInstancesOfService(serviceName: string): Promise<MonitorServiceInstance[]> {
  const res = await request.get(`/monitor/services/${encodeURIComponent(serviceName)}/instances`)
  return unwrap<MonitorServiceInstance[]>(res.data) ?? []
}

/** 从实例清单里折出服务名，供下拉与卡片头使用 */
export async function listServiceNames(): Promise<string[]> {
  const instances = await listInstances()
  const names: string[] = []
  const seen: Record<string, boolean> = {}
  instances.forEach((inst) => {
    const name = inst.serviceName
    if (name && !seen[name]) {
      seen[name] = true
      names.push(name)
    }
  })
  return names
}

/** 单个服务的健康汇总（整体状态 + 活跃告警数） */
export async function getHealth(serviceName: string): Promise<MonitorServiceHealth> {
  const res = await request.get(`/monitor/health/${encodeURIComponent(serviceName)}`)
  return unwrap<MonitorServiceHealth>(res.data)
}

/**
 * 某服务的**现值**指标：每个 (实例, 指标名) 只回最新一条。
 * 曲线请走 getMetricHistory。
 */
export async function listMetrics(query: MonitorMetricQuery): Promise<MonitorMetric[]> {
  const res = await request.get('/monitor/metrics', { params: query })
  return unwrap<MonitorMetric[]>(res.data) ?? []
}

/** 单个指标的时间序列，按时间正序 */
export async function getMetricHistory(query: MonitorMetricHistoryQuery): Promise<MonitorMetricHistoryPoint[]> {
  const res = await request.get('/monitor/metrics/history', { params: query })
  return unwrap<MonitorMetricHistoryPoint[]>(res.data) ?? []
}
