import type { EtlNode, EtlNodeConfig } from '@dataviz/shared-types'

/**
 * 基础节点定义
 */
export interface BaseNodeDefinition {
  type: string
  label: string
  icon: string
  color: string
  defaultConfig: EtlNodeConfig
  ports: { in: boolean; out: boolean }
}

export abstract class BaseNode {
  abstract definition: BaseNodeDefinition

  toEtlNode(x: number, y: number, overrides?: Partial<EtlNode>): EtlNode {
    const id = `node_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`
    return {
      id,
      type: this.definition.type as EtlNode['type'],
      name: this.definition.label,
      x,
      y,
      config: { ...this.definition.defaultConfig },
      ...overrides,
    }
  }
}
