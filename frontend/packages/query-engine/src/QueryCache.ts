import type { AnalysisQuery, QueryResult } from '@dataviz/shared-types'

interface CacheEntry {
  key: string
  value: QueryResult
  timestamp: number
}

/**
 * LRU 查询缓存
 */
export class QueryCache {
  private capacity: number
  private cache: Map<string, CacheEntry>

  constructor(capacity = 50) {
    this.capacity = capacity
    this.cache = new Map()
  }

  /**
   * 生成缓存 key
   */
  buildKey(query: AnalysisQuery): string {
    return JSON.stringify({
      datasetId: query.datasetId,
      dimensions: query.dimensions,
      measures: query.measures,
      filters: query.filters,
      sorts: query.sorts,
      limit: query.limit,
      offset: query.offset,
    })
  }

  get(key: string): QueryResult | null {
    const entry = this.cache.get(key)
    if (!entry) return null
    // 移到最后 (LRU)
    this.cache.delete(key)
    this.cache.set(key, entry)
    return entry.value
  }

  set(key: string, value: QueryResult): void {
    if (this.cache.has(key)) {
      this.cache.delete(key)
    } else if (this.cache.size >= this.capacity) {
      // 删除最旧
      const firstKey = this.cache.keys().next().value
      if (firstKey !== undefined) this.cache.delete(firstKey)
    }
    this.cache.set(key, { key, value, timestamp: Date.now() })
  }

  has(key: string): boolean {
    return this.cache.has(key)
  }

  delete(key: string): void {
    this.cache.delete(key)
  }

  clear(): void {
    this.cache.clear()
  }

  size(): number {
    return this.cache.size
  }
}
