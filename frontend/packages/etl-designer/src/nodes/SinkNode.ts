import type { EtlNode, EtlNodeConfig } from '@dataviz/shared-types'

/**
 * SinkNode - 数据汇节点定义
 * 负责将数据写入目标存储
 */
export class SinkNode {
  static readonly type = 'output' as const

  /**
   * 创建汇节点
   */
  static create(
    id: string,
    name: string,
    config: EtlNodeConfig,
    position = { x: 0, y: 0 }
  ): EtlNode {
    return {
      id,
      type: SinkNode.type,
      name,
      x: position.x,
      y: position.y,
      config: {
        datasourceId: config.datasourceId,
        tableName: config.tableName,
        writeMode: config.writeMode || 'append',
      },
    }
  }

  /**
   * 验证节点配置
   */
  static validate(node: EtlNode): { valid: boolean; errors: string[] } {
    const errors: string[] = []

    if (!node.config.datasourceId) {
      errors.push('目标数据源未选择')
    }

    if (!node.config.tableName) {
      errors.push('目标表名不能为空')
    }

    const validModes = ['append', 'overwrite', 'upsert']
    if (node.config.writeMode && !validModes.includes(node.config.writeMode as string)) {
      errors.push('无效的写入模式')
    }

    return { valid: errors.length === 0, errors }
  }

  /**
   * 获取节点标签
   */
  static getLabel(node: EtlNode): string {
    const mode = node.config.writeMode || 'append'
    const table = node.config.tableName || '未配置'
    return `${table} (${mode})`
  }
}
