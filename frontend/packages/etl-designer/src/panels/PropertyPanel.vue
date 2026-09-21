<template>
  <div class="property-panel">
    <div class="panel-title">节点属性</div>
    <div class="panel-section">
      <label class="field-label">名称</label>
      <input v-model="localNode.name" class="field-input" @change="emitUpdate" />
    </div>
    <div class="panel-section">
      <label class="field-label">类型</label>
      <div class="field-readonly">{{ node.type }}</div>
    </div>
    <div v-if="node.type === 'input'" class="panel-section">
      <label class="field-label">数据源</label>
      <input v-model="configDatasource" class="field-input" @change="emitUpdate" />
      <label class="field-label">表名</label>
      <input v-model="configTable" class="field-input" @change="emitUpdate" />
    </div>
    <div v-if="node.type === 'sql'" class="panel-section">
      <label class="field-label">SQL</label>
      <textarea v-model="configSql" class="field-textarea" rows="6" @change="emitUpdate"></textarea>
    </div>
    <div v-if="node.type === 'filter'" class="panel-section">
      <label class="field-label">过滤条件 (JSON)</label>
      <textarea v-model="configFilters" class="field-textarea" rows="6" @change="emitUpdate"></textarea>
    </div>
  </div>
</template>

<script setup lang="ts">
  import { ref, watch } from 'vue'
  import type { EtlNode } from '@dataviz/shared-types'

  const props = defineProps<{
    node: EtlNode
  }>()

  const emit = defineEmits<{
    (e: 'update', node: EtlNode): void
  }>()

  const localNode = ref<EtlNode>({ ...props.node })
  const configDatasource = ref(String(props.node.config.datasourceId || ''))
  const configTable = ref(props.node.config.tableName || '')
  const configSql = ref(props.node.config.sql || '')
  const configFilters = ref(JSON.stringify(props.node.config.filters || [], null, 2))

  watch(
    () => props.node,
    (n) => {
      localNode.value = { ...n }
      configDatasource.value = String(n.config.datasourceId || '')
      configTable.value = n.config.tableName || ''
      configSql.value = n.config.sql || ''
      configFilters.value = JSON.stringify(n.config.filters || [], null, 2)
    },
    { deep: true },
  )

  function emitUpdate() {
    const updated: EtlNode = {
      ...localNode.value,
      config: {
        ...localNode.value.config,
        datasourceId: configDatasource.value ? Number(configDatasource.value) || configDatasource.value : undefined,
        tableName: configTable.value,
        sql: configSql.value,
        filters: safeParseJson(configFilters.value, []),
      },
    }
    emit('update', updated)
  }

  function safeParseJson<T>(str: string, fallback: T): T {
    try {
      return JSON.parse(str) as T
    } catch {
      return fallback
    }
  }
</script>

<style scoped>
  .property-panel {
    padding: 12px;
  }
  .panel-title {
    font-size: 14px;
    font-weight: bold;
    margin-bottom: 12px;
    color: #333;
  }
  .panel-section {
    margin-bottom: 12px;
  }
  .field-label {
    display: block;
    font-size: 12px;
    color: #666;
    margin-bottom: 4px;
  }
  .field-input,
  .field-textarea {
    width: 100%;
    padding: 6px 8px;
    border: 1px solid #d9d9d9;
    border-radius: 4px;
    font-size: 13px;
    box-sizing: border-box;
  }
  .field-readonly {
    font-size: 13px;
    color: #333;
    padding: 6px 0;
  }
</style>
