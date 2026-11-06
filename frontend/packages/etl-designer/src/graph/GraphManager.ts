import { Graph, MiniMap, Selection, Snapline, type Node as X6Node } from '@antv/x6'
import type { EtlDag, EtlNode, EtlEdge } from '@dataviz/shared-types'
import { buildMiniMapOptions } from '../panels/minimapOptions'
import { buildPanningOptions } from './panningOptions'
import { buildSelectionOptions, SELECTION_PLUGIN_NAME } from './selectionOptions'

/**
 * X6 图管理
 */
export class GraphManager {
  private graph: Graph | null = null
  private nodeSelectHandler: ((node: EtlNode) => void) | null = null
  private selectionHandler: ((nodes: EtlNode[]) => void) | null = null
  private selectionInstalled = false
  private miniMap: MiniMap | null = null
  private miniMapContainer: HTMLElement | null = null

  init(container: HTMLElement): void {
    this.graph = new Graph({
      container,
      grid: { visible: true, size: 10, type: 'dot' },
      // API-137：框选/多选不是 Graph 选项。v3 里根本没有 `selecting` 这个 Graph 选项（写了会被静默忽略，
      // 就是原缺陷的成因），也不需要任何 @antv/x6-plugin-selection 依赖包（已废弃、且不发布 JS）。
      // 正解是把核心导出的 Selection 插件 graph.use(new Selection(...)) 装上，见 installSelection()。
      connecting: {
        router: 'manhattan',
        connector: 'rounded',
        snap: { radius: 20 },
        allowBlank: false,
        allowLoop: false,
      },
      mousewheel: { enabled: true, zoomAtMousePosition: true, modifiers: 'ctrl' },
      // 左键/右键拖拽平移；框选改挂 shift，两套手势不撞车（理由见 panningOptions.ts 与 selectionOptions.ts）
      panning: buildPanningOptions(),
      highlighting: {
        magnetAvailable: { name: 'stroke', args: { padding: 4, attrs: { 'stroke-width': 2, stroke: '#5F95FF' } } },
      },
    })
    // snapline 在 v3 里是插件而不是 Graph 选项，写在选项字面量里会被静默忽略
    this.graph.use(new Snapline({ enabled: true }))
    this.installSelection()
    // 小地图容器可能先于 graph 挂载（子组件 onMounted 早于父组件），init() 这里补装
    this.installMiniMap()
  }

  /**
   * 装上框选/多选插件（`Selection` 在 v3 已并入 `@antv/x6` 主包，无独立插件包）。
   * shift + 空画布左键拖拽即框选、框中多节点一起拖动；ctrl/meta/shift 点击可加选。
   * 左键/右键拖拽仍归平移：框选的 modifiers 与 panning 的不同，
   * x6 的 `resolvePanningSelectionConflict()` 才不会在装上插件那一刻把平移关掉。
   * @returns graph 已存在则当场装上；re-init 时先摘再装，避免重复注册
   */
  installSelection(): boolean {
    if (!this.graph) return false
    this.uninstallSelection()
    this.graph.use(new Selection(buildSelectionOptions()))
    // 兜底告警：上面那套 modifiers/eventTypes 若被改回"两边都不要修饰键"，x6 会静默 disablePanning()，
    // 画布就只剩框选、平移整个没了。把手势的取舍变成显式可见，而不是等用户在浏览器里发现拖不动画布。
    if (this.graph.options.panning.enabled === false) {
      console.warn(
        '[etl-designer] 框选配置使 x6 关掉了空白拖拽平移，检查 selectionOptions/panningOptions 手势分工',
      )
    }
    this.selectionInstalled = true
    this.bindSelectionEvents()
    return true
  }

  /** 卸载框选插件：按插件名从 installedPlugins 释放，避免残留引用重复释放 */
  uninstallSelection(): void {
    if (!this.selectionInstalled) return
    this.graph?.disposePlugins(SELECTION_PLUGIN_NAME)
    this.selectionInstalled = false
  }

  isSelectionInstalled(): boolean {
    return this.graph != null && this.selectionInstalled
  }

  /**
   * 选中集变化回调（多选场景用）。单选仍走 onNodeSelect 的 node:click；
   * 多选时把选中的 EtlNode 数组交给消费者，由面板决定显示（见 EtlDesigner.vue）。
   */
  onSelectionChange(handler: (nodes: EtlNode[]) => void): void {
    this.selectionHandler = handler
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
    // graph.dispose() 会遍历 installedPlugins 逐个 dispose（含 MiniMap 与 Selection），此处不能再手动 dispose 插件
    this.graph?.dispose()
    this.graph = null
    this.selectionInstalled = false
    this.miniMap = null
    this.miniMapContainer = null
  }

  /**
   * 把 Selection 插件的 selection:changed 事件桥接给消费者。
   * v3 里插件会把自身事件同时 graph.trigger 到 graph 上，所以这里直接 graph.on('selection:changed')。
   * 只挑出节点（忽略边），映射回 EtlNode；单选/多选的显示决策交给 selectionHandler。
   */
  private bindSelectionEvents(): void {
    if (!this.graph) return
    this.graph.on('selection:changed', ({ selected }) => {
      if (!this.selectionHandler) return
      const nodes: EtlNode[] = []
      selected.forEach((cell) => {
        if (!cell.isNode()) return
        const etl = cell.getData<{ etlNode?: EtlNode }>().etlNode
        if (etl) nodes.push(etl)
      })
      this.selectionHandler(nodes)
    })
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
