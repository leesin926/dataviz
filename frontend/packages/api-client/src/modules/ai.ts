import type { R } from '@dataviz/shared-types'
import { request } from '../request'

/**
 * ai-service（8091）的真实契约。
 * 路径以前端曾写过的 `/ai/nl2sql`、`/ai/chart/recommend`、`/ai/assistant/ask`、`/ai/query/optimize`
 * 都不存在（那四条是凭空造的），这里逐条对齐 `/v3/api-docs` 抓下来的端点。
 */

/**
 * 大模型可用性。后端把"没配置"和"配了但调不通"分开报，前端不要合并成一句"AI 不可用"：
 * 前者要用户去配 `LLM_API_KEY`，后者要排查网络与端点。
 */
export interface AiStatus {
  llmConfigured: boolean
  note?: string
  model?: string
}

/** 三个会产文本的端点共用这对溯源字段；历史数据里是 null（当时还没记这个） */
export interface LlmProvenance {
  llmUsed?: boolean | null
  llmNote?: string | null
}

export interface Nl2SqlResult extends LlmProvenance {
  sql: string
  explanation?: string
  confidence: number
}

export interface ChartRecommendation {
  chartType: string
  chartName?: string
  score: number
  reason?: string
  suggestedMappings?: string[]
}

export interface ChatConversation {
  id: number
  userId?: number
  title?: string
  tenantId?: number
  createTime?: string
  updateTime?: string
}

export interface ChatMessage extends LlmProvenance {
  id: number
  conversationId: number
  role: string
  content: string
  sqlGenerated?: string
  tokenCount?: number
  createTime?: string
}

export interface AiInsight extends LlmProvenance {
  id: number
  datasourceId?: number
  datasetId?: number
  insightType?: string
  content?: string
  config?: Record<string, unknown>
  tenantId?: number
  createTime?: string
}

export async function getAiStatus(): Promise<AiStatus> {
  const res = await request.get<R<AiStatus>>('/ai/status')
  return res.data.data
}

/** 自然语言 → SQL。后端会读真实表结构，并只放行只读语句 */
export async function translateQuestion(dto: { datasourceId: string | number; question: string }): Promise<Nl2SqlResult> {
  const res = await request.post<R<Nl2SqlResult>>('/ai/nl2sql/translate', dto)
  return res.data.data
}

/** SQL → 中文解释；sql 走同一个 DTO 的 sql 字段 */
export async function explainSql(dto: { datasourceId?: string | number; sql: string }): Promise<Nl2SqlResult> {
  const res = await request.post<R<Nl2SqlResult>>('/ai/nl2sql/explain', dto)
  return res.data.data
}

/**
 * 图表推荐。列名是 `columns` 查询参数（重复键，不带方括号），行数据是 body 的数组，
 * **返回裸数组**，没有 R 包装 —— 所以这里不能取 `.data.data`。
 */
export async function recommendChart(
  columns: string[],
  rows: Array<Record<string, unknown>>,
): Promise<ChartRecommendation[]> {
  const res = await request.post<ChartRecommendation[]>('/ai/recommend/chart', rows, {
    params: { columns },
    // Spring 的 List<String> 绑定要 columns=a&columns=b；axios 默认加 []
    paramsSerializer: { indexes: null },
  })
  return Array.isArray(res.data) ? res.data : []
}

export async function createConversation(title?: string): Promise<ChatConversation> {
  const res = await request.post<R<ChatConversation>>('/ai/chat/conversation', null, {
    params: title ? { title } : undefined,
  })
  return res.data.data
}

export async function sendChatMessage(dto: { conversationId: string | number; content: string }): Promise<ChatMessage> {
  const res = await request.post<R<ChatMessage>>('/ai/chat/send', dto)
  return res.data.data
}

export async function listConversations(): Promise<ChatConversation[]> {
  const res = await request.get<R<ChatConversation[]>>('/ai/chat/conversations')
  return res.data.data
}

export async function getChatHistory(conversationId: string | number): Promise<ChatMessage[]> {
  const res = await request.get<R<ChatMessage[]>>(`/ai/chat/history/${conversationId}`)
  return res.data.data
}

export async function deleteConversation(conversationId: string | number): Promise<void> {
  await request.delete(`/ai/chat/conversation/${conversationId}`)
}

/** 生成洞察。config 是自由 JSON，后端把溯源信息也塞进同一份 config 落库，读回来时再拆成字段 */
export async function generateInsight(dto: {
  datasourceId?: string | number
  datasetId?: string | number
  insightType: string
  config?: Record<string, unknown>
}): Promise<AiInsight> {
  const res = await request.post<R<AiInsight>>('/ai/insight/generate', dto)
  return res.data.data
}

export async function listInsights(query: {
  datasourceId?: string | number
  datasetId?: string | number
  insightType?: string
}): Promise<AiInsight[]> {
  const res = await request.get<R<AiInsight[]>>('/ai/insight/list', { params: query })
  return res.data.data
}

export async function getInsight(id: string | number): Promise<AiInsight> {
  const res = await request.get<R<AiInsight>>(`/ai/insight/${id}`)
  return res.data.data
}
