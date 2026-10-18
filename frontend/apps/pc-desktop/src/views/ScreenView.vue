<template>
  <div class="screen-view">
    <ScreenEngine v-if="screen" :screen="screen" />

    <div v-else-if="loading" class="state">
      <el-icon class="is-loading"><Loading /></el-icon>
      <span>{{ t('desktop.loadingScreen') }}</span>
    </div>

    <div v-else class="state error-state">
      <p class="msg">{{ errorMsg }}</p>
      <p v-if="target" class="sub">{{ t('desktop.screenMissingHint', { key: `screen-${target}` }) }}</p>
    </div>

    <div v-if="screen || errorMsg" class="toolbar">
      <el-tag v-if="snapshotAt" size="small" type="warning" effect="dark">
        {{ t('desktop.offlineBadge', { at: snapshotAt }) }}
      </el-tag>
      <el-tag v-else-if="screen" size="small" type="success" effect="dark">{{ t('desktop.onlineBadge') }}</el-tag>
      <el-button v-if="api" size="small" :icon="Printer" circle :title="t('desktop.printWindow')" @click="doPrint" />
      <el-button
        v-if="api"
        size="small"
        :icon="Document"
        circle
        :loading="busy === 'pdf'"
        :title="t('desktop.exportPdf')"
        @click="doExport('pdf')"
      />
      <el-button
        v-if="api"
        size="small"
        :icon="Picture"
        circle
        :loading="busy === 'png'"
        :title="t('desktop.exportPng')"
        @click="doExport('png')"
      />
      <el-button size="small" :icon="FullScreen" circle :title="t('desktop.fullscreen')" @click="toggleFullscreen" />
      <el-button size="small" :icon="Back" circle :title="t('desktop.backToWorkbench')" @click="back" />
    </div>

    <div v-if="exportText" class="export-bar" :class="{ failed: !exportPath }">
      <span class="txt">{{ exportText }}</span>
      <el-button v-if="exportPath" size="small" text @click="reveal">{{ t('desktop.exportReveal') }}</el-button>
      <el-button size="small" text :icon="Close" @click="exportText = ''" />
    </div>
  </div>
</template>

<script setup lang="ts">
  import { onMounted, ref } from 'vue'
  import { useRoute, useRouter } from 'vue-router'
  import { useI18n } from 'vue-i18n'
  import { ElMessage } from 'element-plus'
  import { Back, Close, Document, FullScreen, Loading, Picture, Printer } from '@element-plus/icons-vue'
  import type { Screen } from '@dataviz/shared-types'
  import { normalizeScreenInk } from '@dataviz/shared-types'
  import { getScreen } from '@dataviz/api-client'
  import { ScreenEngine } from '@dataviz/screen-engine'
  import type { DesktopApi } from '@/desktop/electronApi'
  import { formatBytes, loadScreenSnapshot } from '@/desktop/electronApi'

  const route = useRoute()
  const router = useRouter()
  const { t } = useI18n()

  const api: DesktopApi | null = window.electronAPI ?? null
  const target = String(route.query.target || '')
  const offlineFirst = route.query.offline === '1'

  const screen = ref<Screen | null>(null)
  const snapshotAt = ref('')
  const loading = ref(true)
  const errorMsg = ref('')
  const busy = ref<'' | 'pdf' | 'png'>('')
  const exportText = ref('')
  const exportPath = ref('')

  function human(iso: string): string {
    const d = new Date(iso)
    return Number.isNaN(d.getTime()) ? iso : d.toLocaleString()
  }

  async function useSnapshot(): Promise<boolean> {
    const entry = await loadScreenSnapshot<Screen>(target)
    if (!entry) return false
    screen.value = normalizeScreenInk(entry.payload)
    snapshotAt.value = human(entry.savedAt)
    return true
  }

  async function load() {
    if (!target) {
      errorMsg.value = t('desktop.noScreenTarget')
      loading.value = false
      return
    }
    // 离线打开只读本机快照：这时任何"再试一次在线接口"都是把用户晾在网络超时里
    if (offlineFirst) {
      if (await useSnapshot()) {
        loading.value = false
        return
      }
      errorMsg.value = t('desktop.snapshotMissingOffline')
      loading.value = false
      return
    }
    try {
      const online = await getScreen(target)
      screen.value = online
      // 在线成功后顺手刷一份快照，下次断网就有得看；写失败不影响本次展示
      if (api) {
        await api.cache.write(`screen-${target}`, online).catch(() => undefined)
      }
    } catch (e) {
      if (await useSnapshot()) {
        ElMessage.warning(t('desktop.fallbackToSnapshot'))
      } else {
        errorMsg.value = (e as Error).message || t('desktop.screenLoadFailed')
      }
    } finally {
      loading.value = false
      if (screen.value?.name) document.title = screen.value.name
    }
  }

  async function doPrint() {
    if (!api) return
    try {
      const r = await api.print.run()
      if (!r.success) ElMessage.error(t('desktop.printFailed', { reason: r.failureType }))
    } catch (e) {
      ElMessage.error((e as Error).message)
    }
  }

  /** 文件名主体用大屏名，主进程还会再剥一次路径分隔符 */
  function fileBase(): string {
    const raw = (screen.value?.name || target || 'screen').replace(/[\\/:*?"<>|]/g, '_')
    return `${raw}-${target || 'screen'}`.slice(0, 60)
  }

  async function doExport(kind: 'pdf' | 'png') {
    if (!api || busy.value) return
    busy.value = kind
    exportText.value = t('desktop.exportBusy', { kind: kind.toUpperCase() })
    exportPath.value = ''
    try {
      // 大屏是横的，PDF 跟着横过来；PNG 直接抓窗口像素，不需要排版选项
      const r = kind === 'pdf' ? await api.export.pdf({ fileName: fileBase(), landscape: true }) : await api.export.png({ fileName: fileBase() })
      if (r.saved) {
        exportPath.value = r.filePath
        exportText.value = t('desktop.exportSaved', { name: r.fileName, size: formatBytes(r.bytes) })
      } else {
        exportText.value = t('desktop.exportFailed', { reason: r.reason })
      }
    } catch (e) {
      exportText.value = t('desktop.exportFailed', { reason: (e as Error).message })
    } finally {
      busy.value = ''
    }
  }

  async function reveal() {
    if (!api || !exportPath.value) return
    const r = await api.export.reveal(exportPath.value)
    if (!r.revealed) exportText.value = t('desktop.exportGone', { path: exportPath.value })
  }

  function toggleFullscreen() {
    if (document.fullscreenElement) void document.exitFullscreen()
    else void document.documentElement.requestFullscreen()
  }

  function back() {
    if (window.history.length > 1) router.back()
    else router.push('/')
  }

  onMounted(load)
</script>

<style scoped lang="scss">
  .screen-view {
    position: relative;
    width: 100vw;
    height: 100vh;
    overflow: hidden;
    background: var(--dv-screen-bg, #000);
  }

  .state {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 8px;
    width: 100%;
    height: 100%;
    color: var(--dv-screen-text, #e0e6ed);
    font-size: var(--dv-font-md);
  }

  .error-state .msg {
    margin: 0;
    color: var(--dv-danger);
  }

  .error-state .sub {
    margin: 0;
    font-size: var(--dv-font-xs);
    color: var(--dv-text-4);
  }

  .toolbar {
    position: absolute;
    top: 10px;
    right: 12px;
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 6px 8px;
    background: var(--dv-glass);
    border-radius: var(--dv-radius-md);
    opacity: 0.28;
    transition: opacity 0.18s ease;

    &:hover {
      opacity: 1;
    }
  }

  .export-bar {
    position: absolute;
    right: 12px;
    bottom: 14px;
    display: flex;
    align-items: center;
    gap: 4px;
    max-width: 70vw;
    padding: 4px 6px 4px 12px;
    color: var(--dv-screen-text, #e0e6ed);
    font-size: var(--dv-font-xs);
    background: var(--dv-glass);
    border-radius: var(--dv-radius-md);

    .txt {
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    &.failed .txt {
      color: var(--dv-danger);
    }
  }
</style>
