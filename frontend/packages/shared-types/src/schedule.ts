/**
 * 定时调度域类型（后端 schedule-service）
 *
 * 与后端一起改过的一条语义：`jobClass` 不再是自由文本的"全限定类名"，
 * 而是**注册表里的处理器键**（候选项由 `GET /schedule/job/handlers` 给出）。
 * 老数据里那三条 `com.dataviz.schedule.job.*Job` 从来没有对应的类，
 * 所以 VO 上带了 `handlerRegistered` 让列表能把"这条任务其实跑不起来"显出来。
 */

/** RUNNING 有触发器 / PAUSED 已撤触发器 / ERROR 启动装载时发现处理器或 cron 不可用 */
export type ScheduleJobStatus = 'RUNNING' | 'PAUSED' | 'ERROR'

export type ScheduleMisfirePolicy = 'IGNORE' | 'FIRE_ONCE' | 'EXECUTE_ALL'

export type ScheduleLogStatus = 'SUCCESS' | 'FAILED'

export interface ScheduleJob {
  id: string | number
  jobName: string
  jobGroup?: string | null
  /** 6 段式 Spring cron，如 `0 0/30 * * * ?` */
  cronExpression: string
  /** 处理器键，不是类名 */
  jobClass: string
  jobParams?: string | null
  status: ScheduleJobStatus | string
  description?: string | null
  misfirePolicy?: ScheduleMisfirePolicy | string | null
  createTime?: string
  updateTime?: string
  /** false = 每次触发只会留下一条 FAILED 日志 */
  handlerRegistered?: boolean
  handlerLabel?: string | null
}

export interface ScheduleJobLog {
  id: string | number
  jobId: string | number
  jobName: string
  startTime?: string | null
  endTime?: string | null
  status: ScheduleLogStatus | string
  /** 成功时写"已执行 xx：中文名"，失败时写为什么不执行 */
  message?: string | null
  createTime?: string
}

export interface ScheduleStats {
  totalJobs: number
  runningJobs: number
  pausedJobs: number
  errorJobs: number
  todayExecutions: number
  todaySuccess: number
  todayFailed: number
}

/** 前端可选的处理器（也是"执行内容"下拉的唯一来源） */
export interface ScheduleHandler {
  handlerKey: string
  label: string
  description: string
}

export interface ScheduleJobPayload {
  id?: string | number
  jobName: string
  jobGroup?: string
  cronExpression: string
  jobClass: string
  jobParams?: string
  description?: string
  misfirePolicy?: ScheduleMisfirePolicy | string
}

export interface ScheduleJobListQuery {
  pageNum?: number
  pageSize?: number
  jobGroup?: string
  status?: string
}
