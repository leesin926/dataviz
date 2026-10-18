/**
 * 协作域类型（后端 coll-service 的 4 个 controller / 27 个 handler 中对本域可见的那部分）
 *
 * 三条语义要先说清楚，否则前端很容易"画出来一个不存在的能力"：
 * 1. 评论的 @提醒只落到 `mentions_json` 原文，后端没有任何解析与推送方；
 * 2. 订阅（关注）的 `notify_types` 全后端**零读取方** —— 取消/新增都成立，但"订阅之后会收到什么"没有实现；
 * 3. 审批只有流水与裁决，没有通知，也没有"通过后自动上线"那一步。
 */

/** 审批对象类型的闭集（与 ApprovalServiceImpl.TARGET_TYPES 一致，脏类型会被后端 400 拒掉） */
export type ApprovalTargetType = 'SCREEN' | 'DASHBOARD' | 'DATASET'

/** 0 待审批 / 1 已通过 / 2 已驳回；后端同时给 statusLabel，前端不必维护对照表 */
export type ApprovalStatusCode = 0 | 1 | 2

export interface CollabComment {
  id: string | number
  tenantId?: string | number
  /** 后端不校验评论的对象类型（只有审批校验），所以这里保持宽类型 */
  targetType: string
  targetId: string | number
  content: string
  parentId?: string | number | null
  /** @提醒的原文，形如 `[{"id":3,"name":"张三"}]`；后端不解析、不推送 */
  mentionsJson?: string | null
  createBy?: string | number
  createTime?: string
}

export interface CollabSubscription {
  id: string | number
  tenantId?: string | number
  userId: string | number
  targetType: string
  targetId: string | number
  /** 见文件头第 2 条：存了但没人读，因此列表里原样显示，不做翻译 */
  notifyTypes?: string | null
  createTime?: string
}

export interface ApprovalRecord {
  id: string | number
  tenantId?: string | number
  targetType: string
  targetId: string | number
  applicantId: string | number
  approverId?: string | number | null
  status: ApprovalStatusCode | number
  /** 后端给的中文名（待审批 / 已通过 / 已驳回） */
  statusLabel: string
  comment?: string | null
  createTime?: string
  approveTime?: string | null
}

export interface CommentPayload {
  targetType: string
  targetId: string | number
  content: string
  parentId?: string | number | null
  mentionsJson?: string | null
}

export interface SubscriptionPayload {
  targetType: string
  targetId: string | number
  notifyTypes?: string
}

export interface ApprovalPayload {
  targetType: ApprovalTargetType | string
  targetId: string | number
}

export interface ApprovalDecisionPayload {
  /** 最长 512 字，超了后端 400；且这一列与"申请说明"共用，写下去会覆盖 */
  comment?: string
}

export interface ApprovalListQuery {
  pageNum?: number
  pageSize?: number
  targetType?: string
  targetId?: string | number
  status?: number
  /** 我发起的 */
  mine?: boolean
  /** 待我裁决（后端已排除我自己提的单子） */
  todo?: boolean
}
