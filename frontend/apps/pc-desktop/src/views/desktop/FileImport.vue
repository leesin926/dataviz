<template>
  <section class="card">
    <header class="card-head"><h2>{{ t('desktop.fileImport') }}</h2></header>
    <p class="hint">{{ t('desktop.fileImportHint') }}</p>

    <div
      class="drop-zone"
      :class="{ over: dragging }"
      @dragenter.prevent="dragging = true"
      @dragover.prevent="dragging = true"
      @dragleave.prevent="dragging = false"
      @drop.prevent="onDrop"
    >
      <p>{{ t('desktop.fileDropHere') }}</p>
      <p class="sub">{{ t('desktop.fileAllowed') }}</p>
    </div>

    <el-table v-if="items.length" :data="items" size="small" class="gap">
      <el-table-column prop="name" :label="t('desktop.fileName')" min-width="180" show-overflow-tooltip />
      <el-table-column prop="ext" :label="t('desktop.fileExt')" width="80" />
      <el-table-column :label="t('desktop.fileSize')" width="90">
        <template #default="{ row }">{{ formatBytes(row.size) }}</template>
      </el-table-column>
      <el-table-column :label="t('desktop.fileParsed')" min-width="220">
        <template #default="{ row }">
          <span v-if="row.error" class="error">{{ row.error }}</span>
          <span v-else>{{ summary(row) }}</span>
        </template>
      </el-table-column>
      <el-table-column :label="t('desktop.actions')" width="160">
        <template #default="{ row }">
          <el-button v-if="row.screen" link type="primary" size="small" @click="openScreenFile(row)">
            {{ t('desktop.openAsScreen') }}
          </el-button>
          <el-button v-else-if="!row.error" link type="primary" size="small" :loading="row.saving" @click="saveAsData(row)">
            {{ t('desktop.saveToCache') }}
          </el-button>
          <span v-if="row.savedKey" class="saved">{{ t('desktop.savedAs', { key: row.savedKey }) }}</span>
        </template>
      </el-table-column>
    </el-table>

    <div v-if="previewColumns.length" class="preview-block">
      <h3>{{ t('desktop.filePreview', { name: previewName }) }}</h3>
      <el-table :data="previewRows" size="small" max-height="220">
        <el-table-column v-for="col in previewColumns" :key="col" :prop="col" :label="col" min-width="120" show-overflow-tooltip />
      </el-table>
    </div>
  </section>
</template>

<script setup lang="ts">
  import { ref } from 'vue'
  import { useI18n } from 'vue-i18n'
  import { useRouter } from 'vue-router'
  import { ElMessage } from 'element-plus'
  import type { Screen } from '@dataviz/shared-types'
  import { normalizeScreenInk } from '@dataviz/shared-types'
  import type { DesktopApi, TextFile } from '@/desktop/electronApi'
  import { formatBytes } from '@/desktop/electronApi'

  const props = defineProps<{ api: DesktopApi | null }>()
  const emit = defineEmits<{ (e: 'imported'): void }>()

  const { t } = useI18n()
  const router = useRouter()

  interface ImportItem {
    name: string
    ext: string
    size: number
    path: string
    rows: Record<string, unknown>[]
    columns: string[]
    screen: Screen | null
    text: string
    error: string
    saving: boolean
    savedKey: string
  }

  const dragging = ref(false)
  const items = ref<ImportItem[]>([])
  const previewColumns = ref<string[]>([])
  const previewRows = ref<Record<string, unknown>[]>([])
  const previewName = ref('')

  /** 主进程按扩展名放行；这里只是兜底显示，真正的白名单在 electron/cache.js:readTextFile */
  const MAX_PREVIEW_ROWS = 8
  const MAX_STORE_ROWS = 500

  function slug(name: string, ext: string): string {
    const base = name.replace(/\.[^.]+$/, '')
    const safe = base
      .replace(/[^A-Za-z0-9._-]+/g, '-')
      .replace(/^-+|-+$/g, '')
      .slice(0, 40)
    // 缓存键必须是 ASCII 安全串，中文文件名会被整段替换掉；一个短哈希保证键唯一且合法
    const hash = Array.from(name).reduce((acc, ch) => (acc * 31 + ch.charCodeAt(0)) % 100000, 7)
    return `${safe || `file-${hash}`}${ext ? `.${ext}` : ''}`.slice(0, 60)
  }

  function looksLikeScreen(value: unknown): value is Screen {
    if (!value || typeof value !== 'object' || Array.isArray(value)) return false
    const o = value as Record<string, unknown>
    return Array.isArray(o.components) && typeof o.width === 'number' && typeof o.height === 'number'
  }

  /** 一行可能跨物理行（引号里有换行），所以整段扫描而不是先按 \n 切 */
  function splitRows(text: string, delimiter: string): string[][] {
    const rows: string[][] = []
    let field = ''
    let row: string[] = []
    let inQuotes = false
    const src = text.replace(/\r\n/g, '\n')
    for (let i = 0; i < src.length; i++) {
      const ch = src[i]
      if (inQuotes) {
        if (ch === '"') {
          if (src[i + 1] === '"') {
            field += '"'
            i++
          } else {
            inQuotes = false
          }
        } else {
          field += ch
        }
        continue
      }
      if (ch === '"') {
        inQuotes = true
      } else if (ch === delimiter) {
        row.push(field)
        field = ''
      } else if (ch === '\n') {
        row.push(field)
        rows.push(row)
        row = []
        field = ''
      } else {
        field += ch
      }
    }
    if (field !== '' || row.length) {
      row.push(field)
      rows.push(row)
    }
    return rows
  }

  function parseDelimitedAll(text: string, delimiter: string): { columns: string[]; rows: Record<string, unknown>[] } {
    const rows = splitRows(text, delimiter).filter((r) => r.some((c) => String(c).trim() !== ''))
    if (!rows.length) return { columns: [], rows: [] }
    const columns = rows[0].map((h, i) => (String(h).trim() === '' ? `column_${i + 1}` : String(h).trim()))
    const objects = rows.slice(1).map((cells) => {
      const record: Record<string, unknown> = {}
      columns.forEach((col, i) => {
        record[col] = cells[i] ?? ''
      })
      return record
    })
    return { columns, rows: objects }
  }

  function parseNdjson(text: string): Record<string, unknown>[] {
    return text
      .split('\n')
      .map((l) => l.trim())
      .filter(Boolean)
      .map((l) => JSON.parse(l) as Record<string, unknown>)
  }

  function collectColumns(rows: Record<string, unknown>[]): string[] {
    const set = new Set<string>()
    rows.slice(0, 50).forEach((row) => Object.keys(row).forEach((k) => set.add(k)))
    return [...set]
  }

  function summary(row: ImportItem): string {
    if (row.screen) {
      return t('desktop.summaryScreen', { name: row.screen.name || row.name, w: row.screen.width, h: row.screen.height, count: row.screen.components.length })
    }
    if (row.rows.length) return t('desktop.summaryRows', { rows: row.rows.length, cols: row.columns.length })
    const chars = row.text.length
    return t('desktop.summaryText', { chars, shown: Math.min(chars, 2000) })
  }

  function toFileItem(file: TextFile): ImportItem {
    const item: ImportItem = {
      name: file.name,
      ext: file.ext,
      size: file.size,
      path: file.path,
      rows: [],
      columns: [],
      screen: null,
      text: file.text,
      error: '',
      saving: false,
      savedKey: '',
    }
    try {
      if (file.ext === 'json' || file.ext === 'geojson') {
        const value = JSON.parse(file.text)
        if (looksLikeScreen(value)) {
          item.screen = normalizeScreenInk(value as Screen)
        } else if (Array.isArray(value)) {
          item.rows = value as Record<string, unknown>[]
          item.columns = collectColumns(item.rows)
        } else if (value && typeof value === 'object') {
          const obj = value as Record<string, unknown>
          // 常见的 {"data":[...]} 包一层，取出来当行数据
          const inner = Array.isArray(obj.data) ? (obj.data as Record<string, unknown>[]) : null
          if (inner) {
            item.rows = inner
            item.columns = collectColumns(inner)
          } else {
            item.rows = [obj]
            item.columns = Object.keys(obj)
          }
        } else {
          item.error = t('desktop.fileJsonScalar')
        }
      } else if (file.ext === 'ndjson') {
        item.rows = parseNdjson(file.text)
        item.columns = collectColumns(item.rows)
      } else if (file.ext === 'csv' || file.ext === 'txt' || file.ext === 'tsv') {
        const delimiter = file.ext === 'tsv' ? '\t' : ','
        const parsed = parseDelimitedAll(file.text, delimiter)
        item.rows = parsed.rows
        item.columns = parsed.columns
        // 只有一行且没有列结构：当成纯文本，不假装是表格
        if (item.rows.length === 0 && file.ext === 'txt') item.columns = []
      }
    } catch (e) {
      item.error = `${t('desktop.fileParseFailed')}: ${(e as Error).message}`
    }
    return item
  }

  async function onDrop(event: DragEvent) {
    dragging.value = false
    const api = props.api
    if (!api) {
      ElMessage.warning(t('desktop.needsElectron'))
      return
    }
    const dropped = Array.from(event.dataTransfer?.files || [])
    if (!dropped.length) return
    const next: ImportItem[] = []
    for (const file of dropped) {
      try {
        const abs = api.file.pathForFile(file)
        if (!abs) throw new Error(t('desktop.fileNoPath'))
        next.push(toFileItem(await api.file.readText(abs)))
      } catch (e) {
        next.push({
          name: file.name,
          ext: '',
          size: file.size,
          path: '',
          rows: [],
          columns: [],
          screen: null,
          text: '',
          error: (e as Error).message,
          saving: false,
          savedKey: '',
        })
      }
    }
    items.value = [...next, ...items.value].slice(0, 20)
    const first = next.find((n) => !n.error && n.rows.length)
    if (first) {
      previewName.value = first.name
      previewColumns.value = first.columns.slice(0, 12)
      previewRows.value = first.rows.slice(0, MAX_PREVIEW_ROWS)
    }
  }

  async function saveAsData(row: ImportItem) {
    const api = props.api
    if (!api) return
    row.saving = true
    try {
      const key = `data-${slug(row.name, '')}`
      const payload = {
        source: row.path,
        columns: row.columns,
        truncated: row.rows.length > MAX_STORE_ROWS,
        rows: row.rows.slice(0, MAX_STORE_ROWS),
      }
      const meta = await api.cache.write(key, payload)
      row.savedKey = meta.key
      ElMessage.success(t('desktop.cacheWritten', { key: meta.key }))
      emit('imported')
    } catch (e) {
      ElMessage.error((e as Error).message)
    } finally {
      row.saving = false
    }
  }

  async function openScreenFile(row: ImportItem) {
    const api = props.api
    if (!api || !row.screen) return
    try {
      const target = `local-${slug(row.name, '')}`
      await api.cache.write(`screen-${target}`, row.screen)
      row.savedKey = `screen-${target}`
      router.push({ path: '/screen-view', query: { target, offline: '1' } })
      emit('imported')
    } catch (e) {
      ElMessage.error((e as Error).message)
    }
  }
</script>

<style scoped lang="scss">
  .card {
    padding: 16px;
    background: var(--dv-surface-1);
    border: 1px solid var(--dv-border);
    border-radius: var(--dv-radius-md);
  }

  .card-head h2 {
    margin-bottom: 8px;
    font-size: var(--dv-font-lg);
    font-weight: 620;
  }

  .hint {
    margin-bottom: 10px;
    font-size: var(--dv-font-xs);
    color: var(--dv-text-3);
  }

  .drop-zone {
    padding: 26px 16px;
    text-align: center;
    font-size: var(--dv-font-md);
    color: var(--dv-text-2);
    background: var(--dv-surface-sunken);
    border: 1px dashed var(--dv-border-strong);
    border-radius: var(--dv-radius-md);

    .sub {
      margin-top: 6px;
      font-size: var(--dv-font-xs);
      color: var(--dv-text-4);
    }

    &.over {
      border-color: var(--dv-primary);
      background: var(--dv-primary-soft);
    }
  }

  .gap {
    margin-top: 12px;
  }

  .error {
    color: var(--dv-danger);
  }

  .saved {
    margin-left: 8px;
    font-size: var(--dv-font-xs);
    color: var(--dv-success);
  }

  .preview-block {
    margin-top: 14px;

    h3 {
      margin-bottom: 8px;
      font-size: var(--dv-font-md);
      color: var(--dv-text-2);
    }
  }
</style>
