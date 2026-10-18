<template>
  <div class="pane">
    <section class="card">
      <header class="card-head">
        <h2>{{ t('desktop.updateTitle') }}</h2>
        <el-tag size="small" type="warning" effect="plain">{{ t('desktop.updateCheckOnly') }}</el-tag>
      </header>
      <p class="hint">{{ t('desktop.updateHint') }}</p>

      <div class="row">
        <span class="field-label">{{ t('desktop.currentVersion') }}</span>
        <strong>{{ info?.version || '—' }}</strong>
        <span class="field-label">{{ t('desktop.packaged') }}</span>
        <el-tag size="small" :type="info?.packaged ? 'success' : 'info'" effect="plain">
          {{ info?.packaged ? t('desktop.packagedYes') : t('desktop.packagedNo') }}
        </el-tag>
      </div>

      <div class="row gap">
        <el-input v-model="feedUrl" size="small" class="feed-input" :placeholder="t('desktop.feedPlaceholder')" :disabled="!api" />
        <el-button size="small" :disabled="!api" :loading="busy.save" @click="saveFeed">{{ t('desktop.feedSave') }}</el-button>
        <el-button size="small" type="primary" :disabled="!api" :loading="busy.check" @click="check">{{ t('desktop.checkUpdate') }}</el-button>
      </div>

      <p class="hint">{{ t('desktop.feedLocation', { path: configPath }) }}</p>

      <el-alert v-if="result" :title="result.stateLabel" :type="alertType" :closable="false" show-icon class="gap">
        <div v-if="result.latestVersion" class="result-line">{{ t('desktop.latestVersion', { version: result.latestVersion }) }}</div>
        <div v-if="result.manifestUrl" class="result-line">{{ t('desktop.manifestUrl') }}: {{ result.manifestUrl }}</div>
        <div v-if="result.file" class="result-line">{{ t('desktop.updateFile') }}: {{ result.file }}</div>
        <div v-if="result.note" class="result-line note">{{ result.note }}</div>
      </el-alert>
    </section>

    <section class="card">
      <header class="card-head">
        <h2>{{ t('desktop.cacheTitle') }}</h2>
        <div class="row">
          <el-button size="small" :icon="Refresh" :disabled="!api" @click="loadCache">{{ t('desktop.refresh') }}</el-button>
          <el-button size="small" type="danger" plain :disabled="!api || !entries.length" @click="clearAll">
            {{ t('desktop.clearAll') }}
          </el-button>
        </div>
      </header>
      <p class="hint">{{ t('desktop.cacheHint', { count: entries.length }) }}</p>
      <el-table v-if="entries.length" :data="entries" size="small" max-height="320">
        <el-table-column prop="key" :label="t('desktop.cacheKey')" min-width="200" show-overflow-tooltip />
        <el-table-column :label="t('desktop.savedAt')" width="190">
          <template #default="{ row }">{{ human(row.savedAt) }}</template>
        </el-table-column>
        <el-table-column :label="t('desktop.cacheSize')" width="100">
          <template #default="{ row }">{{ formatBytes(row.bytes) }}</template>
        </el-table-column>
        <el-table-column :label="t('desktop.actions')" width="160">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="view(row.key)">{{ t('desktop.view') }}</el-button>
            <el-button link type="danger" size="small" @click="remove(row.key)">{{ t('desktop.delete') }}</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-else :description="api ? t('desktop.cacheEmpty') : t('desktop.needsElectron')" :image-size="70" />
    </section>

    <el-dialog v-model="viewVisible" :title="t('desktop.cacheContent', { key: viewKey })" width="720px">
      <pre class="json-view">{{ viewText }}</pre>
      <template #footer>
        <el-button @click="viewVisible = false">{{ t('desktop.close') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
  import { computed, onMounted, ref } from 'vue'
  import { useI18n } from 'vue-i18n'
  import { ElMessage, ElMessageBox } from 'element-plus'
  import { Refresh } from '@element-plus/icons-vue'
  import type { AppInfo, CacheEntry, CacheMeta, DesktopApi, UpdateResult } from '@/desktop/electronApi'
  import { formatBytes } from '@/desktop/electronApi'

  const props = defineProps<{ api: DesktopApi | null }>()

  const { t } = useI18n()

  const info = ref<AppInfo | null>(null)
  const feedUrl = ref('')
  const configPath = ref('updates.json')
  const result = ref<UpdateResult | null>(null)
  const entries = ref<CacheMeta[]>([])
  const viewVisible = ref(false)
  const viewKey = ref('')
  const viewText = ref('')
  const busy = ref({ save: false, check: false })

  const alertType = computed(() => {
    const state = result.value?.state
    if (state === 'available') return 'success'
    if (state === 'failed') return 'error'
    if (state === 'not-configured') return 'warning'
    return 'info'
  })

  function human(iso: string): string {
    const d = new Date(iso)
    return Number.isNaN(d.getTime()) ? iso : d.toLocaleString()
  }

  async function loadConfig() {
    if (!props.api) return
    const cfg = await props.api.updater.state()
    feedUrl.value = cfg.feedUrl
    // 主进程给的就是文件名，路径前缀用 userData 拼出来给用户提供"去哪写这个文件"的线索
    configPath.value = cfg.source ? `${info.value?.userDataDir || ''}/${cfg.source}` : 'updates.json'
  }

  async function loadCache() {
    if (!props.api) return
    entries.value = await props.api.cache.list()
  }

  async function saveFeed() {
    if (!props.api) return
    busy.value.save = true
    try {
      const cfg = await props.api.updater.setFeed(feedUrl.value)
      feedUrl.value = cfg.feedUrl
      ElMessage.success(feedUrl.value ? t('desktop.feedSaved') : t('desktop.feedCleared'))
      await loadConfig()
    } catch (e) {
      ElMessage.error((e as Error).message)
    } finally {
      busy.value.save = false
    }
  }

  async function check() {
    if (!props.api) return
    busy.value.check = true
    try {
      result.value = await props.api.updater.check()
    } catch (e) {
      result.value = { state: 'failed', error: (e as Error).message, stateLabel: (e as Error).message }
    } finally {
      busy.value.check = false
    }
  }

  async function view(key: string) {
    if (!props.api) return
    const entry: CacheEntry | null = await props.api.cache.read(key)
    if (!entry) {
      ElMessage.warning(t('desktop.cacheMissing', { key }))
      return
    }
    viewKey.value = key
    const json = JSON.stringify(entry.payload, null, 2)
    // 整屏配置能到几 MB，全塞进 DOM 会让窗口卡住：超过阈值就只给头一份
    viewText.value = json.length > 200000 ? `${json.slice(0, 200000)}\n… ${t('desktop.truncated')}` : json
    viewVisible.value = true
  }

  async function remove(key: string) {
    if (!props.api) return
    const ok = await props.api.cache.remove(key)
    if (ok) ElMessage.success(t('desktop.cacheRemoved', { key }))
    else ElMessage.error(t('desktop.cacheRemoveFailed', { key }))
    await loadCache()
  }

  async function clearAll() {
    if (!props.api) return
    const confirmed = await ElMessageBox.confirm(t('desktop.clearAllConfirm', { count: entries.value.length }), t('desktop.warning'), {
      type: 'warning',
    }).catch(() => false)
    if (!confirmed) return
    const n = await props.api.cache.clear()
    ElMessage.success(t('desktop.clearAllDone', { count: n }))
    await loadCache()
  }

  onMounted(async () => {
    if (props.api) {
      info.value = await props.api.app.info()
    }
    await Promise.all([loadConfig(), loadCache()])
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
    flex-wrap: wrap;
  }

  .field-label {
    font-size: var(--dv-font-sm);
    color: var(--dv-text-3);
  }

  .hint {
    margin-bottom: 10px;
    font-size: var(--dv-font-xs);
    color: var(--dv-text-3);
  }

  .gap {
    margin-top: 10px;
  }

  .feed-input {
    width: 380px;
  }

  .result-line {
    font-size: var(--dv-font-sm);
    word-break: break-all;
  }

  .result-line.note {
    margin-top: 4px;
    color: var(--dv-warning);
  }

  .json-view {
    max-height: 380px;
    padding: 10px;
    overflow: auto;
    font-size: var(--dv-font-xs);
    background: var(--dv-surface-sunken);
    border-radius: var(--dv-radius-sm);
    white-space: pre-wrap;
    word-break: break-all;
  }
</style>
