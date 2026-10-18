import type {
  PageQuery,
  PageResult,
  ScheduleHandler,
  ScheduleJob,
  ScheduleJobListQuery,
  ScheduleJobLog,
  ScheduleJobPayload,
  ScheduleStats,
} from '@dataviz/shared-types'
import { request } from '../request'
import { asPage, unwrap } from '../unwrap'

/**
 * 定时调度（后端 schedule-service）。
 *
 * 两条必须让界面知道的口径，都是这一轮从后端真实行为里读出来的：
 * 1. `jobClass` 是**处理器键**，候选项只有 `listHandlers()` 给的那几个。
 *    以前它由表单自由填写、后端 `Class.forName` 反射实例化，等于让输入框决定跑哪个类；
 *    现在传不认识的键会在创建时就被 400 拒掉。
 * 2. 列表/详情里的 `handlerRegistered === false` 表示这条任务每次触发只会落一条失败日志
 *    （老数据里那三条 `com.dataviz.schedule.job.*` 从来没有对应类），界面要显出来而不是假装能跑。
 *
 * `misfirePolicy` 存库但调度器零读取（没有 Quartz，Spring 的 TaskScheduler 不记错过），
 * 所以它是"记录下来的偏好"，界面不能承诺错过后会不会补跑。
 */

/** 可被调度的处理器清单 = 表单"执行内容"下拉的唯一来源 */
export async function listHandlers(): Promise<ScheduleHandler[]> {
  const res = await request.get('/schedule/job/handlers')
  return unwrap<ScheduleHandler[]>(res.data) ?? []
}

export async function listJobs(query?: ScheduleJobListQuery & PageQuery): Promise<PageResult<ScheduleJob>> {
  const res = await request.get('/schedule/job/list', { params: query })
  return asPage<ScheduleJob>(res.data)
}

export async function getJob(id: string | number): Promise<ScheduleJob> {
  const res = await request.get(`/schedule/job/${id}`)
  return unwrap<ScheduleJob>(res.data)
}

/** 新建即运行：后端落库前先校验处理器与 cron，校验不过不会留下半条任务 */
export async function createJob(payload: ScheduleJobPayload): Promise<string | number> {
  const res = await request.post('/schedule/job', payload)
  return unwrap<string | number>(res.data)
}

/**
 * 编辑任务。
 * 后端只在状态为 RUNNING 时按新 cron 重建触发器；处于 PAUSED 的任务改完仍然不起触发器，
 * 想让它跑起来得显式 resume。
 */
export async function updateJob(payload: ScheduleJobPayload & { id: string | number }): Promise<void> {
  await request.put('/schedule/job', payload)
}

/** 删除任务并撤掉触发器 */
export async function deleteJob(id: string | number): Promise<void> {
  await request.delete(`/schedule/job/${id}`)
}

/** 暂停：撤触发器 + 状态置 PAUSED，重复调用幂等 */
export async function pauseJob(id: string | number): Promise<void> {
  await request.post(`/schedule/job/${id}/pause`)
}

/**
 * 恢复：从库里那一行重建触发器。
 * cron 或处理器已经不可用时后端会把状态置 ERROR 而不是假装恢复成功。
 */
export async function resumeJob(id: string | number): Promise<void> {
  await request.post(`/schedule/job/${id}/resume`)
}

/**
 * 立刻跑一次（异步）。
 * 这条端点返回的是"已排上下一次执行"，不是执行结果；结果只能去 logs 里看。
 */
export async function runJobOnce(id: string | number): Promise<void> {
  await request.post(`/schedule/job/${id}/runOnce`)
}

/** 执行日志分页，倒序 */
export async function listJobLogs(
  jobId: string | number,
  query?: { pageNum?: number; pageSize?: number },
): Promise<PageResult<ScheduleJobLog>> {
  const res = await request.get(`/schedule/job/${jobId}/logs`, { params: query })
  return asPage<ScheduleJobLog>(res.data)
}

export async function getStats(): Promise<ScheduleStats> {
  const res = await request.get('/schedule/dashboard/stats')
  return unwrap<ScheduleStats>(res.data)
}

/**
 * 运行中的任务清单。
 * 注意这条走的是 dashboard controller，它自己拼 VO、**不带 handlerRegistered**，
 * 所以别把它当成列表用；要看能不能跑得用 listJobs。
 */
export async function listRunningJobs(): Promise<ScheduleJob[]> {
  const res = await request.get('/schedule/dashboard/runningJobs')
  return unwrap<ScheduleJob[]>(res.data) ?? []
}
