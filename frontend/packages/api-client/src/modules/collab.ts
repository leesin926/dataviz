import type {
  ApprovalListQuery,
  ApprovalDecisionPayload,
  ApprovalPayload,
  ApprovalRecord,
  CollabComment,
  CollabSubscription,
  CommentPayload,
  PageQuery,
  PageResult,
  SubscriptionPayload,
} from '@dataviz/shared-types'
import { request } from '../request'
import { asPage, unwrap } from '../unwrap'

/**
 * 协作（评论 / 关注 / 审批）。
 *
 * 后端 collab-service 对评论和订阅各留了**两套**端点：复数一组（`/collab/comments`、
 * `/collab/subscriptions`）从会话取用户，单数一组（`/collab/comment`、`/collab/subscription`）
 * 是裸返回、且订阅侧要从入参取 userId。这里**只封装复数一组**：
 * `/collab/subscription/list/{userId}` 与 `/subscription/check?userId=` 让任意登录用户
 * 读别人的关注清单，属 API-40 那一族跨主体读，未收口前不给前端递上入口。
 *
 * 另外两条口径：
 * - `mentionsJson` 只是原文存下来，后端不解析、不推送，所以 @ 提醒在界面上是"记录"不是"通知"；
 * - 订阅的 `notifyTypes` 全后端零读取方，因此这里只提供订阅/退订/查询，界面文案不得承诺推送。
 */

/** 某对象的全部顶层评论（后端不按会话过滤，按 target 全量返回） */
export async function listComments(targetType: string, targetId: string | number): Promise<CollabComment[]> {
  const res = await request.get(`/collab/comments/target/${targetType}/${targetId}`)
  return unwrap<CollabComment[]>(res.data) ?? []
}

export async function getComment(id: string | number): Promise<CollabComment> {
  const res = await request.get(`/collab/comments/${id}`)
  return unwrap<CollabComment>(res.data)
}

/** 发评论，返回新评论 id */
export async function createComment(payload: CommentPayload): Promise<string | number> {
  const res = await request.post('/collab/comments', payload)
  return unwrap<string | number>(res.data)
}

/** 改评论正文。后端只收 content，其他字段（含 @ 原文）改不了 */
export async function updateComment(id: string | number, content: string): Promise<void> {
  await request.put(`/collab/comments/${id}`, null, { params: { content } })
}

/** 删评论。后端物理删除，楼中楼不会级联清理 */
export async function deleteComment(id: string | number): Promise<void> {
  await request.delete(`/collab/comments/${id}`)
}

/** 某条评论的全部回复 */
export async function listReplies(parentId: string | number): Promise<CollabComment[]> {
  const res = await request.get(`/collab/comments/replies/${parentId}`)
  return unwrap<CollabComment[]>(res.data) ?? []
}

/** 关注某对象，返回订阅 id；后端不去重，重复订阅会重复入库 */
export async function subscribe(payload: SubscriptionPayload): Promise<string | number> {
  const res = await request.post('/collab/subscriptions', payload)
  return unwrap<string | number>(res.data)
}

export async function unsubscribe(id: string | number): Promise<void> {
  await request.delete(`/collab/subscriptions/${id}`)
}

/** 我关注了什么（主体取自会话，不接受 userId 入参） */
export async function listMySubscriptions(): Promise<CollabSubscription[]> {
  const res = await request.get('/collab/subscriptions/my')
  return unwrap<CollabSubscription[]>(res.data) ?? []
}

export async function isSubscribed(targetType: string, targetId: string | number): Promise<boolean> {
  const res = await request.get('/collab/subscriptions/check', { params: { targetType, targetId } })
  return unwrap<boolean>(res.data) === true
}

/** 发起审批，返回单据 id */
export async function applyApproval(payload: ApprovalPayload): Promise<string | number> {
  const res = await request.post('/collab/approval', payload)
  return unwrap<string | number>(res.data)
}

/**
 * 裁决。后端通过/驳回是两条端点而不是一个 status 字段，
 * 且裁决意见可空（body 整个可以不传）。
 */
export async function approveApproval(id: string | number, payload?: ApprovalDecisionPayload): Promise<ApprovalRecord> {
  const res = await request.post(`/collab/approval/${id}/approve`, payload ?? {})
  return unwrap<ApprovalRecord>(res.data)
}

export async function rejectApproval(id: string | number, payload?: ApprovalDecisionPayload): Promise<ApprovalRecord> {
  const res = await request.post(`/collab/approval/${id}/reject`, payload ?? {})
  return unwrap<ApprovalRecord>(res.data)
}

export async function getApproval(id: string | number): Promise<ApprovalRecord> {
  const res = await request.get(`/collab/approval/${id}`)
  return unwrap<ApprovalRecord>(res.data)
}

/**
 * 审批流水分页。
 * `mine` 我发起的、`todo` 待我裁决（后端已排除自己提的单子）。
 * 注意审批通过后没有任何后续动作 —— 不会自动上线，也不会通知申请人。
 */
export async function listApprovals(query?: ApprovalListQuery & PageQuery): Promise<PageResult<ApprovalRecord>> {
  const res = await request.get('/collab/approval/list', { params: query })
  return asPage<ApprovalRecord>(res.data)
}
