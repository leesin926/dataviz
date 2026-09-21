import { BaseNode, type BaseNodeDefinition } from './BaseNode'
import type { EtlNodeConfig } from '@dataviz/shared-types'

/**
 * 数据输出节点 (写入目标)
 */
export class OutputNode extends BaseNode {
  definition: BaseNodeDefinition = {
    type: 'output',
    label: '数据输出',
    icon: 'export',
    color: '#faad14',
    defaultConfig: {
      datasourceId: undefined,
      tableName: '',
      script: 'insert',
    },
    ports: { in: true, out: false },
  }

  static toTable(datasourceId: string | number, tableName: string, mode: 'insert' | 'upsert' | 'replace' = 'insert'): EtlNodeConfig {
    return { datasourceId, tableName, script: mode }
  }
}
