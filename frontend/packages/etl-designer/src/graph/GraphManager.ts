import { Graph, type Cell, type Node as X6Node } from '@antv/x6'
import type { EtlDag, EtlNode, EtlEdge } from '@dataviz/shared-types'

/**
 * X6 图管理
 */
export class GraphManager {
  private graph: Graph | null = null
  private nodeSelectHandler: ((node: EtlNode) => void) | null = null

  init(container: HTMLElement): void {
    this.graph = new Graph({
      container,
      grid: { visible: true, size: 10, type: 'dot' },
      snapline: { enabled: true },
      selecting: { enabled: true, rubberband: true },
      connecting: {
        router: 'manhattan',
        connector: 'rounded',
        snap: { radius: 20 },
        allowBlank: false,
        allowLoop: false,
      },
      mousewheel: { enabled: true, zoomAtMousePosition: true, modifiers: 'ctrl' },
      panning: { enabled: true },
      highlighting: {
        magnetAvailable: { name: 'stroke', args: { padding: 4, attrs: { 'stroke-width': 2, stroke: '#5F95FF' } } },
      },
    })
  }

  onNodeSelect(handler: (node: EtlNode) => void): void {
    this.nodeSelectHandler = handler
    this.graph?.on('node:click', ({ node }) => {
      const etlNode = node.getData<EtlNode>('etlNode')
      if (etlNode && this.nodeSelectHandler) this.nodeSelectHandler(etlNode)
    })
  }

  addNode(etlNode: EtlNode): void {
    if (!this.graph) return
    const node = this.graph.createNode({
      id: etlNode.id,
      x: etlNode.x,
      y: etlNode.y,
      shape: 'rect',
      width: 140,
      height: 50,
      label: etlNode.name,
      attrs: {
        body: { fill: this.getColorByType(etlNode.type), stroke: '#5F95FF, rx: 6, ry: 6' },
        text: { fill: '#fff', fontSize: 13 },
      },
      ports: this.buildPorts(etlNode),
      data: { etlNode },
    })
    this.graph.addNode(node)
  }

  updateNode(etlNode: EtlNode): void {
    if (!this.graph) return
    const cell = this.graph.getCellById(etlNode.id) as X6Node
    if (cell) {
      cell.setData({ etlNode })
    }
  }

  addEdge(etlEdge: EtlEdge): void {
    if (!this.graph) return
    this.graph.addEdge({
      id: etlEdge.id,
      source: { cell: etlEdge.source, port: etlEdge.sourcePort || 'out' },
      target: { cell: etlEdge.target, port: etlEdge.targetPort || 'in' },
      router: 'manhattan',
      connector: 'rounded',
    })
  }

  loadDag(dag: EtlDag): void {
    if (!this.graph) return
    this.clear()
    dag.nodes.forEach((n) => this.addNode(n))
    dag.edges.forEach((e) => this.addEdge(e))
  }

  toDag(): EtlDag {
    if (!this.graph) return { nodes: [], edges: [] }
    const nodes: EtlNode[] = []
    const edges: EtlEdge[] = []
    this.graph.getNodes().forEach((cell) => {
      const etl = cell.getData<EtlNode>('etlNode')
      if (etl) {
        const pos = cell.getPosition()
        nodes.push({ ...etl, x: pos.x, y: pos.y })
      }
    })
    this.graph.getEdges().forEach((cell) => {
      const src = cell.getSource() as { cell?: string; port?: string }
      const tgt = cell.getTarget() as { cell?: string; port?: string }
      edges.push({
        id: cell.id,
        source: src.cell || '',
        target: tgt.cell || '',
        sourcePort: src.port,
        targetPort: tgt.port,
      })
    })
    return { nodes, edges }
  }

  clear(): void {
    this.graph?.clearCells()
  }

  dispose(): void {
    this.graph?.dispose()
    this.graph = null
  }

  private getColorByType(type: string): string {
    const map: Record<string, string> = {
      input: '#52c41a',
      transform: '#1890ff',
      output: '#faad14',
      filter: '#722ed1',
      join: '#eb2f96',
      aggregate: '#13c2c2',
      union: '#2f54eb',
      sort: '#fa8c16',
      limit: '#a0d911',
      sql: '#f5222d',
      script: '#595959',
    }
    return map[type] || '#5F95FF'
  }

  private buildPorts(etlNode: EtlNode): object {
    const isInput = etlNode.type === 'input'
    const isOutput = etlNode.type === 'output'
    return {
      groups: {
        in: {
          position: 'left',
          attrs: { circle: { r: 5, magnet: true, stroke: '#5F95FF', fill: '#fff' } },
        },
        out: {
          position: 'right',
          attrs: { circle: { r: 5, magnet: true, stroke: '#5F95FF', fill: '#fff' } },
        },
      },
      items: [
        ...(isInput ? [] : [{ group: 'in', id: 'in' }]),
        ...(isOutput ? [] : [{ group: 'out', id: 'out' }]),
      ],
    }
  }
}
