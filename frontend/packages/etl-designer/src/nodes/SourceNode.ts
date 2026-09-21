import type { EtlNode, EtlNodeConfig } from '@dataviz/shared-types'
import { NodeRegistry } from '../core/NodeRegistry'

/**
 * SourceNode - 数据源节点定义
 * 负责从数据源读取数据
 */
export class SourceNode {
  static readonly type = 'input' as const

  /**
   * 创建源节点
   */
  static create(
    id: string,
    name: string,
    config: EtlNodeConfig,
    position = { x: 0, y: 0 }
  ): EtlNode {
    return {
      id,
      type: SourceNode.type,
      name,
      x: position.x,
      y: position.y,
      config: {
        datasourceId: config.datasourceId,
        tableName: config.tableName,
        sql: config.sql,
        columns: config.columns,
      },
    }
  }

  /**
   * 获取节点定义
   */
  static getDefinition() {
    return NodeRegistry.getInstance().getDefinition(SourceNode.type)
  }

  /**
   * 验证节点配置
   */
  static validate(node: EtlNode): { valid: boolean; errors: string[] } {
    const errors: string[] = []

    if (!node.config.datasourceId) {
      errors.push('数据源未选择')
    }

    if (!node.config.tableName && !node.config.sql) {
      errors.push('请选择表名或输入 SQL 查询')
    }

    return { valid: errors.length === 0, errors }
  }

  /**
   * 获取节点标签
   */
  static getLabel(node: EtlNode): string {
    if (node.config.tableName) return node.config.tableName
    if (node.config.sql) return 'SQL 查询'
    return node.name
  }
}
