import type { EtlNode, EtlNodeConfig, OperatorType } from '@dataviz/shared-types'

/**
 * 转换节点支持的类型
 */
const TRANSFORM_TYPES: OperatorType[] = [
  'transform', 'filter', 'join', 'aggregate',
  'union', 'sort', 'limit', 'sql', 'script',
]

/**
 * TransformNode - 转换节点定义
 * 支持 filter、map、join、aggregate 等转换操作
 */
export class TransformNode {
  /**
   * 创建转换节点
   */
  static create(
    type: OperatorType,
    id: string,
    name: string,
    config: EtlNodeConfig,
    position = { x: 0, y: 0 }
  ): EtlNode {
    if (!TRANSFORM_TYPES.includes(type)) {
      throw new Error(`Invalid transform type: ${type}`)
    }

    return {
      id,
      type,
      name,
      x: position.x,
      y: position.y,
      config,
    }
  }

  /**
   * 验证节点配置
   */
  static validate(node: EtlNode): { valid: boolean; errors: string[] } {
    const errors: string[] = []

    switch (node.type) {
      case 'filter':
        if (!node.config.filters || (Array.isArray(node.config.filters) && node.config.filters.length === 0)) {
          errors.push('过滤条件不能为空')
        }
        break
      case 'join':
        if (!node.config.joinType) errors.push('请选择关联类型')
        if (!node.config.leftKey) errors.push('请指定左表键')
        if (!node.config.rightKey) errors.push('请指定右表键')
        break
      case 'aggregate':
        if (!node.config.groupBy) errors.push('请指定分组字段')
        break
      case 'sql':
        if (!node.config.sql) errors.push('SQL 语句不能为空')
        break
      case 'script':
        if (!node.config.script) errors.push('脚本内容不能为空')
        break
      case 'limit':
        if (!node.config.limit || (node.config.limit as number) <= 0) {
          errors.push('行数必须大于 0')
        }
        break
    }

    return { valid: errors.length === 0, errors }
  }

  /**
   * 获取节点描述
   */
  static getDescription(node: EtlNode): string {
    switch (node.type) {
      case 'filter':
        return `过滤: ${JSON.stringify(node.config.filters || [])}`
      case 'join':
        return `${node.config.joinType || 'inner'} join on ${node.config.leftKey} = ${node.config.rightKey}`
      case 'aggregate':
        return `聚合: group by ${JSON.stringify(node.config.groupBy || [])}`
      case 'sql':
        return `SQL: ${(node.config.sql || '').slice(0, 50)}...`
      case 'sort':
        return `排序: ${JSON.stringify(node.config.sortFields || [])}`
      case 'limit':
        return `限制: ${node.config.limit} 行`
      default:
        return node.name
    }
  }
}
