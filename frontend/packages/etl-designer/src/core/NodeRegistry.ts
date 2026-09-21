import type { OperatorType } from '@dataviz/shared-types'

/**
 * 节点定义
 */
export interface NodeDefinition {
  type: OperatorType
  label: string
  icon: string
  category: 'source' | 'transform' | 'sink'
  description: string
  configFields: ConfigField[]
  inputs: number   // 输入端口数
  outputs: number  // 输出端口数
}

export interface ConfigField {
  key: string
  label: string
  type: 'string' | 'number' | 'boolean' | 'select' | 'textarea' | 'sql' | 'datasource' | 'table' | 'columns'
  required?: boolean
  defaultValue?: unknown
  options?: Array<{ label: string; value: string | number }>
  placeholder?: string
}

/**
 * NodeRegistry - ETL 节点类型注册表
 */
export class NodeRegistry {
  private static instance: NodeRegistry
  private definitions: Map<OperatorType, NodeDefinition> = new Map()

  private constructor() {
    this.registerBuiltInNodes()
  }

  static getInstance(): NodeRegistry {
    if (!NodeRegistry.instance) {
      NodeRegistry.instance = new NodeRegistry()
    }
    return NodeRegistry.instance
  }

  /**
   * 注册节点类型
   */
  register(definition: NodeDefinition): void {
    this.definitions.set(definition.type, definition)
  }

  /**
   * 获取节点定义
   */
  getDefinition(type: OperatorType): NodeDefinition | undefined {
    return this.definitions.get(type)
  }

  /**
   * 获取所有注册类型
   */
  getAllDefinitions(): NodeDefinition[] {
    return Array.from(this.definitions.values())
  }

  /**
   * 按类别获取节点
   */
  getByCategory(category: 'source' | 'transform' | 'sink'): NodeDefinition[] {
    return this.getAllDefinitions().filter((d) => d.category === category)
  }

  /**
   * 生成唯一 ID
   */
  generateNodeId(type: OperatorType): string {
    const prefix = type.charAt(0).toUpperCase() + type.slice(1)
    return `${prefix}_${Date.now()}_${Math.random().toString(36).slice(2, 7)}`
  }

  /**
   * 注册内置节点类型
   */
  private registerBuiltInNodes(): void {
    // Source nodes
    this.register({
      type: 'input',
      label: '数据输入',
      icon: 'download',
      category: 'source',
      description: '从数据源读取数据',
      inputs: 0,
      outputs: 1,
      configFields: [
        { key: 'datasourceId', label: '数据源', type: 'datasource', required: true },
        { key: 'tableName', label: '表名', type: 'table' },
        { key: 'sql', label: 'SQL 查询', type: 'sql' },
        { key: 'columns', label: '选择列', type: 'columns' },
      ],
    })

    // Transform nodes
    this.register({
      type: 'filter',
      label: '数据过滤',
      icon: 'filter',
      category: 'transform',
      description: '根据条件过滤数据行',
      inputs: 1,
      outputs: 1,
      configFields: [
        { key: 'filters', label: '过滤条件', type: 'textarea' },
      ],
    })

    this.register({
      type: 'transform',
      label: '字段转换',
      icon: 'edit',
      category: 'transform',
      description: '映射、重命名、类型转换字段',
      inputs: 1,
      outputs: 1,
      configFields: [
        { key: 'mappings', label: '字段映射', type: 'textarea' },
      ],
    })

    this.register({
      type: 'join',
      label: '数据关联',
      icon: 'connection',
      category: 'transform',
      description: '关联两个数据流',
      inputs: 2,
      outputs: 1,
      configFields: [
        { key: 'joinType', label: '关联类型', type: 'select', options: [
          { label: 'Inner Join', value: 'inner' },
          { label: 'Left Join', value: 'left' },
          { label: 'Right Join', value: 'right' },
          { label: 'Full Join', value: 'full' },
        ]},
        { key: 'leftKey', label: '左表键', type: 'string', required: true },
        { key: 'rightKey', label: '右表键', type: 'string', required: true },
      ],
    })

    this.register({
      type: 'aggregate',
      label: '数据聚合',
      icon: 'data-analysis',
      category: 'transform',
      description: '分组聚合数据',
      inputs: 1,
      outputs: 1,
      configFields: [
        { key: 'groupBy', label: '分组字段', type: 'columns' },
        { key: 'aggregations', label: '聚合方式', type: 'textarea' },
      ],
    })

    this.register({
      type: 'union',
      label: '数据合并',
      icon: 'copy-document',
      category: 'transform',
      description: '合并多个数据流',
      inputs: 2,
      outputs: 1,
      configFields: [
        { key: 'unionAll', label: '保留重复', type: 'boolean', defaultValue: false },
      ],
    })

    this.register({
      type: 'sort',
      label: '数据排序',
      icon: 'sort',
      category: 'transform',
      description: '对数据排序',
      inputs: 1,
      outputs: 1,
      configFields: [
        { key: 'sortFields', label: '排序字段', type: 'textarea' },
      ],
    })

    this.register({
      type: 'limit',
      label: '数据限制',
      icon: 'minus',
      category: 'transform',
      description: '限制数据行数',
      inputs: 1,
      outputs: 1,
      configFields: [
        { key: 'limit', label: '行数', type: 'number', required: true },
        { key: 'offset', label: '偏移量', type: 'number', defaultValue: 0 },
      ],
    })

    this.register({
      type: 'sql',
      label: 'SQL 脚本',
      icon: 'document',
      category: 'transform',
      description: '使用 SQL 处理数据',
      inputs: 1,
      outputs: 1,
      configFields: [
        { key: 'sql', label: 'SQL 语句', type: 'sql', required: true },
      ],
    })

    this.register({
      type: 'script',
      label: '自定义脚本',
      icon: 'cpu',
      category: 'transform',
      description: '使用脚本处理数据',
      inputs: 1,
      outputs: 1,
      configFields: [
        { key: 'script', label: '脚本内容', type: 'textarea', required: true },
        { key: 'language', label: '脚本语言', type: 'select', options: [
          { label: 'JavaScript', value: 'javascript' },
          { label: 'Python', value: 'python' },
        ]},
      ],
    })

    // Sink nodes
    this.register({
      type: 'output',
      label: '数据输出',
      icon: 'upload',
      category: 'sink',
      description: '将数据写入目标',
      inputs: 1,
      outputs: 0,
      configFields: [
        { key: 'datasourceId', label: '目标数据源', type: 'datasource', required: true },
        { key: 'tableName', label: '目标表', type: 'table', required: true },
        { key: 'writeMode', label: '写入模式', type: 'select', options: [
          { label: '追加', value: 'append' },
          { label: '覆盖', value: 'overwrite' },
          { label: '更新', value: 'upsert' },
        ]},
      ],
    })
  }
}
