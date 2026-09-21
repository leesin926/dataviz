import type { ChartType, AnalysisQuery, R } from '@dataviz/shared-types'
import { request } from '../request'

export interface NL2SQLRequest {
  question: string
  datasetId: string | number
  context?: string
}

export interface NL2SQLResult {
  sql: string
  explanation?: string
  confidence: number
}

export interface ChartRecommendRequest {
  datasetId: string | number
  fields: string[]
  chartTypes?: ChartType[]
}

export interface ChartRecommendResult {
  recommendations: Array<{
    chartType: ChartType
    score: number
    config: Record<string, unknown>
    reason?: string
  }>
}

/** 自然语言转 SQL */
export async function nl2sql(req: NL2SQLRequest): Promise<NL2SQLResult> {
  const res = await request.post<R<NL2SQLResult>>('/ai/nl2sql', req)
  return res.data.data
}

/** 图表推荐 */
export async function recommendChart(req: ChartRecommendRequest): Promise<ChartRecommendResult> {
  const res = await request.post<R<ChartRecommendResult>>('/ai/chart/recommend', req)
  return res.data.data
}

/** 智能问答 (对话式) */
export async function askAssistant(question: string, sessionId?: string): Promise<{ answer: string; sessionId: string; suggestions?: string[] }> {
  const res = await request.post<R<{ answer: string; sessionId: string; suggestions?: string[] }>>(
    '/ai/assistant/ask',
    { question, sessionId },
  )
  return res.data.data
}

/** 查询优化建议 */
export async function optimizeQuery(query: AnalysisQuery): Promise<{ optimizedSql: string; suggestions: string[] }> {
  const res = await request.post<R<{ optimizedSql: string; suggestions: string[] }>>('/ai/query/optimize', query)
  return res.data.data
}
