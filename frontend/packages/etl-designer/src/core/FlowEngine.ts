import type { EtlDag, EtlNode, EtlEdge } from '@dataviz/shared-types'

/**
 * FlowEngine - DAG-based ETL 流程引擎
 * 负责节点执行顺序计算、流程验证、执行调度
 */
export class FlowEngine {
  private nodes: Map<string, EtlNode> = new Map()
  private edges: EtlEdge[] = []

  constructor(dag?: EtlDag) {
    if (dag) {
      this.loadDag(dag)
    }
  }

  /**
   * 加载 DAG
   */
  loadDag(dag: EtlDag): void {
    this.nodes.clear()
    this.edges = []
    dag.nodes.forEach((node) => this.nodes.set(node.id, { ...node }))
    this.edges = [...dag.edges]
  }

  /**
   * 导出 DAG
   */
  exportDag(): EtlDag {
    return {
      nodes: Array.from(this.nodes.values()),
      edges: [...this.edges],
    }
  }

  /**
   * 添加节点
   */
  addNode(node: EtlNode): void {
    this.nodes.set(node.id, node)
  }

  /**
   * 移除节点
   */
  removeNode(id: string): void {
    this.nodes.delete(id)
    this.edges = this.edges.filter((e) => e.source !== id && e.target !== id)
  }

  /**
   * 更新节点
   */
  updateNode(id: string, patch: Partial<EtlNode>): void {
    const node = this.nodes.get(id)
    if (node) {
      Object.assign(node, patch)
    }
  }

  /**
   * 添加边
   */
  addEdge(edge: EtlEdge): void {
    // 检查是否存在环路
    if (this.wouldCreateCycle(edge.source, edge.target)) {
      throw new Error('Adding this edge would create a cycle in the DAG')
    }
    this.edges.push(edge)
  }

  /**
   * 移除边
   */
  removeEdge(id: string): void {
    this.edges = this.edges.filter((e) => e.id !== id)
  }

  /**
   * 获取节点
   */
  getNode(id: string): EtlNode | undefined {
    return this.nodes.get(id)
  }

  /**
   * 获取所有节点
   */
  getNodes(): EtlNode[] {
    return Array.from(this.nodes.values())
  }

  /**
   * 获取所有边
   */
  getEdges(): EtlEdge[] {
    return [...this.edges]
  }

  /**
   * 计算执行顺序（拓扑排序）
   */
  getExecutionOrder(): string[] {
    const inDegree = new Map<string, number>()
    const adjacency = new Map<string, string[]>()

    // 初始化
    this.nodes.forEach((_, id) => {
      inDegree.set(id, 0)
      adjacency.set(id, [])
    })

    // 计算入度和邻接表
    this.edges.forEach((edge) => {
      inDegree.set(edge.target, (inDegree.get(edge.target) || 0) + 1)
      adjacency.get(edge.source)?.push(edge.target)
    })

    // BFS 拓扑排序
    const queue: string[] = []
    inDegree.forEach((degree, id) => {
      if (degree === 0) queue.push(id)
    })

    const order: string[] = []
    while (queue.length > 0) {
      const id = queue.shift()!
      order.push(id)
      adjacency.get(id)?.forEach((targetId) => {
        const newDegree = (inDegree.get(targetId) || 1) - 1
        inDegree.set(targetId, newDegree)
        if (newDegree === 0) {
          queue.push(targetId)
        }
      })
    }

    if (order.length !== this.nodes.size) {
      throw new Error('DAG contains a cycle - topological sort impossible')
    }

    return order
  }

  /**
   * 获取节点的上游节点
   */
  getUpstream(nodeId: string): EtlNode[] {
    const upstreamIds = this.edges
      .filter((e) => e.target === nodeId)
      .map((e) => e.source)
    return upstreamIds
      .map((id) => this.nodes.get(id))
      .filter((n): n is EtlNode => n !== undefined)
  }

  /**
   * 获取节点的下游节点
   */
  getDownstream(nodeId: string): EtlNode[] {
    const downstreamIds = this.edges
      .filter((e) => e.source === nodeId)
      .map((e) => e.target)
    return downstreamIds
      .map((id) => this.nodes.get(id))
      .filter((n): n is EtlNode => n !== undefined)
  }

  /**
   * 验证 DAG 是否有效
   */
  validate(): { valid: boolean; errors: string[] } {
    const errors: string[] = []

    // 检查是否有节点
    if (this.nodes.size === 0) {
      errors.push('Flow has no nodes')
    }

    // 检查是否有源节点
    const sourceNodes = this.getNodes().filter((n) => n.type === 'input')
    if (sourceNodes.length === 0) {
      errors.push('Flow has no source (input) node')
    }

    // 检查是否有汇节点
    const sinkNodes = this.getNodes().filter((n) => n.type === 'output')
    if (sinkNodes.length === 0) {
      errors.push('Flow has no sink (output) node')
    }

    // 检查环路
    try {
      this.getExecutionOrder()
    } catch {
      errors.push('Flow contains a cycle')
    }

    // 检查孤立节点
    this.nodes.forEach((node) => {
      const hasIn = this.edges.some((e) => e.target === node.id)
      const hasOut = this.edges.some((e) => e.source === node.id)
      if (!hasIn && !hasOut && this.nodes.size > 1) {
        errors.push(`Node "${node.name}" is disconnected`)
      }
    })

    return { valid: errors.length === 0, errors }
  }

  /**
   * 检查添加边是否会创建环
   */
  private wouldCreateCycle(source: string, target: string): boolean {
    if (source === target) return true

    // 从 target 出发，看能否到达 source
    const visited = new Set<string>()
    const stack = [target]
    const adjacency = new Map<string, string[]>()

    this.edges.forEach((edge) => {
      if (!adjacency.has(edge.source)) adjacency.set(edge.source, [])
      adjacency.get(edge.source)!.push(edge.target)
    })
    // 加上候选边
    if (!adjacency.has(source)) adjacency.set(source, [])
    adjacency.get(source)!.push(target)

    while (stack.length > 0) {
      const id = stack.pop()!
      if (id === source) return true
      if (visited.has(id)) continue
      visited.add(id)
      adjacency.get(id)?.forEach((next) => stack.push(next))
    }

    return false
  }
}
