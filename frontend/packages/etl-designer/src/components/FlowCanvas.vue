<template>
  <div class="flow-canvas" ref="canvasRef"
    @click="handleCanvasClick"
    @dragover.prevent
    @drop="handleDrop"
  >
    <svg class="connections-layer" :width="canvasWidth" :height="canvasHeight">
      <defs>
        <marker id="arrowhead" markerWidth="10" markerHeight="7" refX="10" refY="3.5" orient="auto">
          <polygon points="0 0, 10 3.5, 0 7" fill="#999" />
        </marker>
      </defs>
      <path
        v-for="edge in edges"
        :key="edge.id"
        :d="getConnectionPath(edge)"
        fill="none"
        stroke="#999"
        stroke-width="2"
        marker-end="url(#arrowhead)"
        :class="{ 'is-selected': selectedEdgeId === edge.id }"
        @click.stop="selectEdge(edge.id)"
      />
      <!-- Connection preview line while dragging -->
      <path
        v-if="isConnecting"
        :d="previewPath"
        fill="none"
        stroke="#409eff"
        stroke-width="2"
        stroke-dasharray="5,5"
      />
    </svg>

    <div class="nodes-layer">
      <div
        v-for="node in nodes"
        :key="node.id"
        class="flow-node"
        :class="{
          'is-selected': selectedNodeId === node.id,
          [`is-${nodeCategory(node.type)}`]: true,
        }"
        :style="getNodeStyle(node)"
        @mousedown.stop="startDragNode($event, node)"
      >
        <div class="node-header">
          <span class="node-icon">{{ getNodeIcon(node.type) }}</span>
          <span class="node-name">{{ node.name }}</span>
        </div>
        <div class="node-body">
          <span class="node-type">{{ node.type }}</span>
        </div>
        <!-- Input ports -->
        <div
          v-for="i in getInputPorts(node.type)"
          :key="'in-' + i"
          class="port port-input"
          :style="{ top: `${30 + i * 20}px`, left: '-6px' }"
          @mousedown.stop="startConnect($event, node.id, 'input')"
        />
        <!-- Output ports -->
        <div
          v-for="i in getOutputPorts(node.type)"
          :key="'out-' + i"
          class="port port-output"
          :style="{ top: `${30 + i * 20}px`, right: '-6px' }"
          @mousedown.stop="startConnect($event, node.id, 'output')"
        />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import type { EtlNode, EtlEdge, OperatorType } from '@dataviz/shared-types'

const props = defineProps<{
  nodes: EtlNode[]
  edges: EtlEdge[]
  selectedNodeId?: string | null
  selectedEdgeId?: string | null
}>()

const emit = defineEmits<{
  (e: 'nodeSelect', id: string): void
  (e: 'nodeMove', id: string, x: number, y: number): void
  (e: 'edgeSelect', id: string): void
  (e: 'connect', sourceId: string, targetId: string): void
  (e: 'canvasClick'): void
  (e: 'drop', type: OperatorType, x: number, y: number): void
}>()

const canvasRef = ref<HTMLElement | null>(null)
const canvasWidth = ref(4000)
const canvasHeight = ref(3000)

// Node dragging state
const dragState = ref<{ nodeId: string; offsetX: number; offsetY: number } | null>(null)

// Connection state
const isConnecting = ref(false)
const connectFromId = ref<string | null>(null)
const connectFromType = ref<'input' | 'output' | null>(null)
const connectMousePos = ref({ x: 0, y: 0 })

const NODE_WIDTH = 160
const NODE_HEIGHT = 60

function nodeCategory(type: OperatorType): string {
  if (type === 'input') return 'source'
  if (type === 'output') return 'sink'
  return 'transform'
}

function getNodeIcon(type: OperatorType): string {
  const icons: Record<string, string> = {
    input: '↓', output: '↑', filter: '⊳', transform: '✎',
    join: '⋈', aggregate: 'Σ', union: '∪', sort: '↕',
    limit: '◔', sql: 'SQL', script: '{}',
  }
  return icons[type] || '?'
}

function getInputPorts(type: OperatorType): number {
  return type === 'input' ? 0 : type === 'join' || type === 'union' ? 2 : 1
}

function getOutputPorts(_type: OperatorType): number {
  return 1
}

function getNodeStyle(node: EtlNode): Record<string, string> {
  return {
    left: `${node.x}px`,
    top: `${node.y}px`,
    width: `${NODE_WIDTH}px`,
  }
}

function getNodeCenter(node: EtlNode, port: 'input' | 'output'): { x: number; y: number } {
  if (port === 'input') {
    return { x: node.x, y: node.y + NODE_HEIGHT / 2 }
  }
  return { x: node.x + NODE_WIDTH, y: node.y + NODE_HEIGHT / 2 }
}

function getConnectionPath(edge: EtlEdge): string {
  const sourceNode = props.nodes.find((n) => n.id === edge.source)
  const targetNode = props.nodes.find((n) => n.id === edge.target)
  if (!sourceNode || !targetNode) return ''

  const from = getNodeCenter(sourceNode, 'output')
  const to = getNodeCenter(targetNode, 'input')
  const dx = Math.abs(to.x - from.x) * 0.5

  return `M ${from.x} ${from.y} C ${from.x + dx} ${from.y}, ${to.x - dx} ${to.y}, ${to.x} ${to.y}`
}

const previewPath = computed(() => {
  if (!isConnecting.value || !connectFromId.value) return ''
  const node = props.nodes.find((n) => n.id === connectFromId.value)
  if (!node) return ''

  const from = getNodeCenter(node, connectFromType.value === 'output' ? 'output' : 'input')
  const to = connectMousePos.value
  const dx = Math.abs(to.x - from.x) * 0.5

  if (connectFromType.value === 'output') {
    return `M ${from.x} ${from.y} C ${from.x + dx} ${from.y}, ${to.x - dx} ${to.y}, ${to.x} ${to.y}`
  }
  return `M ${to.x} ${to.y} C ${to.x + dx} ${to.y}, ${from.x - dx} ${from.y}, ${from.x} ${from.y}`
})

function startDragNode(e: MouseEvent, node: EtlNode): void {
  emit('nodeSelect', node.id)
  dragState.value = {
    nodeId: node.id,
    offsetX: e.clientX - node.x,
    offsetY: e.clientY - node.y,
  }

  function onMove(moveE: MouseEvent): void {
    if (!dragState.value) return
    const newX = moveE.clientX - dragState.value.offsetX
    const newY = moveE.clientY - dragState.value.offsetY
    emit('nodeMove', dragState.value.nodeId, Math.max(0, newX), Math.max(0, newY))
  }

  function onUp(): void {
    dragState.value = null
    document.removeEventListener('mousemove', onMove)
    document.removeEventListener('mouseup', onUp)
  }

  document.addEventListener('mousemove', onMove)
  document.addEventListener('mouseup', onUp)
}

function startConnect(_e: MouseEvent, nodeId: string, portType: 'input' | 'output'): void {
  isConnecting.value = true
  connectFromId.value = nodeId
  connectFromType.value = portType

  function onMove(moveE: MouseEvent): void {
    if (!canvasRef.value) return
    const rect = canvasRef.value.getBoundingClientRect()
    connectMousePos.value = {
      x: moveE.clientX - rect.left,
      y: moveE.clientY - rect.top,
    }
  }

  function onUp(): void {
    isConnecting.value = false
    connectFromId.value = null
    connectFromType.value = null
    document.removeEventListener('mousemove', onMove)
    document.removeEventListener('mouseup', onUp)
  }

  document.addEventListener('mousemove', onMove)
  document.addEventListener('mouseup', onUp)
}

function selectEdge(id: string): void {
  emit('edgeSelect', id)
}

function handleCanvasClick(): void {
  emit('canvasClick')
}

function handleDrop(e: DragEvent): void {
  if (!canvasRef.value) return
  const type = e.dataTransfer?.getData('nodeType') as OperatorType
  if (!type) return
  const rect = canvasRef.value.getBoundingClientRect()
  emit('drop', type, e.clientX - rect.left, e.clientY - rect.top)
}
</script>

<style scoped>
.flow-canvas {
  position: relative;
  width: 100%;
  height: 100%;
  background: #fafafa;
  background-image: radial-gradient(circle, #ddd 1px, transparent 1px);
  background-size: 20px 20px;
  overflow: auto;
}

.connections-layer {
  position: absolute;
  top: 0;
  left: 0;
  pointer-events: none;
}

.connections-layer path {
  pointer-events: stroke;
  cursor: pointer;
}

.connections-layer path.is-selected {
  stroke: #409eff;
  stroke-width: 3;
}

.nodes-layer {
  position: absolute;
  top: 0;
  left: 0;
}

.flow-node {
  position: absolute;
  background: #fff;
  border: 2px solid #dcdfe6;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
  cursor: grab;
  user-select: none;
  transition: box-shadow 0.2s;
}

.flow-node:hover {
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.15);
}

.flow-node.is-selected {
  border-color: #409eff;
  box-shadow: 0 0 0 2px rgba(64, 158, 255, 0.2);
}

.flow-node.is-source { border-left: 4px solid #67c23a; }
.flow-node.is-sink { border-left: 4px solid #e6a23c; }
.flow-node.is-transform { border-left: 4px solid #409eff; }

.node-header {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  font-size: 13px;
  font-weight: 500;
}

.node-icon {
  font-size: 14px;
  width: 22px;
  height: 22px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f0f2f5;
  border-radius: 4px;
}

.node-body {
  padding: 0 12px 8px;
  font-size: 11px;
  color: #999;
}

.port {
  position: absolute;
  width: 12px;
  height: 12px;
  background: #fff;
  border: 2px solid #999;
  border-radius: 50%;
  cursor: crosshair;
  z-index: 1;
}

.port:hover {
  border-color: #409eff;
  background: #409eff;
}
</style>
