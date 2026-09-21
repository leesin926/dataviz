import { BaseNode, type BaseNodeDefinition } from './BaseNode'
import type { EtlNodeConfig } from '@dataviz/shared-types'

/**
 * 数据输入节点 (数据源读取)
 */
export class InputNode extends BaseNode {
  definition: BaseNodeDefinition = {
    type: 'input',
    label: '数据输入',
    icon: 'database',
    color: '#52c41a',
    defaultConfig: {
      datasourceId: undefined,
      tableName: '',
      columns: [],
    },
    ports: { in: false, out: true },
  }

  static fromTable(datasourceId: string | number, tableName: string): EtlNodeConfig {
    return { datasourceId, tableName, columns: [] }
  }

  static fromSql(datasourceId: string | number, sql: string): EtlNodeConfig {
    return { datasourceId, sql, columns: [] }
  }
}
