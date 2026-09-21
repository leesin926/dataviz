<template>
  <div class="property-panel">
    <!-- 未选中组件：画布属性 -->
    <template v-if="!comp">
      <h4>{{ t('screen.editor.canvasProps') }}</h4>
      <el-collapse v-model="canvasActive" class="form-collapse">
        <el-collapse-item :title="t('screen.editor.sec_base')" name="base">
          <div class="field">
            <label>{{ t('screen.editor.fWidthHeight') }}</label>
            <div class="grid2">
              <el-input-number v-model="docWidth" size="small" :min="1" :step="10" controls-position="right" />
              <el-input-number v-model="docHeight" size="small" :min="1" :step="10" controls-position="right" />
            </div>
          </div>
          <div class="field">
            <label>{{ t('screen.editor.fAdapt') }}</label>
            <el-select v-model="adaptMode" size="small">
              <el-option :label="t('screen.editor.adaptScale')" value="scale" />
              <el-option :label="t('screen.editor.adaptFixed')" value="fixed-width" />
              <el-option :label="t('screen.editor.adaptResponsive')" value="responsive" />
            </el-select>
          </div>
          <p class="tip">{{ t('screen.editor.fGrayGlobal') }}</p>
        </el-collapse-item>
        <el-collapse-item :title="t('screen.editor.sec_bg')" name="bg">
          <!-- 画布背景色固定白色（不可改），视觉底由上传的背景图承载 -->
          <div class="field">
            <label>{{ t('screen.editor.fBgImage') }}</label>
            <img v-if="bgImageUrl" :src="bgImageUrl" class="bg-thumb" alt="" />
            <div class="inline bg-actions">
              <el-upload
                :show-file-list="false"
                accept="image/png,image/jpeg,image/gif,image/webp"
                :http-request="handleBgUpload"
                :disabled="bgUploading"
              >
                <el-button size="small" type="primary" plain :loading="bgUploading">
                  {{ bgImageUrl ? t('screen.editor.bgReplace') : t('screen.editor.bgUpload') }}
                </el-button>
              </el-upload>
              <el-button v-if="bgImageUrl" size="small" plain @click="handleBgRemove">{{
                t('screen.editor.bgRemove')
              }}</el-button>
            </div>
            <p class="tip">{{ t('screen.editor.bgTip') }}</p>
          </div>
        </el-collapse-item>
        <el-collapse-item :title="t('screen.editor.sec_grid')" name="grid">
          <div class="field">
            <label>{{ t('screen.editor.fGridSnap') }}</label>
            <el-switch v-model="gridSnap" size="small" />
          </div>
          <div class="field">
            <label>{{ t('screen.editor.fGridSize') }}</label>
            <el-input-number v-model="gridSize" size="small" :min="2" :max="200" :step="5" controls-position="right" />
          </div>
        </el-collapse-item>
      </el-collapse>
    </template>

    <!-- 选中组件：分类手风琴 -->
    <template v-else>
      <h4>{{ t('screen.editor.compProps', { type: compLabel(comp.type) }) }}</h4>
      <el-collapse v-model="compActive" accordion class="form-collapse">
        <el-collapse-item :title="t('screen.editor.sec_base')" name="base">
          <div class="field">
            <label>{{ t('screen.editor.fName') }}</label>
            <el-input v-model="comp.name" size="small" />
          </div>
          <div class="field">
            <label>X / Y</label>
            <div class="grid2">
              <el-input-number v-model="comp.x" size="small" :min="0" :max="cMaxX" :step="5" controls-position="right" />
              <el-input-number v-model="comp.y" size="small" :min="0" :max="cMaxY" :step="5" controls-position="right" />
            </div>
          </div>
          <div class="field">
            <label>{{ t('screen.editor.fWidthHeight') }}</label>
            <div class="grid2">
              <el-input-number v-model="comp.w" size="small" :min="10" :max="cMaxW" :step="10" controls-position="right" />
              <el-input-number v-model="comp.h" size="small" :min="10" :max="cMaxH" :step="10" controls-position="right" />
            </div>
          </div>
          <div class="field">
            <label>{{ t('screen.editor.fVisibleLocked') }}</label>
            <div class="inline">
              <el-switch v-model="comp.visible" size="small" />
              <el-switch v-model="comp.locked" size="small" />
            </div>
          </div>
        </el-collapse-item>

        <el-collapse-item :title="t('screen.editor.sec_style')" name="style">
          <!-- 数据驱动快捷样式：组件定义里声明 styleFields，这里自动生成表单 -->
          <div v-for="f in styleFields" :key="f.key" class="field">
            <label>{{ t(f.labelKey) }}</label>
            <el-color-picker
              v-if="f.kind === 'color'"
              size="small"
              :model-value="propStr(f.key)"
              @change="(v: string | null) => setProp(f.key, v)"
            />
            <el-input-number
              v-else-if="f.kind === 'number'"
              size="small"
              :min="f.min"
              :max="f.max"
              :step="f.step || 1"
              controls-position="right"
              :model-value="propNum(f.key)"
              @update:model-value="(v: number | undefined) => setProp(f.key, v)"
            />
            <el-switch
              v-else-if="f.kind === 'switch'"
              size="small"
              :model-value="propBool(f.key)"
              @update:model-value="(v: boolean) => setProp(f.key, v)"
            />
            <el-select
              v-else-if="f.kind === 'select'"
              size="small"
              :model-value="propStr(f.key)"
              @update:model-value="(v: string) => setProp(f.key, v)"
            >
              <el-option v-for="o in f.options || []" :key="o" :label="o" :value="o" />
            </el-select>
            <el-input
              v-else
              size="small"
              :model-value="propStr(f.key)"
              @update:model-value="(v: string) => setProp(f.key, v)"
            />
          </div>

          <!-- 图表组件快捷样式 -->
          <template v-if="isChart && chartConfig">
            <div class="field">
              <label>{{ t('screen.editor.fSeriesColor') }}</label>
              <div v-for="(m, i) in seriesLabels" :key="m" class="series-color-row">
                <span class="series-name">{{ m }}</span>
                <el-color-picker
                  :model-value="seriesColor(i)"
                  size="small"
                  @change="(v: string | null) => setSeriesColor(i, v)"
                />
              </div>
            </div>
            <template v-if="isLine">
              <div class="field inline checks">
                <el-checkbox v-model="optSmooth" :label="t('screen.editor.optSmooth')" />
                <el-checkbox v-model="optArea" :label="t('screen.editor.optArea')" />
                <el-checkbox v-model="optSymbol" :label="t('screen.editor.optSymbol')" />
              </div>
            </template>
            <template v-if="isBar">
              <div class="field inline checks">
                <el-checkbox v-model="optStack" :label="t('screen.editor.optStack')" />
                <el-checkbox v-model="optHorizontal" :label="t('screen.editor.optHorizontal')" />
              </div>
            </template>
            <template v-if="isPie">
              <div class="field inline checks">
                <el-checkbox v-model="optDonut" :label="t('screen.editor.optDonut')" />
                <el-checkbox v-model="optRose" :label="t('screen.editor.optRose')" />
              </div>
            </template>
            <template v-if="isScatter">
              <div class="field inline checks">
                <el-checkbox v-model="optBubble" :label="t('screen.editor.optBubble')" />
              </div>
            </template>
            <template v-if="isRadar">
              <div class="field inline checks">
                <el-checkbox v-model="optRadarArea" :label="t('screen.editor.optRadarArea')" />
              </div>
            </template>
            <template v-if="isGauge">
              <div class="field">
                <label>{{ t('screen.editor.fGaugeMax') }}</label>
                <el-input-number v-model="optGaugeMax" size="small" :min="1" :step="50" controls-position="right" />
              </div>
            </template>
          </template>

          <p v-if="!styleFields.length && !(isChart && chartConfig)" class="tip">
            {{ t('screen.editor.styleTip') }}
          </p>
        </el-collapse-item>

        <el-collapse-item v-if="isChart" :title="t('screen.editor.sec_chart')" name="chart">
          <div class="field">
            <label>{{ t('screen.editor.fChartType') }}</label>
            <el-select v-model="chartType" size="small">
              <el-option v-for="ct in chartTypeOptions" :key="ct" :label="ct" :value="ct" />
            </el-select>
          </div>
          <div class="field">
            <label>{{ t('screen.editor.fChartFrame') }}</label>
            <el-select v-model="chartFrame" size="small" clearable :placeholder="t('screen.editor.frameDefault')">
              <el-option v-for="f in frameOptions" :key="f" :label="f" :value="f" />
            </el-select>
          </div>
          <div class="field">
            <label>{{ t('screen.editor.fDatasetId') }}</label>
            <el-input v-model="datasetId" size="small" :placeholder="t('screen.editor.datasetIdPh')" />
          </div>
          <div class="field">
            <label>{{ t('screen.editor.fDims') }}</label>
            <el-input v-model="dimsText" type="textarea" :rows="2" placeholder="category" />
          </div>
          <div class="field">
            <label>{{ t('screen.editor.fMeasures') }}</label>
            <el-input v-model="measuresText" type="textarea" :rows="3" placeholder="sales::sum&#10;profit::sum" />
          </div>
          <div class="inline" style="margin-top: 4px">
            <el-button size="small" type="primary" @click="applyFields">{{ t('screen.editor.applyFields') }}</el-button>
          </div>
        </el-collapse-item>

        <el-collapse-item :title="t('screen.editor.sec_data')" name="data">
          <div class="field">
            <label>{{ t('screen.editor.fSourceType') }}</label>
            <el-select v-model="sourceType" size="small">
              <el-option :label="t('screen.editor.srcStatic')" value="static" />
              <el-option :label="t('screen.editor.srcDataset')" value="dataset" />
              <el-option :label="t('screen.editor.srcHttp')" value="http" />
            </el-select>
          </div>
          <template v-if="sourceType === 'http'">
            <div class="field">
              <label>URL</label>
              <el-input v-model="requestUrl" size="small" />
            </div>
            <div class="field inline">
              <el-select v-model="requestMethod" size="small" style="width: 90px">
                <el-option label="GET" value="GET" />
                <el-option label="POST" value="POST" />
              </el-select>
              <el-input-number
                v-model="interval"
                size="small"
                :min="0"
                :max="3600"
                controls-position="right"
                :placeholder="t('screen.editor.intervalPh')"
              />
            </div>
            <div class="field">
              <label>params (JSON)</label>
              <el-input v-model="paramsJson" type="textarea" :rows="2" />
            </div>
            <div class="field">
              <label>headers (JSON)</label>
              <el-input v-model="headersJson" type="textarea" :rows="2" />
            </div>
          </template>
          <div v-if="sourceType === 'static'" class="field">
            <label>{{ t('screen.editor.fStaticJson') }}</label>
            <el-input v-model="staticJson" type="textarea" :rows="6" />
            <div class="inline" style="margin-top: 4px">
              <el-button size="small" @click="useDemoData">{{ t('screen.editor.fillDemo') }}</el-button>
            </div>
          </div>
        </el-collapse-item>

        <el-collapse-item :title="t('screen.editor.sec_advanced')" name="advanced">
          <div class="field">
            <label>props (JSON)</label>
            <el-input v-model="propsJson" type="textarea" :rows="5" />
          </div>
          <div v-if="isChart" class="field">
            <label>{{ t('screen.editor.fOptionsJson') }}</label>
            <el-input v-model="optionsJson" type="textarea" :rows="5" />
          </div>
          <div class="inline" style="margin-top: 6px">
            <el-button size="small" type="primary" @click="applyJson">{{ t('screen.editor.apply') }}</el-button>
            <el-button size="small" @click="resetJson">{{ t('screen.editor.reload') }}</el-button>
            <el-button size="small" type="danger" plain @click="editor.removeComponent(comp.id)">{{
              t('screen.editor.removeComp')
            }}</el-button>
          </div>
        </el-collapse-item>
      </el-collapse>
    </template>
  </div>
</template>

<script setup lang="ts">
  import { computed, ref, watch } from 'vue'
  import { ElMessage } from 'element-plus'
  import type { UploadRequestOptions } from 'element-plus'
  import { useI18n } from 'vue-i18n'
  import { ChartType, DEFAULT_DEMO_DATASET, UNI_NATIVE_CHART_TYPES } from '@dataviz/shared-types'
  import type { ScreenChartFrame, ScreenConfig } from '@dataviz/shared-types'
  import { getComponentDefinition, isChartPreset, styleFieldsOf } from '@dataviz/shared-types'
  import { deleteFile, parseFileId, uploadFile } from '@dataviz/api-client'
  import type { EditorState } from './editorState'

  const { t, te } = useI18n()
  const props = defineProps<{ editor: EditorState }>()
  const editor = props.editor

  const comp = computed(() => editor.selectedComponent.value)
  const doc = computed(() => editor.activeDoc())

  /** 组件标题：优先 i18n（comp.<type>），回落到定义里的中文 label */
  function compLabel(type: string): string {
    const key = `comp.${type}`
    if (te(key)) return t(key)
    return getComponentDefinition(type)?.label || type
  }

  // 计数器上界：组件尺寸不超过画布、位置保持在画布内
  const cMaxW = computed(() => doc.value.width)
  const cMaxH = computed(() => doc.value.height)
  const cMaxX = computed(() => Math.max(0, doc.value.width - (comp.value?.w || 0)))
  const cMaxY = computed(() => Math.max(0, doc.value.height - (comp.value?.h || 0)))

  watch(
    () => [comp.value?.x, comp.value?.y, comp.value?.w, comp.value?.h, doc.value.width, doc.value.height],
    () => {
      if (comp.value) editor.clampActive(comp.value)
    },
  )

  const canvasActive = ref(['base', 'bg', 'grid'])
  const compActive = ref('base')

  const docWidth = computed({
    get: () => doc.value.width,
    set: (v) => {
      doc.value.width = v
      editor.clampAllActive()
    },
  })
  const docHeight = computed({
    get: () => doc.value.height,
    set: (v) => {
      doc.value.height = v
      editor.clampAllActive()
    },
  })
  // ---------- 背景图：仅上传，替换时删除旧文件 ----------
  const bgUploading = ref(false)
  const bgImageUrl = computed({
    get: () => doc.value.config.backgroundImage || '',
    set: (v) => (doc.value.config.backgroundImage = v || undefined),
  })

  async function handleBgUpload(options: UploadRequestOptions) {
    bgUploading.value = true
    const oldUrl = bgImageUrl.value
    try {
      const res = await uploadFile(options.file, { bizType: 'screen' })
      bgImageUrl.value = res.fileUrl
      await releaseBgImage(oldUrl)
      ElMessage.success(t('screen.editor.bgUploaded'))
    } catch (e) {
      ElMessage.error((e as Error).message)
    } finally {
      bgUploading.value = false
    }
  }

  async function handleBgRemove() {
    const oldUrl = bgImageUrl.value
    bgImageUrl.value = ''
    await releaseBgImage(oldUrl)
  }

  /** 旧图不再被任何端引用时才真正删除文件（变体由 pc 配置克隆而来，地址可能相同） */
  async function releaseBgImage(url: string) {
    const fileId = parseFileId(url)
    if (!fileId || stillReferenced(url)) return
    try {
      await deleteFile(fileId)
    } catch (e) {
      console.warn('[screen-bg] delete old image failed:', (e as Error).message)
    }
  }

  function stillReferenced(url: string): boolean {
    const active = doc.value.config
    const configs: (ScreenConfig | undefined)[] = [editor.draft.config]
    for (const v of Object.values(editor.draft.variants || {})) configs.push(v?.config)
    return configs.some((c) => c && c !== active && c.backgroundImage === url)
  }

  const adaptMode = computed({
    get: () => doc.value.config.adaptationMode,
    set: (v) => (doc.value.config.adaptationMode = v),
  })
  const gridSnap = computed({
    get: () => !!doc.value.config.gridSnap,
    set: (v) => (doc.value.config.gridSnap = v),
  })
  const gridSize = computed({
    get: () => doc.value.config.gridSize || 10,
    set: (v) => (doc.value.config.gridSize = v),
  })

  // ---------- 图表配置 ----------
  const chartConfig = computed(() => comp.value?.chartConfig)

  const chartType = computed({
    get: () => chartConfig.value?.chartType,
    set: (v) => {
      const cc = comp.value?.chartConfig
      if (cc && v) cc.chartType = v as ChartType
    },
  })
  const datasetId = computed({
    get: () => String(chartConfig.value?.datasetId ?? ''),
    set: (v) => {
      const cc = comp.value?.chartConfig
      if (cc) cc.datasetId = v || undefined
    },
  })
  const chartFrame = computed<ScreenChartFrame | undefined>({
    get: () => comp.value?.chartFrame,
    set: (v) => {
      if (comp.value) comp.value.chartFrame = v
    },
  })

  const frameOptions = computed<ScreenChartFrame[]>(() =>
    editor.activePlatform.value === 'pc' ? ['internal', 'echarts'] : ['internal', 'ucharts'],
  )
  const chartTypeOptions = computed(() =>
    editor.activePlatform.value === 'pc'
      ? Object.values(ChartType)
      : UNI_NATIVE_CHART_TYPES.filter((t) => t !== ChartType.MAP),
  )

  const isLine = computed(() => chartConfig.value?.chartType === ChartType.LINE)
  const isBar = computed(() =>
    [ChartType.BAR, ChartType.WATERFALL].includes(chartConfig.value?.chartType as ChartType),
  )
  const isPie = computed(() => chartConfig.value?.chartType === ChartType.PIE)
  const isScatter = computed(() => chartConfig.value?.chartType === ChartType.SCATTER)
  const isRadar = computed(() => chartConfig.value?.chartType === ChartType.RADAR)
  const isGauge = computed(() => chartConfig.value?.chartType === ChartType.GAUGE)

  function setOption(key: string, value: unknown) {
    const cc = comp.value?.chartConfig
    if (!cc) return
    if (!cc.options) cc.options = {}
    cc.options[key] = value
  }

  function boolOption(key: string, def = false): boolean {
    const v = chartConfig.value?.options?.[key]
    return v === undefined ? def : !!v
  }

  const optSmooth = computed({ get: () => boolOption('smooth', true), set: (v) => setOption('smooth', v) })
  const optArea = computed({ get: () => boolOption('area'), set: (v) => setOption('area', v) })
  const optSymbol = computed({ get: () => boolOption('symbol', true), set: (v) => setOption('symbol', v) })
  const optStack = computed({ get: () => boolOption('stack'), set: (v) => setOption('stack', v) })
  const optHorizontal = computed({ get: () => boolOption('horizontal'), set: (v) => setOption('horizontal', v) })
  const optDonut = computed({ get: () => boolOption('donut'), set: (v) => setOption('donut', v) })
  const optRose = computed({ get: () => boolOption('rose'), set: (v) => setOption('rose', v) })
  const optBubble = computed({ get: () => boolOption('bubble'), set: (v) => setOption('bubble', v) })
  const optRadarArea = computed({ get: () => boolOption('area'), set: (v) => setOption('area', v) })
  const optGaugeMax = computed({
    get: () => Number(chartConfig.value?.options?.max ?? 100),
    set: (v) => setOption('max', v),
  })

  // ---------- 系列颜色 ----------
  const seriesLabels = computed(() =>
    (chartConfig.value?.measures || []).map((m) => m.alias || m.field),
  )

  function seriesColor(i: number): string | undefined {
    const color = chartConfig.value?.options?.color as string[] | undefined
    return color?.[i]
  }

  function setSeriesColor(i: number, value: string | null) {
    const cc = comp.value?.chartConfig
    if (!cc) return
    if (!cc.options) cc.options = {}
    const color = [...((cc.options.color as string[]) || [])]
    while (color.length <= i) color.push('')
    color[i] = value || ''
    cc.options.color = color
  }

  // ---------- 维度 / 度量 ----------
  const dimsText = ref('')
  const measuresText = ref('')

  function syncFieldTexts() {
    const cc = comp.value?.chartConfig
    dimsText.value = (cc?.dimensions || []).map((d) => (d.alias ? `${d.field}:${d.alias}` : d.field)).join('\n')
    measuresText.value = (cc?.measures || [])
      .map((m) => [m.field, m.alias || '', m.aggregation || ''].join(':').replace(/:+$/, ''))
      .join('\n')
  }

  function applyFields() {
    const cc = comp.value?.chartConfig
    if (!cc) return
    cc.dimensions = dimsText.value
      .split('\n')
      .map((l) => l.trim())
      .filter(Boolean)
      .map((l) => {
        const [field, alias] = l.split(':')
        return alias ? { field, alias } : { field }
      })
    cc.measures = measuresText.value
      .split('\n')
      .map((l) => l.trim())
      .filter(Boolean)
      .map((l) => {
        const [field, alias, aggregation] = l.split(':')
        const m: Record<string, unknown> = { field }
        if (alias) m.alias = alias
        if (aggregation) m.aggregation = aggregation
        return m
      }) as unknown as typeof cc.measures
    ElMessage.success(t('screen.editor.fieldsApplied'))
  }

  // ---------- 组件 props 快捷样式（styleFields 数据驱动） ----------
  const isChart = computed(() => isChartPreset(comp.value?.type || ''))
  const styleFields = computed(() => styleFieldsOf(comp.value?.type || ''))

  function propStr(key: string): string {
    const v = comp.value?.props?.[key]
    return v === undefined || v === null ? '' : String(v)
  }
  function propNum(key: string): number {
    const v = Number(comp.value?.props?.[key])
    return Number.isFinite(v) ? v : 0
  }
  function propBool(key: string): boolean {
    return !!comp.value?.props?.[key]
  }
  function setProp(key: string, value: unknown): void {
    const c = comp.value
    if (!c) return
    if (!c.props) c.props = {}
    c.props[key] = value === null ? undefined : value
  }

  // ---------- 数据源 ----------
  function ensureRequest() {
    if (comp.value && !comp.value.request) {
      comp.value.request = { sourceType: 'static' }
    }
    return comp.value?.request
  }

  const sourceType = computed({
    get: () => comp.value?.request?.sourceType ?? 'static',
    set: (v) => {
      const r = ensureRequest()
      if (r) r.sourceType = v
    },
  })
  const requestUrl = computed({
    get: () => comp.value?.request?.requestUrl || '',
    set: (v) => {
      const r = ensureRequest()
      if (r) r.requestUrl = v
    },
  })
  const requestMethod = computed({
    get: () => comp.value?.request?.requestMethod || 'GET',
    set: (v) => {
      const r = ensureRequest()
      if (r) r.requestMethod = v
    },
  })
  const interval = computed({
    get: () => comp.value?.request?.interval ?? 0,
    set: (v) => {
      const r = ensureRequest()
      if (r) r.interval = v
    },
  })

  const paramsJson = ref('')
  const headersJson = ref('')
  const staticJson = ref('')
  const propsJson = ref('')
  const optionsJson = ref('')

  function resetJson() {
    if (!comp.value) return
    propsJson.value = JSON.stringify(comp.value.props ?? {}, null, 2)
    optionsJson.value = JSON.stringify(comp.value.chartConfig?.options ?? {}, null, 2)
    const r = comp.value.request
    paramsJson.value = r?.requestParams ? JSON.stringify(r.requestParams, null, 2) : ''
    headersJson.value = r?.requestHeaders ? JSON.stringify(r.requestHeaders, null, 2) : ''
    staticJson.value = r && r.staticData !== undefined ? JSON.stringify(r.staticData, null, 2) : ''
  }
  watch(() => comp.value?.id, () => { resetJson(); syncFieldTexts() }, { immediate: true })

  function useDemoData() {
    const r = ensureRequest()
    if (r) {
      r.staticData = JSON.parse(JSON.stringify(DEFAULT_DEMO_DATASET))
      staticJson.value = JSON.stringify(r.staticData, null, 2)
      ElMessage.success(t('screen.editor.demoFilled'))
    }
  }

  function parseJson(text: string, label: string): unknown {
    try {
      return JSON.parse(text)
    } catch (e) {
      ElMessage.error(t('screen.editor.invalidJson', { label }))
      throw e
    }
  }

  function applyJson() {
    if (!comp.value) return
    comp.value.props = parseJson(propsJson.value || '{}', 'props') as Record<string, unknown>
    if (comp.value.chartConfig) {
      comp.value.chartConfig.options = parseJson(optionsJson.value || '{}', 'chartConfig.options') as Record<string, unknown>
    }
    const r = ensureRequest()
    if (r) {
      if (r.sourceType === 'http') {
        if (paramsJson.value.trim()) r.requestParams = parseJson(paramsJson.value, 'params') as Record<string, unknown>
        if (headersJson.value.trim()) r.requestHeaders = parseJson(headersJson.value, 'headers') as Record<string, unknown>
      }
      if (r.sourceType === 'static' && staticJson.value.trim()) {
        r.staticData = parseJson(staticJson.value, 'staticData')
      }
    }
    ElMessage.success(t('screen.editor.applied'))
  }
</script>

<style lang="scss" scoped>
  .property-panel {
    padding: var(--dv-space-md);
    overflow-y: auto;
    h4 {
      color: var(--dv-text-1);
      margin: 0 0 10px;
      font-size: var(--dv-font-sm);
      letter-spacing: 0.3px;
    }
    .field {
      margin-bottom: var(--dv-space-sm);
      label {
        display: block;
        font-size: var(--dv-font-xs);
        color: var(--dv-text-3);
        margin-bottom: 3px;
      }
      .inline {
        display: flex;
        gap: 6px;
        align-items: center;
      }
      .checks {
        flex-wrap: wrap;
      }
      .grid2 {
        display: grid;
        grid-template-columns: 1fr 1fr;
        gap: 6px;
        :deep(.el-input-number) {
          width: 100%;
        }
      }
    }
    .series-color-row {
      display: flex;
      align-items: center;
      gap: var(--dv-space-sm);
      margin-bottom: 4px;
      .series-name {
        font-size: var(--dv-font-xs);
        color: var(--dv-text-2);
        min-width: 60px;
      }
    }
    .tip {
      color: var(--dv-text-4);
      font-size: var(--dv-font-xs);
    }
    .bg-thumb {
      display: block;
      width: 100%;
      max-height: 96px;
      object-fit: cover;
      border: 1px solid var(--dv-border);
      border-radius: var(--dv-radius-sm);
      margin-bottom: 6px;
    }
    .bg-actions {
      flex-wrap: wrap;
    }
    :deep(.el-input-number .el-input__inner) {
      text-align: left;
    }
  }
  .form-collapse {
    border: none;
    :deep(.el-collapse-item__header) {
      background: transparent;
      color: var(--dv-text-1);
      border-color: var(--dv-border);
      font-size: var(--dv-font-sm);
    }
    :deep(.el-collapse-item__wrap) {
      background: transparent;
      border-color: var(--dv-border);
    }
    :deep(.el-collapse-item__content) {
      color: var(--dv-text-2);
      padding-bottom: var(--dv-space-sm);
    }
    :deep(.el-checkbox__label) {
      color: var(--dv-text-2);
      font-size: var(--dv-font-xs);
    }
  }
</style>
