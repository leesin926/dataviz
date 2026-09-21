<template>
  <div class="node-palette">
    <div class="palette-title">节点列表</div>
    <div v-for="group in nodeGroups" :key="group.label" class="node-group">
      <div class="group-label">{{ group.label }}</div>
      <div
        v-for="item in group.items"
        :key="item.type"
        class="node-item"
        :style="{ backgroundColor: item.color }"
        draggable="true"
        @dragstart="handleDragStart(item, $event)"
      >
        <span class="node-icon">{{ item.icon }}</span>
        <span class="node-label">{{ item.label }}</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
  import { ref } from 'vue'
  import type { EtlNode, OperatorType } from '@dataviz/shared-types'

  const emit = defineEmits<{
    (e: 'add-node', node: EtlNode): void
  }>()

  interface PaletteItem {
    type: OperatorType
    label: string
    icon: string
    color: string
  }

  interface PaletteGroup {
    label: string
    items: PaletteItem[]
  }

  const nodeGroups = ref<PaletteGroup[]>([
    {
      label: '输入',
      items: [
        { type: 'input', label: '数据库表', icon: '🗄️', color: '#52c41a' },
        { type: 'sql', label: 'SQL 查询', icon: '📝', color: '#52c41a' },
      ],
    },
    {
      label: '转换',
      items: [
        { type: 'filter', label: '过滤', icon: '🔍', color: '#722ed1' },
        { type: 'aggregate', label: '聚合', icon: '📊', color: '#13c2c2' },
        { type: 'join', label: '关联', icon: '🔗', color: '#eb2f96' },
        { type: 'union', label: '合并', icon: '🧩', color: '#2f54eb' },
        { type: 'sort', label: '排序', icon: '↕️', color: '#fa8c16' },
        { type: 'limit', label: '限制行数', icon: '📏', color: '#a0d911' },
        { type: 'transform', label: '字段转换', icon: '🔧', color: '#1890ff' },
      ],
    },
    {
      label: '输出',
      items: [
        { type: 'output', label: '数据库写入', icon: '💾', color: '#faad14' },
        { type: 'output', label: '数据集', icon: '📦', color: '#faad14' },
      ],
    },
  ])

  function handleDragStart(item: PaletteItem, e: DragEvent) {
    const etlNode: EtlNode = {
      id: `node_${Date.now()}`,
      type: item.type,
      name: item.label,
      x: 100,
      y: 100,
      config: {},
    }
    emit('add-node', etlNode)
    if (e.dataTransfer) {
      e.dataTransfer.setData('application/etl-node', JSON.stringify(etlNode))
    }
  }
</script>

<style scoped>
  .node-palette {
    padding: 12px;
  }
  .palette-title {
    font-size: 14px;
    font-weight: bold;
    margin-bottom: 12px;
    color: #333;
  }
  .node-group {
    margin-bottom: 16px;
  }
  .group-label {
    font-size: 12px;
    color: #999;
    margin-bottom: 6px;
  }
  .node-item {
    display: flex;
    align-items: center;
    padding: 8px 10px;
    margin-bottom: 6px;
    border-radius: 4px;
    color: #fff;
    cursor: grab;
    user-select: none;
  }
  .node-item:hover {
    opacity: 0.85;
  }
  .node-icon {
    margin-right: 8px;
    font-size: 14px;
  }
  .node-label {
    font-size: 13px;
  }
</style>
