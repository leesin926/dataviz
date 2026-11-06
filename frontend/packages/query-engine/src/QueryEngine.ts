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
  /** 在途请求表：按 (datasetId + 序列化查询) 建键，并发同参调用共享同一个 promise（API-142）。
   *  这是"在途"的去重、不是结果缓存 —— 结算即移除，之后的同参调用会重新打后端。 */
  private inflight: Map<string, Promise<QueryResult>>

  constructor(cacheSize = 50) {
    this.cache = new QueryCache(cacheSize)
    this.parser = new QueryParser()
    this.inflight = new Map()
  }

  /**
   * 执行查询 (带缓存)
   */
  async execute(query: AnalysisQuery, useCache = true): Promise<QueryResult> {
    const cacheKey = this.cache.buildKey(query)
    if (useCache) {
      const cached = this.cache.get(cacheKey)
      if (cached) return cached
      const pending = this.inflight.get(cacheKey)
      if (pending) return pending
    }

    const promise = (async () => {
      const result = await executeQuery(query)
      if (useCache) {
        this.cache.set(cacheKey, result)
      }
      return result
    })()

    if (useCache) {
      this.inflight.set(cacheKey, promise)
      const release = () => {
        // 只清自己这一条：结算期间可能已有新的同 key 请求挂上，不能把它误删
        if (this.inflight.get(cacheKey) === promise) this.inflight.delete(cacheKey)
      }
      void promise.then(release, release)
    }
    return promise
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
