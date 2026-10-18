<template>
  <div v-if="!api" class="pane">
    <el-alert :title="t('desktop.needsElectron')" type="info" :closable="false" show-icon>
      {{ t('desktop.needsElectronDesc') }}
    </el-alert>
  </div>

  <div v-else class="pane">
    <section class="card">
      <header class="card-head">
        <h2>{{ t('desktop.runtime') }}</h2>
        <el-button size="small" :icon="Refresh" @click="loadAll">{{ t('desktop.refresh') }}</el-button>
      </header>
      <el-descriptions v-if="info" :column="2" border size="small">
        <el-descriptions-item :label="t('desktop.appNameLabel')">{{ info.name }}</el-descriptions-item>
        <el-descriptions-item :label="t('desktop.appVersion')">{{ info.version }}</el-descriptions-item>
        <el-descriptions-item label="Electron">{{ info.electronVersion }}</el-descriptions-item>
        <el-descriptions-item label="Chromium">{{ info.chromeVersion }}</el-descriptions-item>
        <el-descriptions-item :label="t('desktop.platform')">{{ info.platform }}</el-descriptions-item>
        <el-descriptions-item :label="t('desktop.packaged')">
          <el-tag :type="info.packaged ? 'success' : 'info'" size="small" effect="plain">
            {{ info.packaged ? t('desktop.packagedYes') : t('desktop.packagedNo') }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item :label="t('desktop.userDataDir')" :span="2">{{ info.userDataDir }}</el-descriptions-item>
      </el-descriptions>
      <div class="row">
        <el-button size="small" @click="openPath('userData')">{{ t('desktop.openUserData') }}</el-button>
        <el-button size="small" @click="openPath('downloads')">{{ t('desktop.openDownloads') }}</el-button>
        <el-button size="small" @click="openPath('cache')">{{ t('desktop.openCache') }}</el-button>
      </div>
    </section>

    <section class="card">
      <header class="card-head">
        <h2>{{ t('desktop.windows') }}</h2>
        <div class="row">
          <el-button size="small" :icon="CopyDocument" @click="createWindow">{{ t('desktop.newWindow') }}</el-button>
          <el-button size="small" @click="toggleMain">{{ t('desktop.toggleMain') }}</el-button>
        </div>
      </header>
      <p class="hint">{{ t('desktop.windowsHint') }}</p>
      <el-table :data="windows" size="small" max-height="220">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="title" :label="t('desktop.windowTitle')" min-width="180" show-overflow-tooltip />
        <el-table-column :label="t('desktop.windowState')" width="200">
          <template #default="{ row }">
            <el-tag size="small" :type="row.visible ? 'success' : 'info'" effect="plain">
              {{ row.visible ? t('desktop.visible') : t('desktop.hidden') }}
            </el-tag>
            <el-tag v-if="row.minimized" size="small" type="warning" effect="plain">{{ t('desktop.minimized') }}</el-tag>
            <el-tag v-if="row.maximized" size="small" effect="plain">{{ t('desktop.maximized') }}</el-tag>
            <el-tag v-if="row.isScreenWindow" size="small" type="primary" effect="plain">{{ t('desktop.screenWindow') }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('desktop.bounds')" width="180">
          <template #default="{ row }">{{ row.bounds.x }},{{ row.bounds.y }} · {{ row.bounds.width }}×{{ row.bounds.height }}</template>
        </el-table-column>
      </el-table>
    </section>

    <section class="card">
      <header class="card-head"><h2>{{ t('desktop.displays') }}</h2></header>
      <p class="hint">{{ t('desktop.displaysHint') }}</p>
      <el-table :data="displays" size="small">
        <el-table-column prop="id" :label="t('desktop.displayId')" width="90" />
        <el-table-column :label="t('desktop.displaySpec')" min-width="200">
          <template #default="{ row }">{{ displayLabel(row) }}</template>
        </el-table-column>
        <el-table-column :label="t('desktop.displayKind')" width="140">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ row.internal ? t('desktop.internalScreen') : t('desktop.externalScreen') }}</el-tag>
            <el-tag v-if="row.isPrimary" size="small" type="primary" effect="plain">{{ t('desktop.primaryScreen') }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('desktop.workArea')" width="160">
          <template #default="{ row }">{{ row.workArea.width }}×{{ row.workArea.height }}</template>
        </el-table-column>
      </el-table>
      <el-alert :title="t('desktop.mirroringTitle')" type="info" :closable="false" class="gap">
        {{ t('desktop.mirroringDesc') }}
      </el-alert>
    </section>

    <section class="card">
      <header class="card-head"><h2>{{ t('desktop.tray') }}</h2></header>
      <div class="row">
        <el-switch v-model="closeToTray" :disabled="busy.tray" @change="applyTray" />
        <span class="field-label">{{ t('desktop.closeToTray') }}</span>
        <el-tag size="small" :type="settings && settings.trayAvailable ? 'success' : 'info'" effect="plain">
          {{ settings && settings.trayAvailable ? t('desktop.trayReady') : t('desktop.trayAbsent') }}
        </el-tag>
      </div>
      <p class="hint">{{ t('desktop.trayHint') }}</p>
    </section>

    <section class="card">
      <header class="card-head">
        <h2>{{ t('desktop.shortcuts') }}</h2>
        <div class="row">
          <el-button size="small" :loading="busy.shortcut" @click="registerShortcuts">{{ t('desktop.shortcutsRegister') }}</el-button>
          <el-button size="small" :loading="busy.shortcut" @click="unregisterShortcuts">{{ t('desktop.shortcutsUnregister') }}</el-button>
        </div>
      </header>
      <p class="hint">{{ t('desktop.shortcutsHint') }}</p>
      <el-table :data="shortcuts" size="small">
        <el-table-column prop="accelerator" :label="t('desktop.accelerator')" width="200" />
        <el-table-column :label="t('desktop.shortcutAction')" min-width="180">
          <template #default="{ row }">{{ shortcutLabel(row) }}</template>
        </el-table-column>
        <el-table-column :label="t('desktop.shortcutState')" width="140">
          <template #default="{ row }">
            <el-tag size="small" :type="row.registered ? 'success' : 'danger'" effect="plain">
              {{ row.registered ? t('desktop.registered') : t('desktop.registerFailed') }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <section class="card">
      <header class="card-head">
        <h2>{{ t('desktop.output') }}</h2>
        <div class="row">
          <el-button size="small" @click="openExportDir">{{ t('desktop.exportOpenDir') }}</el-button>
        </div>
      </header>
      <div class="row">
        <el-button size="small" :loading="busy.print" @click="doPrint">{{ t('desktop.printWindow') }}</el-button>
        <el-button size="small" :loading="busy.pdf" @click="doExport('pdf')">{{ t('desktop.exportPdf') }}</el-button>
        <el-button size="small" :loading="busy.png" @click="doExport('png')">{{ t('desktop.exportPng') }}</el-button>
      </div>
      <p v-if="printResult" class="result">
        {{ printResult.success ? t('desktop.printSent') : t('desktop.printFailed', { reason: printResult.failureType }) }}
        · {{ t('desktop.printerCount', { count: printResult.printerCount }) }}
      </p>
      <p v-if="exportText" class="result" :class="{ error: exportFailed }">{{ exportText }}</p>
      <el-table v-if="exportList.length" :data="exportList" size="small" max-height="180">
        <el-table-column :label="t('desktop.fileName')" min-width="240">
          <template #default="{ row }">{{ row.fileName }}</template>
        </el-table-column>
        <el-table-column :label="t('desktop.cacheSize')" width="110">
          <template #default="{ row }">{{ formatBytes(row.bytes) }}</template>
        </el-table-column>
        <el-table-column :label="t('desktop.savedAt')" width="180">
          <template #default="{ row }">{{ humanTime(row.savedAt) }}</template>
        </el-table-column>
        <el-table-column :label="t('desktop.actions')" width="120">
          <template #default="{ row }">
            <el-button size="small" text :disabled="!row.exists" @click="reveal(row.filePath)">
              {{ t('desktop.exportReveal') }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <p class="hint">{{ t('desktop.outputHint') }}</p>
    </section>

    <section class="card">
      <header class="card-head">
        <h2>{{ t('desktop.devices') }}</h2>
        <div class="row">
          <el-button size="small" @click="listDevices">{{ t('desktop.devicesRefresh') }}</el-button>
          <el-button size="small" :loading="busy.camera" @click="askCamera">{{ t('desktop.askCamera') }}</el-button>
        </div>
      </header>
      <p class="hint">{{ t('desktop.devicesHint') }}</p>
      <el-table :data="devices" size="small" max-height="200">
        <el-table-column :label="t('desktop.deviceKind')" width="120">
          <template #default="{ row }">{{ kindLabel(row.kind) }}</template>
        </el-table-column>
        <el-table-column :label="t('desktop.deviceLabel')" min-width="220">
          <template #default="{ row }">{{ row.label || t('desktop.deviceUnnamed') }}</template>
        </el-table-column>
        <el-table-column prop="deviceId" :label="t('desktop.deviceId')" min-width="200" show-overflow-tooltip />
      </el-table>
      <video v-if="cameraStream" ref="videoRef" class="preview" autoplay muted playsinline />
      <p v-if="cameraError" class="result error">{{ cameraError }}</p>
    </section>
  </div>
</template>

<script setup lang="ts">
  import { onBeforeUnmount, onMounted, reactive, ref } from 'vue'
  import { useI18n } from 'vue-i18n'
  import { ElMessage } from 'element-plus'
  import { CopyDocument, Refresh } from '@element-plus/icons-vue'
  import type {
    AppInfo,
    DesktopApi,
    DesktopSettings,
    DisplayInfo,
    ExportRecord,
    PathKind,
    PrintResult,
    ShortcutInfo,
    WindowInfo,
  } from '@/desktop/electronApi'
  import { formatBytes } from '@/desktop/electronApi'

  const props = defineProps<{ api: DesktopApi | null }>()

  /** useI18n 的 t：模板与脚本共用一份，切语言时两处都跟着重渲染 */
  const { t } = useI18n()

  const info = ref<AppInfo | null>(null)
  const windows = ref<WindowInfo[]>([])
  const displays = ref<DisplayInfo[]>([])
  const shortcuts = ref<ShortcutInfo[]>([])
  const settings = ref<DesktopSettings | null>(null)
  const closeToTray = ref(true)
  const printResult = ref<PrintResult | null>(null)
  const exportList = ref<ExportRecord[]>([])
  const exportText = ref('')
  const exportFailed = ref(false)
  const devices = ref<MediaDeviceInfo[]>([])
  const cameraStream = ref<MediaStream | null>(null)
  const cameraError = ref('')
  const videoRef = ref<HTMLVideoElement | null>(null)
  const busy = reactive({ tray: false, shortcut: false, print: false, pdf: false, png: false, camera: false })

  function humanTime(iso: string): string {
    const d = new Date(iso)
    return Number.isNaN(d.getTime()) ? iso : d.toLocaleString()
  }

  function displayLabel(row: DisplayInfo): string {
    return `${row.label} · ${t('desktop.boundsAt', { x: row.bounds.x, y: row.bounds.y, w: row.bounds.width, h: row.bounds.height })}`
  }

  function shortcutLabel(row: ShortcutInfo): string {
    // 主进程给的是中文兜底名；有 i18n 词条就用词条
    const known = t(`desktop.shortcutNames.${row.action}`)
    return known.startsWith('desktop.') ? row.label : known
  }

  function kindLabel(kind: string): string {
    if (kind === 'videoinput') return t('desktop.kindVideo')
    if (kind === 'audioinput') return t('desktop.kindAudio')
    if (kind === 'audiooutput') return t('desktop.kindAudioOut')
    return kind
  }

  async function openPath(kind: PathKind) {
    if (!props.api) return
    // shell.openPath 成功返回空串，非空就是错误说明
    const err = await props.api.app.openPath(kind)
    if (err) ElMessage.error(err)
  }

  async function loadWindowState() {
    if (!props.api) return
    const state = await props.api.window.state()
    windows.value = state.windows
    displays.value = state.displays
  }

  async function createWindow() {
    if (!props.api) return
    await props.api.window.create()
    await loadWindowState()
  }

  async function toggleMain() {
    if (!props.api) return
    await props.api.window.toggleMain()
    await loadWindowState()
  }

  async function applyTray(value: boolean) {
    if (!props.api) return
    busy.tray = true
    try {
      settings.value = await props.api.settings.set({ closeToTray: value })
      closeToTray.value = settings.value.closeToTray
      if (!settings.value.trayAvailable && value) ElMessage.warning(t('desktop.trayAbsent'))
    } catch (e) {
      ElMessage.error((e as Error).message)
    } finally {
      busy.tray = false
    }
  }

  async function registerShortcuts() {
    if (!props.api) return
    busy.shortcut = true
    try {
      shortcuts.value = await props.api.shortcut.register()
      const failed = shortcuts.value.filter((s) => !s.registered).length
      if (failed) ElMessage.warning(t('desktop.someShortcutsFailed', { count: failed }))
      else ElMessage.success(t('desktop.shortcutsRegistered'))
    } finally {
      busy.shortcut = false
    }
  }

  async function unregisterShortcuts() {
    if (!props.api) return
    busy.shortcut = true
    try {
      const n = await props.api.shortcut.unregisterAll()
      shortcuts.value = []
      ElMessage.info(t('desktop.shortcutsUnregistered', { count: n }))
    } finally {
      busy.shortcut = false
    }
  }

  async function doPrint() {
    if (!props.api) return
    busy.print = true
    try {
      printResult.value = await props.api.print.run()
    } catch (e) {
      ElMessage.error((e as Error).message)
    } finally {
      busy.print = false
    }
  }

  async function doExport(kind: 'pdf' | 'png') {
    if (!props.api) return
    busy[kind] = true
    exportFailed.value = false
    exportText.value = t('desktop.exportBusy', { kind: kind.toUpperCase() })
    try {
      const r = kind === 'pdf' ? await props.api.export.pdf({ fileName: 'dataviz-capability', landscape: true }) : await props.api.export.png({ fileName: 'dataviz-capability' })
      if (r.saved) exportText.value = t('desktop.exportSaved', { name: r.fileName, size: formatBytes(r.bytes) })
      else {
        exportFailed.value = true
        exportText.value = t('desktop.exportFailed', { reason: r.reason })
      }
      await refreshExports()
    } catch (e) {
      exportFailed.value = true
      exportText.value = t('desktop.exportFailed', { reason: (e as Error).message })
    } finally {
      busy[kind] = false
    }
  }

  async function refreshExports() {
    if (!props.api) return
    exportList.value = await props.api.export.recent()
  }

  async function reveal(filePath: string) {
    if (!props.api) return
    const r = await props.api.export.reveal(filePath)
    if (!r.revealed) {
      ElMessage.warning(t('desktop.exportGone', { path: filePath }))
      await refreshExports()
    }
  }

  async function openExportDir() {
    if (!props.api) return
    // shell.openPath 成功返回空串，非空就是错误说明
    const err = await props.api.export.openDir()
    if (err) ElMessage.error(err)
  }

  async function listDevices() {
    if (typeof navigator.mediaDevices?.enumerateDevices !== 'function') {
      ElMessage.warning(t('desktop.devicesUnsupported'))
      return
    }
    devices.value = await navigator.mediaDevices.enumerateDevices()
  }

  async function askCamera() {
    cameraError.value = ''
    stopCamera()
    if (typeof navigator.mediaDevices?.getUserMedia !== 'function') {
      cameraError.value = t('desktop.devicesUnsupported')
      return
    }
    busy.camera = true
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ video: true, audio: true })
      cameraStream.value = stream
      await listDevices()
      // 拿一次帧就交还设备：镜头一直被占着，别的程序会以为摄像头坏了
      setTimeout(() => stopCamera(), 3000)
    } catch (e) {
      cameraError.value = cameraErrorText(e as DOMException)
    } finally {
      busy.camera = false
    }
  }

  function cameraErrorText(e: DOMException): string {
    if (e.name === 'NotAllowedError') return t('desktop.cameraDenied')
    if (e.name === 'NotFoundError') return t('desktop.cameraMissing')
    return `${e.name}: ${e.message}`
  }

  function stopCamera() {
    if (!cameraStream.value) return
    cameraStream.value.getTracks().forEach((track) => track.stop())
    cameraStream.value = null
    if (videoRef.value) videoRef.value.srcObject = null
  }

  async function loadAll() {
    if (!props.api) return
    try {
      const [appInfo, state, sc, st, ex] = await Promise.all([
        props.api.app.info(),
        props.api.window.state(),
        props.api.shortcut.list(),
        props.api.settings.get(),
        props.api.export.recent(),
      ])
      info.value = appInfo
      windows.value = state.windows
      displays.value = state.displays
      shortcuts.value = sc
      settings.value = st
      closeToTray.value = st.closeToTray
      exportList.value = ex
    } catch (e) {
      ElMessage.error((e as Error).message || t('desktop.loadFailed'))
    }
  }

  onMounted(() => {
    void loadAll()
    void listDevices()
  })
  onBeforeUnmount(stopCamera)
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
    margin-bottom: 12px;

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
    color: var(--dv-text-2);
  }

  .hint {
    margin: 6px 0 10px;
    font-size: var(--dv-font-xs);
    color: var(--dv-text-3);
  }

  .result {
    margin-top: 8px;
    font-size: var(--dv-font-sm);
    color: var(--dv-text-2);
    word-break: break-all;
  }

  .result.error {
    color: var(--dv-danger);
  }

  .gap {
    margin-top: 12px;
  }

  .preview {
    width: 240px;
    margin-top: 10px;
    border-radius: var(--dv-radius-sm);
    background: #000;
  }
</style>
