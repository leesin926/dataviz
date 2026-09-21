<template>
  <div class="query-editor">
    <div class="editor-toolbar">
      <div class="toolbar-left">
        <select v-model="selectedDatasource" class="datasource-select">
          <option value="">选择数据源</option>
          <option v-for="ds in datasources" :key="ds.id" :value="ds.id">{{ ds.name }}</option>
        </select>
      </div>
      <div class="toolbar-right">
        <button class="btn btn-format" @click="handleFormat">格式化</button>
        <button class="btn btn-execute" :disabled="isExecuting" @click="handleExecute">
          {{ isExecuting ? '执行中...' : '执行' }}
        </button>
      </div>
    </div>

    <div class="editor-body">
      <div class="line-numbers">
        <div v-for="i in lineCount" :key="i" class="line-number">{{ i }}</div>
      </div>
      <textarea
        ref="editorRef"
        v-model="sqlText"
        class="sql-textarea"
        spellcheck="false"
        placeholder="输入 SQL 查询..."
        @keydown="handleKeydown"
      />
    </div>

    <div class="editor-status">
      <span class="status-item">行数: {{ lineCount }}</span>
      <span class="status-item">字符: {{ sqlText.length }}</span>
      <span v-if="validationErrors.length > 0" class="status-item status-error">
        {{ validationErrors[0] }}
      </span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { SqlParser } from '../core/SqlParser'
import { SqlCompleter } from '../core/SqlCompleter'

interface DatasourceOption {
  id: string | number
  name: string
}

const props = withDefaults(
  defineProps<{
    modelValue?: string
    datasources?: DatasourceOption[]
    tables?: Record<string, string[]>
    isExecuting?: boolean
  }>(),
  {
    modelValue: '',
    datasources: () => [],
    tables: () => ({}),
    isExecuting: false,
  }
)

const emit = defineEmits<{
  (e: 'update:modelValue', value: string): void
  (e: 'execute', sql: string): void
  (e: 'format', sql: string): void
}>()

const editorRef = ref<HTMLTextAreaElement | null>(null)
const sqlText = ref(props.modelValue)
const selectedDatasource = ref<string | number>('')
const completer = new SqlCompleter()

const lineCount = computed(() => sqlText.value.split('\n').length)

const validationErrors = computed(() => {
  if (!sqlText.value.trim()) return []
  const { errors } = SqlParser.validate(sqlText.value)
  return errors.filter((e) => !e.startsWith('警告'))
})

watch(() => props.modelValue, (val) => {
  sqlText.value = val
})

watch(sqlText, (val) => {
  emit('update:modelValue', val)
})

watch(() => props.tables, (tables) => {
  completer.registerTables(tables)
}, { immediate: true, deep: true })

function handleExecute(): void {
  if (!sqlText.value.trim()) return
  emit('execute', sqlText.value)
}

function handleFormat(): void {
  const formatted = SqlParser.format(sqlText.value)
  sqlText.value = formatted
  emit('format', formatted)
}

function handleKeydown(e: KeyboardEvent): void {
  // Ctrl/Cmd + Enter 执行
  if ((e.ctrlKey || e.metaKey) && e.key === 'Enter') {
    e.preventDefault()
    handleExecute()
  }

  // Tab 键插入空格
  if (e.key === 'Tab') {
    e.preventDefault()
    const textarea = editorRef.value
    if (!textarea) return
    const start = textarea.selectionStart
    const end = textarea.selectionEnd
    sqlText.value = sqlText.value.substring(0, start) + '  ' + sqlText.value.substring(end)
    setTimeout(() => {
      textarea.selectionStart = textarea.selectionEnd = start + 2
    })
  }
}
</script>

<style scoped>
.query-editor {
  display: flex;
  flex-direction: column;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  background: #fff;
  overflow: hidden;
}

.editor-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  background: #f5f7fa;
  border-bottom: 1px solid #e4e7ed;
}

.datasource-select {
  padding: 4px 8px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  font-size: 13px;
}

.btn {
  padding: 6px 16px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  font-size: 13px;
  cursor: pointer;
  margin-left: 8px;
}

.btn-format {
  background: #fff;
  color: #606266;
}

.btn-execute {
  background: #409eff;
  color: #fff;
  border-color: #409eff;
}

.btn-execute:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.editor-body {
  display: flex;
  flex: 1;
  min-height: 200px;
  position: relative;
}

.line-numbers {
  padding: 8px 0;
  background: #f5f7fa;
  border-right: 1px solid #e4e7ed;
  user-select: none;
  min-width: 40px;
}

.line-number {
  padding: 0 8px;
  text-align: right;
  font-size: 13px;
  line-height: 20px;
  color: #999;
  font-family: 'Consolas', 'Monaco', monospace;
}

.sql-textarea {
  flex: 1;
  padding: 8px 12px;
  border: none;
  outline: none;
  resize: none;
  font-size: 13px;
  line-height: 20px;
  font-family: 'Consolas', 'Monaco', monospace;
  color: #303133;
  background: #fff;
  tab-size: 2;
}

.editor-status {
  display: flex;
  gap: 16px;
  padding: 4px 12px;
  background: #f5f7fa;
  border-top: 1px solid #e4e7ed;
  font-size: 12px;
  color: #909399;
}

.status-error {
  color: #f56c6c;
}
</style>
