<template>
  <div class="pane">
    <section class="card">
      <header class="card-head">
        <h2>{{ t('desktop.screenList') }}</h2>
        <div class="row">
          <el-select v-model="displayId" size="small" class="display-select" :placeholder="t('desktop.chooseDisplay')">
            <el-option v-for="d in displays" :key="d.id" :label="displayOption(d)" :value="d.id" />
          </el-select>
          <el-button size="small" :icon="Refresh" :loading="busy.list" @click="loadAll">{{ t('desktop.refresh') }}</el-button>
        </div>
      </header>

      <el-alert v-if="loadError" :title="t('desktop.screenListFailed')" type="error" :closable="false" show-icon class="gap">
        {{ loadError }}
        <span class="hint-inline">{{ t('desktop.screenListFailedFallback') }}</span>
      </el-alert>

      <p class="hint">{{ t('desktop.screenListHint') }}</p>

      <el-table v-if="screens.length" :data="screens" size="small" max-height="300">
        <el-table-column prop="name" :label="t('desktop.screenName')" min-width="160" show-overflow-tooltip />
        <el-table-column :label="t('desktop.canvasSize')" width="120">
          <template #default="{ row }">{{ row.width }}×{{ row.height }}</template>
        </el-table-column>
        <el-table-column :label="t('desktop.componentCount')" width="90">
          <!-- 列表接口不带 components，只带 componentCount；缺字段就显示占位符而不是假的 0 -->
          <template #default="{ row }">{{ row.components ? row.components.length : (row.componentCount ?? '—') }}</template>
        </el-table-column>
        <el-table-column :label="t('desktop.snapshotAt')" width="190">
          <template #default="{ row }">
            <span v-if="snapshotOf(row.id)" class="snap">{{ human(snapshotOf(row.id)?.savedAt) }}</span>
            <span v-else class="muted">{{ t('desktop.noSnapshot') }}</span>
          </template>
        </el-table-column>
        <el-table-column :label="t('desktop.actions')" width="300">
          <template #default="{ row }">
            <el-button link type="primary" size="small" :loading="busy.cache === row.id" @click="snapshot(row)">
              {{ t('desktop.saveSnapshot') }}
            </el-button>
            <el-button link type="primary" size="small" @click="openLocal(row)">
              {{ t('desktop.openOfflineFirst') }}
            </el-button>
            <el-button link type="primary" size="small" :disabled="!api || displayId === null" @click="castTo(row)">
              {{ t('desktop.castToDisplay') }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-else-if="!busy.list" :description="t('desktop.noScreens')" :image-size="70" />
    </section>

    <section class="card">
      <header class="card-head">
        <h2>{{ t('desktop.snapshotStore') }}</h2>
        <el-button size="small" :disabled="!api || !snapshots.length" @click="clearScreens">{{ t('desktop.clearScreens') }}</el-button>
      </header>
      <p class="hint">{{ t('desktop.snapshotStoreHint') }}</p>
      <el-table v-if="snapshots.length" :data="snapshots" size="small" max-height="240">
        <el-table-column prop="key" :label="t('desktop.cacheKey')" min-width="180" />
        <el-table-column :label="t('desktop.savedAt')" width="190">
          <template #default="{ row }">{{ human(row.savedAt) }}</template>
        </el-table-column>
        <el-table-column :label="t('desktop.cacheSize')" width="100">
          <template #default="{ row }">{{ formatBytes(row.bytes) }}</template>
        </el-table-column>
        <el-table-column :label="t('desktop.actions')" width="180">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openSnapshot(row.key)">{{ t('desktop.view') }}</el-button>
            <el-button link type="danger" size="small" @click="removeSnapshot(row.key)">{{ t('desktop.delete') }}</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-else :description="t('desktop.noSnapshots')" :image-size="70" />
    </section>

    <FileImport v-if="api" :api="api" @imported="loadSnapshots" />
  </div>
</template>

<script setup lang="ts">
  import { computed, onMounted, ref } from 'vue'
  import { useI18n } from 'vue-i18n'
  import { useRouter } from 'vue-router'
  import { ElMessage, ElMessageBox } from 'element-plus'
  import { Refresh } from '@element-plus/icons-vue'
  import type { Screen } from '@dataviz/shared-types'
  import { rowsOf } from '@dataviz/shared-types'
  import { getScreen, listScreens } from '@dataviz/api-client'
  import type { CacheMeta, DesktopApi, DisplayInfo } from '@/desktop/electronApi'
  import { formatBytes, saveScreenSnapshot, screenCacheKey } from '@/desktop/electronApi'
  import FileImport from '@/views/desktop/FileImport.vue'

  const props = defineProps<{ api: DesktopApi | null }>()

  const { t } = useI18n()
  const router = useRouter()

  const screens = ref<Screen[]>([])
  const snapshots = ref<CacheMeta[]>([])
  const displays = ref<DisplayInfo[]>([])
  const displayId = ref<number | null>(null)
  const loadError = ref('')
  const busy = ref<{ list: boolean; cache: string | number | null }>({ list: false, cache: null })

  const screenSnaps = computed(() => snapshots.value.filter((s) => s.key.startsWith('screen-')))

  function displayOption(d: DisplayInfo): string {
    return `${d.id} · ${d.label}${d.isPrimary ? ` · ${t('desktop.primaryScreen')}` : ''}`
  }

  function snapshotOf(id: string | number): CacheMeta | undefined {
    return screenSnaps.value.find((s) => s.key === screenCacheKey(id))
  }

  function human(iso?: string): string {
    if (!iso) return '—'
    const d = new Date(iso)
    return Number.isNaN(d.getTime()) ? iso : d.toLocaleString()
  }

  async function loadScreens() {
    busy.value.list = true
    loadError.value = ''
    try {
      const page = await listScreens({ pageNum: 1, pageSize: 50, status: 'published' })
      screens.value = rowsOf(page)
    } catch (e) {
      loadError.value = (e as Error).message
      screens.value = []
    } finally {
      busy.value.list = false
    }
  }

  async function loadSnapshots() {
    if (!props.api) {
      snapshots.value = []
      return
    }
    snapshots.value = await props.api.cache.list()
  }

  async function loadDisplays() {
    if (!props.api) return
    const state = await props.api.window.state()
    displays.value = state.displays
    const primary = state.displays.find((d) => d.isPrimary) || state.displays[0]
    if (primary && displayId.value === null) displayId.value = primary.id
  }

  async function loadAll() {
    await Promise.all([loadScreens(), loadSnapshots(), loadDisplays()])
  }

  async function snapshot(row: Screen) {
    if (!props.api) {
      ElMessage.warning(t('desktop.needsElectron'))
      return
    }
    busy.value.cache = row.id
    try {
      // 列表接口不带完整 components，快照必须取详情，否则离线打开是一张空屏
      const full = await getScreen(row.id)
      const meta = await saveScreenSnapshot(full)
      ElMessage.success(t('desktop.snapshotSaved', { key: meta.key, at: human(meta.savedAt) }))
      await loadSnapshots()
    } catch (e) {
      ElMessage.error((e as Error).message)
    } finally {
      busy.value.cache = null
    }
  }

  function openLocal(row: Screen) {
    // 有快照就先读快照（离线可用），没有快照直接走在线详情
    router.push({ path: '/screen-view', query: snapshotOf(row.id) ? { target: String(row.id), offline: '1' } : { target: String(row.id) } })
  }

  async function castTo(row: Screen) {
    if (!props.api || displayId.value === null) return
    try {
      await props.api.window.openScreen({ target: String(row.id), displayId: displayId.value })
      ElMessage.success(t('desktop.castOpened', { id: displayId.value }))
    } catch (e) {
      ElMessage.error((e as Error).message)
    }
  }

  function openSnapshot(key: string) {
    const target = key.slice('screen-'.length)
    router.push({ path: '/screen-view', query: { target, offline: '1' } })
  }

  async function removeSnapshot(key: string) {
    if (!props.api) return
    const ok = await props.api.cache.remove(key)
    if (ok) ElMessage.success(t('desktop.cacheRemoved', { key }))
    else ElMessage.error(t('desktop.cacheRemoveFailed', { key }))
    await loadSnapshots()
  }

  async function clearScreens() {
    if (!props.api) return
    const confirmed = await ElMessageBox.confirm(t('desktop.clearScreensConfirm', { count: screenSnaps.value.length }), t('desktop.warning'), {
      type: 'warning',
    }).catch(() => false)
    if (!confirmed) return
    for (const item of screenSnaps.value) await props.api.cache.remove(item.key)
    await loadSnapshots()
    ElMessage.success(t('desktop.clearScreensDone'))
  }

  onMounted(() => {
    void loadAll()
  })
</script>

<style scoped lang="scss">
  .pane {
    display: flex;
    flex-direction: column;
    gap: 16px;
  }

  .card {
    padding: 16px;
    background: var(--dv-surface-1);
    border: 1px solid var(--dv-border);
    border-radius: var(--dv-radius-md);
  }

  .card-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 8px;

    h2 {
      font-size: var(--dv-font-lg);
      font-weight: 620;
    }
  }

  .row {
    display: flex;
    align-items: center;
    gap: 10px;
  }

  .display-select {
    width: 240px;
  }

  .hint {
    margin: 4px 0 10px;
    font-size: var(--dv-font-xs);
    color: var(--dv-text-3);
  }

  .hint-inline {
    display: block;
    margin-top: 4px;
  }

  .gap {
    margin-bottom: 10px;
  }

  .muted {
    color: var(--dv-text-4);
  }

  .snap {
    font-size: var(--dv-font-xs);
    color: var(--dv-text-2);
  }
</style>
