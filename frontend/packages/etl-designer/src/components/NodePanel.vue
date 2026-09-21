<template>
  <div class="node-panel">
    <div class="panel-header">
      <h3>{{ node ? node.name : '节点配置' }}</h3>
      <button class="close-btn" @click="emit('close')">&times;</button>
    </div>

    <div v-if="!node" class="panel-empty">
      <p>选择一个节点进行配置</p>
    </div>

    <div v-else class="panel-body">
      <div class="form-group">
        <label class="form-label">节点名称</label>
        <input class="form-input" :value="node.name" @input="updateConfig('name', ($event.target as HTMLInputElement).value)" />
      </div>

      <div class="form-group">
        <label class="form-label">节点类型</label>
        <span class="form-value">{{ definition?.label || node.type }}</span>
      </div>

      <div class="form-group">
        <label class="form-label">描述</label>
        <span class="form-value">{{ definition?.description || '-' }}</span>
      </div>

      <el-divider />

      <h4>配置项</h4>
      <div v-for="field in definition?.configFields || []" :key="field.key" class="form-group">
        <label class="form-label">
          {{ field.label }}
          <span v-if="field.required" class="required">*</span>
        </label>

        <template v-if="field.type === 'string'">
          <input
            class="form-input"
            :value="String(node.config[field.key] ?? field.defaultValue ?? '')"
            :placeholder="field.placeholder || ''"
            @input="updateNodeConfig(field.key, ($event.target as HTMLInputElement).value)"
          />
        </template>

        <template v-else-if="field.type === 'number'">
          <input
            class="form-input"
            type="number"
            :value="node.config[field.key] ?? field.defaultValue ?? ''"
            :placeholder="field.placeholder || ''"
            @input="updateNodeConfig(field.key, Number(($event.target as HTMLInputElement).value))"
          />
        </template>

        <template v-else-if="field.type === 'boolean'">
          <label class="checkbox-label">
            <input
              type="checkbox"
              :checked="Boolean(node.config[field.key] ?? field.defaultValue)"
              @change="updateNodeConfig(field.key, ($event.target as HTMLInputElement).checked)"
            />
            <span>{{ field.label }}</span>
          </label>
        </template>

        <template v-else-if="field.type === 'select'">
          <select
            class="form-input"
            :value="String(node.config[field.key] ?? '')"
            @change="updateNodeConfig(field.key, ($event.target as HTMLSelectElement).value)"
          >
            <option value="">请选择</option>
            <option
              v-for="opt in field.options"
              :key="String(opt.value)"
              :value="opt.value"
            >{{ opt.label }}</option>
          </select>
        </template>

        <template v-else-if="field.type === 'textarea' || field.type === 'sql'">
          <textarea
            class="form-textarea"
            :class="{ 'is-sql': field.type === 'sql' }"
            :value="String(node.config[field.key] ?? '')"
            :placeholder="field.placeholder || ''"
            rows="5"
            @input="updateNodeConfig(field.key, ($event.target as HTMLTextAreaElement).value)"
          />
        </template>

        <template v-else>
          <input
            class="form-input"
            :value="String(node.config[field.key] ?? '')"
            :placeholder="field.placeholder || ''"
            @input="updateNodeConfig(field.key, ($event.target as HTMLInputElement).value)"
          />
        </template>
      </div>

      <el-divider />

      <div class="panel-actions">
        <button class="btn btn-danger" @click="emit('delete', node.id)">删除节点</button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { EtlNode, OperatorType } from '@dataviz/shared-types'
import { NodeRegistry } from '../core/NodeRegistry'

const props = defineProps<{
  node: EtlNode | null
}>()

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'update', id: string, patch: Partial<EtlNode>): void
  (e: 'updateConfig', id: string, key: string, value: unknown): void
  (e: 'delete', id: string): void
}>()

const registry = NodeRegistry.getInstance()

const definition = computed(() => {
  if (!props.node) return undefined
  return registry.getDefinition(props.node.type as OperatorType)
})

function updateConfig(key: string, value: unknown): void {
  if (!props.node) return
  if (key === 'name') {
    emit('update', props.node.id, { name: value as string })
  } else {
    updateNodeConfig(key, value)
  }
}

function updateNodeConfig(key: string, value: unknown): void {
  if (!props.node) return
  emit('updateConfig', props.node.id, key, value)
}
</script>

<style scoped>
.node-panel {
  width: 320px;
  height: 100%;
  background: #fff;
  border-left: 1px solid #e4e7ed;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid #e4e7ed;
}

.panel-header h3 {
  margin: 0;
  font-size: 15px;
  font-weight: 500;
}

.close-btn {
  border: none;
  background: none;
  font-size: 20px;
  cursor: pointer;
  color: #999;
  padding: 0 4px;
}

.close-btn:hover { color: #333; }

.panel-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 200px;
  color: #999;
}

.panel-body {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
}

.form-group {
  margin-bottom: 14px;
}

.form-label {
  display: block;
  font-size: 13px;
  font-weight: 500;
  color: #606266;
  margin-bottom: 4px;
}

.required {
  color: #f56c6c;
}

.form-input {
  width: 100%;
  padding: 6px 10px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  font-size: 13px;
  outline: none;
  box-sizing: border-box;
}

.form-input:focus {
  border-color: #409eff;
}

.form-textarea {
  width: 100%;
  padding: 6px 10px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  font-size: 13px;
  font-family: 'Consolas', 'Monaco', monospace;
  resize: vertical;
  outline: none;
  box-sizing: border-box;
}

.form-textarea.is-sql {
  background: #f8f9fa;
  color: #333;
}

.form-value {
  font-size: 13px;
  color: #909399;
}

.checkbox-label {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  cursor: pointer;
}

.panel-actions {
  display: flex;
  gap: 8px;
}

.btn {
  padding: 6px 14px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  font-size: 13px;
  cursor: pointer;
  background: #fff;
}

.btn-danger {
  color: #f56c6c;
  border-color: #f56c6c;
}

.btn-danger:hover {
  background: #f56c6c;
  color: #fff;
}

h4 {
  font-size: 14px;
  font-weight: 500;
  margin: 0 0 12px;
  color: #303133;
}
</style>
