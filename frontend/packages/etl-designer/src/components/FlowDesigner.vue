<template>
  <div class="flow-designer">
    <!-- Left sidebar: Node palette -->
    <div class="node-palette">
      <div class="palette-header">
        <h3>节点列表</h3>
      </div>

      <div v-for="category in nodeCategories" :key="category.key" class="palette-group">
        <div class="group-title">{{ category.label }}</div>
        <div
          v-for="def in category.definitions"
          :key="def.type"
          class="palette-item"
          draggable="true"
          @dragstart="handleDragStart($event, def.type)"
        >
          <span class="palette-icon">{{ getIcon(def.type) }}</span>
          <span class="palette-label">{{ def.label }}</span>
        </div>
      </div>
    </div>

    <!-- Center: Flow canvas -->
    <div class="designer-canvas">
      <FlowCanvas
        :nodes="nodes"
        :edges="edges"
        :selected-node-id="selectedNodeId"
        :selected-edge-id="selectedEdgeId"
        @node-select="handleNodeSelect"
        @node-move="handleNodeMove"
        @edge-select="handleEdgeSelect"
        @connect="handleConnect"
        @canvas-click="handleCanvasClick"
        @drop="handleNodeDrop"
      />
    </div>

    <!-- Right sidebar: Node panel -->
    <NodePanel
      :node="selectedNode"
      @close="selectedNodeId = null"
      @update="handleNodeUpdate"
      @update-config="handleNodeConfigUpdate"
      @delete="handleNodeDelete"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import type { EtlNode, EtlEdge, OperatorType } from '@dataviz/shared-types'
import { FlowEngine } from '../core/FlowEngine'
import { NodeRegistry } from '../core/NodeRegistry'
import FlowCanvas from './FlowCanvas.vue'
import NodePanel from './NodePanel.vue'

const props = defineProps<{
  initialDag?: { nodes: EtlNode[]; edges: EtlEdge[] }
}>()

const emit = defineEmits<{
  (e: 'change', dag: { nodes: EtlNode[]; edges: EtlEdge[] }): void
}>()

const registry = NodeRegistry.getInstance()
const engine = new FlowEngine(props.initialDag)

const nodes = ref<EtlNode[]>(engine.getNodes())
const edges = ref<EtlEdge[]>(engine.getEdges())
const selectedNodeId = ref<string | null>(null)
const selectedEdgeId = ref<string | null>(null)

const selectedNode = computed(() => {
  if (!selectedNodeId.value) return null
  return nodes.value.find((n) => n.id === selectedNodeId.value) || null
})

const nodeCategories = computed(() => {
  const sources = registry.getByCategory('source')
  const transforms = registry.getByCategory('transform')
  const sinks = registry.getByCategory('sink')
  return [
    { key: 'source', label: '数据源', definitions: sources },
    { key: 'transform', label: '转换', definitions: transforms },
    { key: 'sink', label: '数据汇', definitions: sinks },
  ]
})

function getIcon(type: OperatorType): string {
  const def = registry.getDefinition(type)
  return def?.icon || '?'
}

function handleDragStart(e: DragEvent, type: OperatorType): void {
  e.dataTransfer?.setData('nodeType', type)
}

function handleNodeDrop(type: OperatorType, x: number, y: number): void {
  const def = registry.getDefinition(type)
  if (!def) return

  const id = registry.generateNodeId(type)
  const node: EtlNode = {
    id,
    type,
    name: def.label,
    x,
    y,
    config: {},
  }
  engine.addNode(node)
  syncNodes()
}

function handleNodeSelect(id: string): void {
  selectedNodeId.value = id
  selectedEdgeId.value = null
}

function handleEdgeSelect(id: string): void {
  selectedEdgeId.value = id
  selectedNodeId.value = null
}

function handleNodeMove(id: string, x: number, y: number): void {
  engine.updateNode(id, { x, y })
  syncNodes()
}

function handleNodeUpdate(id: string, patch: Partial<EtlNode>): void {
  engine.updateNode(id, patch)
  syncNodes()
}

function handleNodeConfigUpdate(id: string, key: string, value: unknown): void {
  const node = engine.getNode(id)
  if (node) {
    node.config[key] = value
    syncNodes()
  }
}

function handleNodeDelete(id: string): void {
  engine.removeNode(id)
  selectedNodeId.value = null
  syncNodes()
  syncEdges()
}

function handleConnect(sourceId: string, targetId: string): void {
  try {
    engine.addEdge({
      id: `edge_${Date.now()}`,
      source: sourceId,
      target: targetId,
    })
    syncEdges()
  } catch (e) {
    console.warn('Cannot connect:', e)
  }
}

function handleCanvasClick(): void {
  selectedNodeId.value = null
  selectedEdgeId.value = null
}

function syncNodes(): void {
  nodes.value = engine.getNodes()
  emitChange()
}

function syncEdges(): void {
  edges.value = engine.getEdges()
  emitChange()
}

function emitChange(): void {
  emit('change', engine.exportDag())
}
</script>

<style scoped>
.flow-designer {
  display: flex;
  width: 100%;
  height: 100%;
  background: #f5f7fa;
}

.node-palette {
  width: 220px;
  background: #fff;
  border-right: 1px solid #e4e7ed;
  display: flex;
  flex-direction: column;
  overflow-y: auto;
}

.palette-header {
  padding: 12px 16px;
  border-bottom: 1px solid #e4e7ed;
}

.palette-header h3 {
  margin: 0;
  font-size: 15px;
  font-weight: 500;
}

.palette-group {
  padding: 8px 0;
}

.group-title {
  padding: 4px 16px;
  font-size: 12px;
  font-weight: 600;
  color: #909399;
  text-transform: uppercase;
}

.palette-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 16px;
  cursor: grab;
  transition: background 0.15s;
  font-size: 13px;
}

.palette-item:hover {
  background: #f0f2f5;
}

.palette-icon {
  width: 24px;
  height: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #ecf5ff;
  border-radius: 4px;
  font-size: 12px;
}

.designer-canvas {
  flex: 1;
  position: relative;
  overflow: hidden;
}
</style>
