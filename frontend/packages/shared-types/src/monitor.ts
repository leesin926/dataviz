/**
 * 服务监控域类型（后端 monitor-service）
 *
 * 这一域能被前端诚实呈现的前提，是它对"数据从哪来"说得很直白：
 * 1. 实例清单与指标全来自 `POST /monitor/registry/*`（注册/心跳/上报），而那组端点
 *    的预期调用方是服务进程本身，**当前后端与前端都零调用**。所以"服务列表为空"
 *    的真实含义是"没人注册过"，不是"所有服务都挂了" —— 页面必须把这两种状态分开显示。
 * 2. 服务状态由 monitor 自己的 30 秒周期任务推进（90 秒无心跳判 DOWN 并记 instance_down 告警），
 *    所以页面上的时间戳天然是滞后的，不做实时探针。
 * 3. `GET /monitor/metrics` 的语义是**现值**：同一实例的同一指标只回最新一条（后端已按此收敛）。
 *    要看走势走 `/monitor/metrics/history`，那条才是全量点列。
 */

/** UP 在线 / DOWN 下线（含被判死） / STARTING 启动中 */
export type MonitorInstanceStatus = 'UP' | 'DOWN' | 'STARTING'

/** 只有实例全 UP 才是 UP；一个都不 UP 是 DOWN；介于两者之间是 PARTIAL */
export type MonitorOverallStatus = 'UP' | 'DOWN' | 'PARTIAL'

/** GAUGE 瞬时值 / COUNTER 累计计数 / HISTOGRAM 分布 */
export type MonitorMetricType = 'GAUGE' | 'COUNTER' | 'HISTOGRAM'

export type MonitorAlertStatus = 'ACTIVE' | 'RESOLVED'

export interface MonitorServiceInstance {
  id: string | number
  serviceName: string
  instanceId: string
  host: string
  port: number
  status: MonitorInstanceStatus | string
  /** 注册时上报的键值元数据（版本、JVM 等）。库里存 JSON，由后端解析成对象 */
  metadata?: Record<string, string>
  /** 最后一次心跳；空列表与陈旧心跳的差别就是第 1 条说的两件事 */
  lastHeartbeat?: string | null
  createTime?: string
}

export interface MonitorServiceHealth {
  serviceName: string
  instanceCount: number
  healthyCount: number
  overallStatus: MonitorOverallStatus | string
  /** 本次检查的时间（每次调用都是当下） */
  lastCheckTime?: string
  /** 该服务全部实例里最新的一次心跳 */
  lastHeartbeat?: string | null
  /** 该服务未处理的监控告警条数 */
  activeAlertCount: number
}

export interface MonitorMetric {
  id: string | number
  serviceName: string
  instanceId?: string | null
  metricName: string
  /** 后端是 BigDecimal，JSON 里落成数字 */
  metricValue: number
  metricType?: MonitorMetricType | string | null
  tags?: Record<string, string> | null
  timestamp?: string
}

/** 历史点列没有 id / tags，只有画曲线用得上的四列 */
export interface MonitorMetricHistoryPoint {
  metricName: string
  metricValue: number
  metricType?: MonitorMetricType | string | null
  timestamp?: string
}

export interface MonitorMetricQuery {
  serviceName: string
  instanceId?: string
}

export interface MonitorMetricHistoryQuery {
  serviceName: string
  metricName: string
  /** ISO 本地日期时间串，形如 `2026-10-16T09:00:00` */
  startTime?: string
  endTime?: string
}
