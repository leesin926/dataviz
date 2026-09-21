<template>
  <div class="etl-designer">
    <div class="etl-sidebar">
      <NodePalette @add-node="handleAddNode" />
    </div>
    <div class="etl-canvas" ref="canvasRef"></div>
    <div class="etl-property-panel">
      <PropertyPanel v-if="selectedNode" :node="selectedNode" @update="handleUpdateNode" />
      <div v-else class="empty-hint">请选择节点查看属性</div>
    </div>
    <div class="etl-minimap">
      <MiniMap />
    </div>
    <div class="etl-toolbar">
      <button @click="handleSave">保存</button>
      <button @click="handleRun">运行</button>
      <button @click="handleValidate">校验</button>
      <button @click="handleClear">清空</button>
    </div>
  </div>
</template>

<script setup lang="ts">
  import { ref, onMounted, onBeforeUnmount } from 'vue'
  import type { EtlTask, EtlNode, EtlEdge } from '@dataviz/shared-types'
  import { GraphManager } from './graph/GraphManager'
  import NodePalette from './panels/NodePalette.vue'
  import PropertyPanel from './panels/PropertyPanel.vue'
  import MiniMap from './panels/MiniMap.vue'

  const props = withDefaults(
    defineProps<{
      task?: EtlTask
    }>(),
    {
      task: undefined,
    },
  )

  const emit = defineEmits<{
    (e: 'save', task: EtlTask): void
    (e: 'run', task: EtlTask): void
    (e: 'update', task: EtlTask): void
  }>()

  const canvasRef = ref<HTMLDivElement | null>(null)
  const selectedNode = ref<EtlNode | null>(null)
  const manager = new GraphManager()

  onMounted(() => {
    if (canvasRef.value) {
      manager.init(canvasRef.value)
      manager.onNodeSelect((node) => {
        selectedNode.value = node
      })
      if (props.task?.dag) {
        manager.loadDag(props.task.dag)
      }
    }
  })

  onBeforeUnmount(() => {
    manager.dispose()
  })

  function handleAddNode(node: EtlNode) {
    manager.addNode(node)
  }

  function handleUpdateNode(node: EtlNode) {
    manager.updateNode(node)
  }

  function handleSave() {
    const dag = manager.toDag()
    const task: EtlTask = {
      ...(props.task || { id: 0, name: '', dag: { nodes: [], edges: [] }, status: 'draft' }),
      dag,
    }
    emit('save', task)
  }

  function handleRun() {
    const dag = manager.toDag()
    const task: EtlTask = {
      ...(props.task || { id: 0, name: '', dag: { nodes: [], edges: [] }, status: 'draft' }),
      dag,
    }
    emit('run', task)
  }

  function handleValidate() {
    const dag = manager.toDag()
    const errors = validateDag(dag)
    if (errors.length === 0) {
      console.log('校验通过')
    } else {
      console.error('校验失败:', errors)
    }
  }

  function handleClear() {
    manager.clear()
    selectedNode.value = null
  }

  function validateDag(dag: { nodes: EtlNode[]; edges: EtlEdge[] }): string[] {
    const errors: string[] = []
    const inputNodes = dag.nodes.filter((n) => n.type === 'input')
    const outputNodes = dag.nodes.filter((n) => n.type === 'output')
    if (inputNodes.length === 0) errors.push('必须至少有一个输入节点')
    if (outputNodes.length === 0) errors.push('必须至少有一个输出节点')
    return errors
  }
</script>

<style scoped>
  .etl-designer {
    display: flex;
    width: 100%;
    height: 100%;
    position: relative;
    background: #f5f6f8;
  }
  .etl-sidebar {
    width: 220px;
    background: #fff;
    border-right: 1px solid #e8e8e8;
    overflow-y: auto;
  }
  .etl-canvas {
    flex: 1;
    position: relative;
  }
  .etl-property-panel {
    width: 280px;
    background: #fff;
    border-left: 1px solid #e8e8e8;
    overflow-y: auto;
  }
  .etl-property-panel .empty-hint {
    padding: 20px;
    text-align: center;
    color: #999;
  }
  .etl-minimap {
    position: absolute;
    right: 300px;
    bottom: 20px;
    width: 180px;
    height: 120px;
    background: #fff;
    border: 1px solid #d9d9d9;
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
  }
  .etl-toolbar {
    position: absolute;
    top: 10px;
    left: 230px;
    display: flex;
    gap: 8px;
    background: #fff;
    padding: 8px 12px;
    border-radius: 4px;
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
  }
  .etl-toolbar button {
    padding: 4px 12px;
    border: 1px solid #d9d9d9;
    background: #fff;
    border-radius: 4px;
    cursor: pointer;
  }
</style>
