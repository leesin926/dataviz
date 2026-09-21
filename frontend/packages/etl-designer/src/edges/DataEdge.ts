import type { EtlEdge } from '@dataviz/shared-types'

/**
 * 数据边定义
 */
export interface DataEdgeOptions {
  color?: string
  dashed?: boolean
  animated?: boolean
  label?: string
}

export class DataEdge {
  static create(source: string, target: string, options: DataEdgeOptions = {}): EtlEdge {
    return {
      id: `edge_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`,
      source,
      target,
      sourcePort: 'out',
      targetPort: 'in',
    }
  }

  static validate(source: string, target: string, existingEdges: EtlEdge[]): boolean {
    // 不允许自连接
    if (source === target) return false
    // 不允许重复边
    if (existingEdges.some((e) => e.source === source && e.target === target)) return false
    return true
  }
}
