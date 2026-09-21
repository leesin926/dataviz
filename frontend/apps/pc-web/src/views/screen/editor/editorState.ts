import { computed, reactive, ref } from 'vue'
import type {
  Screen,
  ScreenComponent,
  ScreenConfig,
  ScreenLayer,
  ScreenPlatform,
  ScreenVariant,
} from '@dataviz/shared-types'
import {
  CANVAS_BG_COLOR,
  DEFAULT_DEMO_DATASET,
  ChartType,
  chartConfigFromPreset,
  defaultChartConfig,
  defaultComponentProps,
  getComponentDefinition,
  isRenderableOnPlatform,
  listComponentDefinitions,
  normalizeScreenInk,
  presetDemoData,
} from '@dataviz/shared-types'
import { t } from '@/locales'

/** 编辑器当前端正在编辑的一份自洽配置（pc = 顶层字段，uni = 对应变体） */
export interface VariantDoc {
  width: number
  height: number
  config: ScreenConfig
  components: ScreenComponent[]
  layers: ScreenLayer[]
}

export const PLATFORM_DEFAULT_SIZE: Record<ScreenPlatform, { width: number; height: number }> = {
  pc: { width: 1920, height: 1080 },
  mobile: { width: 375, height: 667 },
  tablet: { width: 1024, height: 768 },
}

export const ALL_PLATFORMS: ScreenPlatform[] = ['pc', 'mobile', 'tablet']

function clone<T>(v: T): T {
  return JSON.parse(JSON.stringify(v)) as T
}

/** 画布背景固定白色（不再开放取色），视觉底由上传的背景图承载 */
export { CANVAS_BG_COLOR }

function defaultConfig(): ScreenConfig {
  return { backgroundColor: CANVAS_BG_COLOR, adaptationMode: 'scale', gridSnap: true, gridSize: 10 }
}

export function newDraftScreen(): Screen {
  return {
    id: 0,
    name: t('screen.editor.defaultName'),
    width: 1920,
    height: 1080,
    components: [],
    layers: [],
    status: 'draft',
    config: defaultConfig(),
    variants: {},
  }
}

export function useEditorState(initial?: Screen) {
  const draft = reactive<Screen>(initial ? normalizeScreen(clone(initial)) : newDraftScreen())
  const activePlatform = ref<ScreenPlatform>('pc')
  const selectedId = ref<string | null>(null)

  function normalizeLayers(layers: ScreenLayer[]): ScreenLayer[] {
    for (const l of layers) {
      l.componentIds = l.componentIds || []
      if (l.visible === undefined) l.visible = true
      if (l.locked === undefined) l.locked = false
    }
    return layers
  }

  function normalizeScreen(s: Screen): Screen {
    s.components = s.components || []
    s.layers = normalizeLayers(s.layers || [])
    // 底色已不开放修改，历史存值一律归一到白色，视觉底交给上传的背景图
    s.config = { ...defaultConfig(), ...(s.config || {}), backgroundColor: CANVAS_BG_COLOR }
    s.variants = s.variants || {}
    for (const p of ['mobile', 'tablet'] as ScreenPlatform[]) {
      const v = s.variants[p]
      if (v) {
        v.components = v.components || []
        v.layers = normalizeLayers(v.layers || [])
        v.config = { ...defaultConfig(), ...(v.config || {}), backgroundColor: CANVAS_BG_COLOR }
        normalizeScreenInk(v)
      }
    }
    // 深色底时代存的浅色前景在白底上等于空白，加载时统一换成画布墨色
    normalizeScreenInk(s)
    return s
  }

  function ensureVariant(p: ScreenPlatform): ScreenVariant {
    if (!draft.variants) draft.variants = {}
    let v = draft.variants[p]
    if (!v) {
      // 同源复制：以 pc 顶层配置为底，尺寸取该端默认值
      v = {
        width: PLATFORM_DEFAULT_SIZE[p].width,
        height: PLATFORM_DEFAULT_SIZE[p].height,
        config: clone(draft.config),
        components: clone(draft.components),
        layers: clone(draft.layers),
      }
      draft.variants[p] = v
      clampDoc(v.components, v.width, v.height)
    }
    return v
  }

  /** 当前编辑目标（活的引用，子组件直接改内部字段） */
  function activeDoc(): VariantDoc {
    const p = activePlatform.value
    if (p === 'pc') {
      return {
        get width() { return draft.width },
        set width(v: number) { draft.width = v },
        get height() { return draft.height },
        set height(v: number) { draft.height = v },
        get config() { return draft.config },
        set config(v: ScreenConfig) { draft.config = v },
        get components() { return draft.components },
        set components(v: ScreenComponent[]) { draft.components = v },
        get layers() { return draft.layers },
        set layers(v: ScreenLayer[]) { draft.layers = v },
      }
    }
    const variant = ensureVariant(p)
    return {
      get width() { return variant.width },
      set width(v: number) { variant.width = v },
      get height() { return variant.height },
      set height(v: number) { variant.height = v },
      get config() { return variant.config },
      set config(v: ScreenConfig) { variant.config = v },
      get components() { return variant.components },
      set components(v: ScreenComponent[]) { variant.components = v },
      get layers() { return variant.layers ?? (variant.layers = []) },
      set layers(v: ScreenLayer[]) { variant.layers = v },
    }
  }

  const activeComponents = computed(() => activeDoc().components)
  const activeLayers = computed(() => activeDoc().layers)
  const selectedComponent = computed(
    () => activeDoc().components.find((c) => c.id === selectedId.value) || null,
  )

  /** 载入已有大屏：归一化后写入 draft */
  function adoptScreen(s: Screen) {
    Object.assign(draft, normalizeScreen(clone(s)))
    selectedId.value = null
  }

  function switchPlatform(p: ScreenPlatform) {
    activePlatform.value = p
    selectedId.value = null
    if (p !== 'pc') ensureVariant(p)
  }

  /** 按图层分组重排 zIndex：未分组在下（按创建序），分组自底向上依次编号 */
  function recomputeZ() {
    const doc = activeDoc()
    let z = 1
    const grouped = new Set<string>()
    for (const layer of doc.layers) {
      for (const cid of layer.componentIds) grouped.add(cid)
    }
    for (const c of doc.components) {
      if (!grouped.has(c.id)) c.zIndex = z++
    }
    for (const layer of doc.layers) {
      for (const cid of layer.componentIds) {
        const c = doc.components.find((x) => x.id === cid)
        if (c) c.zIndex = z++
      }
    }
  }

  /** 几何钳制：组件尺寸不超过画布，且整体保持在画布内 */
  function clampActive(comp: ScreenComponent) {
    const doc = activeDoc()
    comp.w = Math.max(10, Math.min(comp.w, doc.width))
    comp.h = Math.max(10, Math.min(comp.h, doc.height))
    comp.x = Math.max(0, Math.min(comp.x, doc.width - comp.w))
    comp.y = Math.max(0, Math.min(comp.y, doc.height - comp.h))
  }

  /** 拖拽入画布后同步钳制当前端所有组件（覆盖画布缩小后的存量越界） */
  function clampAllActive() {
    for (const c of activeDoc().components) clampActive(c)
  }

  function clampDoc(components: ScreenComponent[], width: number, height: number) {
    for (const c of components) {
      c.w = Math.max(10, Math.min(c.w, width))
      c.h = Math.max(10, Math.min(c.h, height))
      c.x = Math.max(0, Math.min(c.x, width - c.w))
      c.y = Math.max(0, Math.min(c.y, height - c.h))
    }
  }

  function addComponentByType(type: string, at?: { x: number; y: number }): ScreenComponent | null {
    const def = getComponentDefinition(type)
    if (!def) return null
    const doc = activeDoc()
    const n = doc.components.length
    // 组件默认名跟随当前语言；未收录的类型回落到定义里的中文 label
    const nameKey = `comp.${def.type}`
    const nameBase = t(nameKey)
    const comp: ScreenComponent = {
      id: `comp_${Date.now()}_${n}`,
      type: def.type,
      name: `${nameKey === nameBase ? def.label : nameBase}-${n + 1}`,
      x: at ? Math.round(at.x) : 40 + (n % 8) * 16,
      y: at ? Math.round(at.y) : 40 + (n % 8) * 16,
      w: def.defaultSize.w,
      h: def.defaultSize.h,
      zIndex: n + 1,
      visible: true,
      locked: false,
      props: def.defaultProps ? clone(def.defaultProps) : {},
    }
    if (def.chartPreset || def.type === 'chart') {
      const preset = def.chartPreset
      comp.chartConfig = preset
        ? chartConfigFromPreset(preset)
        : defaultChartConfig(def.defaultChartType || ChartType.BAR)
      comp.request = {
        sourceType: 'static',
        staticData: preset ? presetDemoData(preset) : clone(DEFAULT_DEMO_DATASET),
      }
    }
    doc.components.push(comp)
    clampActive(comp)
    selectedId.value = comp.id
    return comp
  }

  function removeComponent(id: string) {
    const doc = activeDoc()
    doc.components = doc.components.filter((c) => c.id !== id)
    for (const layer of doc.layers) {
      layer.componentIds = layer.componentIds.filter((cid) => cid !== id)
    }
    if (selectedId.value === id) selectedId.value = null
    recomputeZ()
  }

  // ---------- 图层分组 ----------

  function createGroup(name?: string): ScreenLayer {
    const doc = activeDoc()
    const group: ScreenLayer = {
      id: `group_${Date.now()}`,
      name: name || t('screen.editor.groupN', { n: doc.layers.length + 1 }),
      visible: true,
      locked: false,
      componentIds: [],
    }
    doc.layers.push(group)
    return group
  }

  function renameGroup(id: string, name: string) {
    const g = activeDoc().layers.find((l) => l.id === id)
    if (g) g.name = name
  }

  function removeGroup(id: string) {
    const doc = activeDoc()
    doc.layers = doc.layers.filter((l) => l.id !== id)
    recomputeZ()
  }

  function toggleGroupVisible(id: string) {
    const g = activeDoc().layers.find((l) => l.id === id)
    if (g) g.visible = !g.visible
  }

  function toggleGroupLock(id: string) {
    const g = activeDoc().layers.find((l) => l.id === id)
    if (g) g.locked = !g.locked
  }

  /** dir=1 上移一层（数组后段为高层） */
  function moveGroup(id: string, dir: 1 | -1) {
    const doc = activeDoc()
    const idx = doc.layers.findIndex((l) => l.id === id)
    const target = idx + dir
    if (idx < 0 || target < 0 || target >= doc.layers.length) return
    const [removed] = doc.layers.splice(idx, 1)
    doc.layers.splice(target, 0, removed)
    recomputeZ()
  }

  /** 组件移入分组（groupId=null 移出分组） */
  function moveComponentToGroup(compId: string, groupId: string | null) {
    const doc = activeDoc()
    for (const layer of doc.layers) {
      layer.componentIds = layer.componentIds.filter((cid) => cid !== compId)
    }
    if (groupId) {
      const g = doc.layers.find((l) => l.id === groupId)
      if (g) g.componentIds.push(compId)
    }
    recomputeZ()
  }

  function groupOfComponent(compId: string): ScreenLayer | null {
    const doc = activeDoc()
    return doc.layers.find((l) => l.componentIds.includes(compId)) || null
  }

  /** 画布渲染用：组件的有效可见/锁定（叠加所属分组） */
  function effectiveVisible(comp: ScreenComponent): boolean {
    if (!comp.visible) return false
    const g = groupOfComponent(comp.id)
    return !g || g.visible
  }

  function effectiveLocked(comp: ScreenComponent): boolean {
    if (comp.locked) return true
    const g = groupOfComponent(comp.id)
    return !!g?.locked
  }

  /** 端间一键复制（目标仅限 uni 端）：pc→uni 走白名单过滤，mobile↔tablet 全量复制 */
  function copyActiveTo(target: ScreenPlatform) {
    if (target === 'pc') return
    const src = activeDoc()
    const components =
      activePlatform.value === 'pc'
        ? src.components.filter((c) => isRenderableOnPlatform(c, target))
        : src.components
    const size = PLATFORM_DEFAULT_SIZE[target]
    const v = (draft.variants![target] = draft.variants![target] || ({} as ScreenVariant))
    v.width = size.width
    v.height = size.height
    v.config = clone(src.config)
    v.components = clone(components)
    v.layers = clone(src.layers)
    clampDoc(v.components, v.width, v.height)
    selectedId.value = null
  }

  /** 保存前兜底：各端重排一次 zIndex */
  function normalizeAll() {
    const p0 = activePlatform.value
    recomputeZ()
    for (const p of ['mobile', 'tablet'] as ScreenPlatform[]) {
      if (p === p0 || !draft.variants?.[p]) continue
      const v = draft.variants[p]!
      let z = 1
      const grouped = new Set<string>()
      for (const layer of v.layers || []) for (const cid of layer.componentIds) grouped.add(cid)
      for (const c of v.components) if (!grouped.has(c.id)) c.zIndex = z++
      for (const layer of v.layers || []) {
        for (const cid of layer.componentIds) {
          const c = v.components.find((x) => x.id === cid)
          if (c) c.zIndex = z++
        }
      }
    }
  }

  /** 组件面板数据源（当前端白名单） */
  function panelDefinitions() {
    return listComponentDefinitions(activePlatform.value)
  }

  /** 组装保存载荷 */
  function buildPayload(name: string) {
    const variants: Partial<Record<ScreenPlatform, ScreenVariant>> = {}
    for (const p of ['mobile', 'tablet'] as ScreenPlatform[]) {
      const v = draft.variants?.[p]
      if (v) {
        variants[p] = { width: v.width, height: v.height, config: v.config, components: v.components, layers: v.layers }
      }
    }
    return {
      name,
      description: draft.description,
      cover: draft.cover,
      width: draft.width,
      height: draft.height,
      config: draft.config,
      components: draft.components,
      layers: draft.layers,
      variants,
    }
  }

  return {
    draft,
    activePlatform,
    selectedId,
    selectedComponent,
    activeComponents,
    activeLayers,
    activeDoc,
    adoptScreen,
    switchPlatform,
    recomputeZ,
    clampActive,
    clampAllActive,
    addComponentByType,
    removeComponent,
    createGroup,
    renameGroup,
    removeGroup,
    toggleGroupVisible,
    toggleGroupLock,
    moveGroup,
    moveComponentToGroup,
    groupOfComponent,
    effectiveVisible,
    effectiveLocked,
    copyActiveTo,
    normalizeAll,
    panelDefinitions,
    buildPayload,
  }
}

export type EditorState = ReturnType<typeof useEditorState>
