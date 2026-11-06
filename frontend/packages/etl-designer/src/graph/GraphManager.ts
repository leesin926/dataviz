import { Graph, MiniMap, Snapline, type Node as X6Node } from '@antv/x6'
import type { EtlDag, EtlNode, EtlEdge } from '@dataviz/shared-types'
import { buildMiniMapOptions } from '../panels/minimapOptions'

/**
 * X6 图管理
 */
export class GraphManager {
  private graph: Graph | null = null
  private nodeSelectHandler: ((node: EtlNode) => void) | null = null
  private miniMap: MiniMap | null = null
  private miniMapContainer: HTMLElement | null = null

  init(container: HTMLElement): void {
    this.graph = new Graph({
      container,
      grid: { visible: true, size: 10, type: 'dot' },
      // 框选/多选需要 @antv/x6-plugin-selection（peer 仍写 x6 ^2.x）；原先的 selecting 选项在 v3 会被静默忽略
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
    // snapline 在 v3 里是插件而不是 Graph 选项，写在选项字面量里会被静默忽略
    this.graph.use(new Snapline({ enabled: true }))
    // 小地图容器可能先于 graph 挂载（子组件 onMounted 早于父组件），init() 这里补装
    this.installMiniMap()
  }

  /**
   * 接入 X6 小地图插件（`MiniMap` 在 v3 已并入 `@antv/x6` 主包，无独立插件包）。
   * 传入 null 表示面板卸载。
   * @returns graph 已存在则当场装上；否则只记下容器，等 init() 建好 graph 再装
   */
  attachMiniMap(container: HTMLElement | null): boolean {
    this.uninstallMiniMap()
    this.miniMapContainer = container
    return this.installMiniMap()
  }

  /** 面板卸载：只摘插件，graph 的释放交给 dispose() */
  detachMiniMap(): void {
    this.uninstallMiniMap()
    this.miniMapContainer = null
  }

  isMiniMapAttached(): boolean {
    return this.graph != null && this.miniMap != null
  }

  onNodeSelect(handler: (node: EtlNode) => void): void {
    this.nodeSelectHandler = handler
    this.graph?.on('node:click', ({ node }) => {
      const etlNode = node.getData<{ etlNode?: EtlNode }>().etlNode
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
        body: { fill: this.getColorByType(etlNode.type), stroke: '#5F95FF', rx: 6, ry: 6 },
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
      const etl = cell.getData<{ etlNode?: EtlNode }>().etlNode
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
    // graph.dispose() 会遍历 installedPlugins 逐个 dispose（含 MiniMap），此处不能再手动 dispose 插件
    this.graph?.dispose()
    this.graph = null
    this.miniMap = null
    this.miniMapContainer = null
  }

  /**
   * 装上小地图：尺寸在装上那一刻按面板容器的实际像素采样。
   * 主画布目前同样只在 init() 时取一次 clientWidth/clientHeight，所以窗口缩放后两者一起失准；
   * 要跟随布局变化，只需在布局变化时调用 graph.resize(w, h) —— 它会触发 resize 事件，
   * 插件据此重算 画布→小地图 的缩放比例，小地图自身的像素框仍保持面板给的大小。
   */
  private installMiniMap(): boolean {
    if (!this.graph || !this.miniMapContainer) return false
    this.uninstallMiniMap()
    const container = this.miniMapContainer
    const options = buildMiniMapOptions(container, {
      width: container.clientWidth,
      height: container.clientHeight,
    })
    const miniMap = new MiniMap(options)
    this.graph.use(miniMap)
    this.miniMap = miniMap
    return true
  }

  private uninstallMiniMap(): void {
    if (!this.miniMap) return
    // disposePlugins 会调用 plugin.dispose() 并把它从 installedPlugins 中移除，避免残留引用重复释放
    this.graph?.disposePlugins(this.miniMap.name)
    this.miniMap = null
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
