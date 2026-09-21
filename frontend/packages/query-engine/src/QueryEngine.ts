import type { AnalysisQuery, QueryResult, ChartFilter } from '@dataviz/shared-types'
import { executeQuery } from '@dataviz/api-client'
import { QueryCache } from './QueryCache'
import { QueryParser } from './QueryParser'

/**
 * 客户端查询引擎
 */
export class QueryEngine {
  private cache: QueryCache
  private parser: QueryParser

  constructor(cacheSize = 50) {
    this.cache = new QueryCache(cacheSize)
    this.parser = new QueryParser()
  }

  /**
   * 执行查询 (带缓存)
   */
  async execute(query: AnalysisQuery, useCache = true): Promise<QueryResult> {
    const cacheKey = this.cache.buildKey(query)
    if (useCache) {
      const cached = this.cache.get(cacheKey)
      if (cached) return cached
    }
    const result = await executeQuery(query)
    if (useCache) {
      this.cache.set(cacheKey, result)
    }
    return result
  }

  /**
   * 执行多次查询并合并结果 (并行)
   */
  async executeBatch(queries: AnalysisQuery[]): Promise<QueryResult[]> {
    return Promise.all(queries.map((q) => this.execute(q)))
  }

  /**
   * 清除缓存
   */
  clearCache(): void {
    this.cache.clear()
  }

  /**
   * 将图表过滤器与已有查询合并
   */
  mergeFilters(query: AnalysisQuery, filters: ChartFilter[]): AnalysisQuery {
    return {
      ...query,
      filters: [...(query.filters || []), ...filters],
    }
  }

  /**
   * 解析可视化配置到查询
   */
  parseFromVisualConfig(config: unknown): AnalysisQuery {
    return this.parser.parse(config)
  }
}
