<template>
  <div
    ref="wrapRef"
    class="editor-canvas"
    @mousedown.self="editor.selectedId.value = null"
    @dragover="onDragOver"
    @drop="onDrop"
  >
    <div class="canvas-viewport" :style="viewportStyle">
      <div
        ref="stageRef"
        class="canvas-stage"
        :style="stageStyle"
        @mousedown.self="editor.selectedId.value = null"
      >
        <ScreenWidget
          v-for="comp in sortedComponents"
          :key="comp.id"
          :x="comp.x"
          :y="comp.y"
          :width="comp.w"
          :height="comp.h"
          :selected="editor.selectedId.value === comp.id"
          :locked="editor.effectiveLocked(comp)"
          :editing="true"
          :scale="scale"
          @select="editor.selectedId.value = comp.id"
          @move="(x, y) => moveComp(comp, x, y)"
          @resize="(w, h) => resizeComp(comp, w, h)"
        >
          <component :is="rendererFor(comp.type)" v-if="editor.effectiveVisible(comp)" :component="comp" />
        </ScreenWidget>
      </div>
      <div v-if="showGrid" class="grid-overlay" :style="gridStyle" />
    </div>

    <div class="zoom-bar">
      <button class="zb" :title="t('screen.editor.zoomOut')" @click="stepZoom(-1)">−</button>
      <span class="zb zb-pct" :title="t('screen.editor.zoomReset')" @click="setOne">{{ zoomPercent }}%</span>
      <button class="zb" :title="t('screen.editor.zoomIn')" @click="stepZoom(1)">＋</button>
      <button class="zb zb-fit" :class="{ on: fitMode }" :title="t('screen.editor.zoomFit')" @click="fitToViewport">
        {{ t('screen.editor.fitBtn') }}
      </button>
    </div>
    <div class="canvas-zoom-tip">{{ docSize.width }}×{{ docSize.height }} · {{ t('screen.editor.dropTip') }}</div>
  </div>
</template>

<script setup lang="ts">
  import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
  import type { Component } from 'vue'
  import { useI18n } from 'vue-i18n'
  import type { ScreenComponent } from '@dataviz/shared-types'
  import { isDarkColor } from '@dataviz/shared-types'
  import { ScreenWidget, resolveScreenRenderer } from '@dataviz/screen-engine'
  import { CANVAS_BG_COLOR, type EditorState } from './editorState'

  const props = withDefaults(defineProps<{ editor: EditorState; showGrid?: boolean }>(), {
    showGrid: true,
  })
  const editor = props.editor
  const { t } = useI18n()

  const wrapRef = ref<HTMLDivElement | null>(null)
  const stageRef = ref<HTMLDivElement | null>(null)
  const wrapSize = ref({ w: 0, h: 0 })
  let observer: ResizeObserver | null = null

  const docSize = computed(() => {
    const doc = editor.activeDoc()
    return { width: doc.width, height: doc.height }
  })

  // 缩放：默认自适应，用户可手动放大/缩小；改画布尺寸或换端时回到自适应
  const fitScale = computed(() => {
    const { width, height } = docSize.value
    if (!wrapSize.value.w || !wrapSize.value.h) return 1
    // 不限制为 1：可视区小于画布时缩到恰好铺满（大于画布时由 1 封顶）
    return Math.max(0.05, Math.min(1, (wrapSize.value.w - 48) / width, (wrapSize.value.h - 48) / height))
  })
  const fitMode = ref(true)
  const userZoom = ref(100)
  const scale = computed(() => (fitMode.value ? fitScale.value : userZoom.value / 100))
  const zoomPercent = computed(() => Math.round(scale.value * 100))

  watch(
    () => [docSize.value.width, docSize.value.height, editor.activePlatform.value],
    () => {
      fitMode.value = true
    },
  )

  const ZOOM_MIN = 10
  const ZOOM_MAX = 400
  const ZOOM_STEP = 10

  /** 以当前实际缩放为基准连续加减 10%，避免档位表造成的跨级跳变（如自适应 47% 直接跳到 125%） */
  function stepZoom(dir: 1 | -1) {
    const next = Math.round(zoomPercent.value + dir * ZOOM_STEP)
    fitMode.value = false
    userZoom.value = Math.min(ZOOM_MAX, Math.max(ZOOM_MIN, next))
  }

  function setOne() {
    fitMode.value = false
    userZoom.value = 100
  }

  function fitToViewport() {
    fitMode.value = true
  }

  const viewportStyle = computed(() => ({
    width: `${Math.ceil(docSize.value.width * scale.value)}px`,
    height: `${Math.ceil(docSize.value.height * scale.value)}px`,
  }))

  // 舞台所见即所得：直接渲染画布配置的底色与底图（与展示端同一套样式）
  const stageStyle = computed(() => {
    const doc = editor.activeDoc()
    const cfg = doc.config || ({} as NonNullable<typeof doc.config>)
    const bgImg = cfg.backgroundImage
    return {
      width: `${doc.width}px`,
      height: `${doc.height}px`,
      transform: `scale(${scale.value})`,
      backgroundColor: CANVAS_BG_COLOR,
      backgroundImage: bgImg ? `url(${bgImg})` : undefined,
      backgroundSize: cfg.backgroundSize || 'cover',
      backgroundRepeat: cfg.backgroundRepeat || 'no-repeat',
    }
  })

  /** 依据画布底色明暗切换网格线色，保证深/浅底都看得见 */
  const stageDark = computed(() => isDarkColor(CANVAS_BG_COLOR))

  const gridStyle = computed(() => {
    const doc = editor.activeDoc()
    let step = (doc.config?.gridSize || 10) * scale.value
    // 缩放过小时按 5 倍递增网格间距，保证可见
    while (step < 8) step *= 5
    const line = stageDark.value ? 'rgba(255,255,255,0.10)' : 'var(--dv-border)'
    const major = stageDark.value ? 'rgba(255,255,255,0.22)' : 'var(--dv-border-strong)'
    return {
      backgroundImage: [
        `linear-gradient(to right, ${major} 1px, transparent 1px)`,
        `linear-gradient(to bottom, ${major} 1px, transparent 1px)`,
        `linear-gradient(to right, ${line} 1px, transparent 1px)`,
        `linear-gradient(to bottom, ${line} 1px, transparent 1px)`,
      ].join(', '),
      backgroundSize: `${step * 5}px ${step * 5}px, ${step * 5}px ${step * 5}px, ${step}px ${step}px, ${step}px ${step}px`,
    }
  })

  const sortedComponents = computed(() =>
    [...editor.activeComponents.value].sort((a, b) => a.zIndex - b.zIndex),
  )

  // ScreenWidget 已按 scale 换算，这里收到的就是文档坐标
  function moveComp(comp: ScreenComponent, x: number, y: number) {
    comp.x = Math.round(x)
    comp.y = Math.round(y)
    editor.clampActive(comp)
  }

  function resizeComp(comp: ScreenComponent, w: number, h: number) {
    comp.w = Math.round(w)
    comp.h = Math.round(h)
    editor.clampActive(comp)
  }

  // 拖拽添加：组件面板 dataTransfer 携带 type，落点即创建坐标（换算回文档空间，clamp 保证不越界）
  function onDragOver(e: DragEvent) {
    if (!e.dataTransfer?.types.includes('application/x-screen-comp')) return
    e.preventDefault()
    e.dataTransfer.dropEffect = 'copy'
  }

  function onDrop(e: DragEvent) {
    const type = e.dataTransfer?.getData('application/x-screen-comp')
    if (!type) return
    e.preventDefault()
    const rect = stageRef.value?.getBoundingClientRect()
    if (!rect) return
    const s = scale.value || 1
    editor.addComponentByType(type, {
      x: (e.clientX - rect.left) / s,
      y: (e.clientY - rect.top) / s,
    })
  }

  // 渲染器与展示端共用一份注册表（新增组件只需在 screen-engine 注册）
  function rendererFor(type: string): Component {
    return resolveScreenRenderer(type)
  }

  /** 滚轮缩放：以指针位置无关的连续 10% 步进；按住 Ctrl/Shift 时交还原生滚动 */
  function onWheel(e: WheelEvent) {
    if (e.ctrlKey || e.metaKey || e.shiftKey) return
    e.preventDefault()
    stepZoom(e.deltaY < 0 ? 1 : -1)
  }

  onMounted(() => {
    if (wrapRef.value) {
      // 兜底：隐藏标签页中 ResizeObserver 不派发回调，需先直接测量一次
      wrapSize.value = { w: wrapRef.value.clientWidth, h: wrapRef.value.clientHeight }
      if (typeof ResizeObserver !== 'undefined') {
        observer = new ResizeObserver((entries) => {
          const r = entries[0].contentRect
          wrapSize.value = { w: r.width, h: r.height }
        })
        observer.observe(wrapRef.value)
      }
      wrapRef.value.addEventListener('wheel', onWheel, { passive: false })
    }
  })

  onBeforeUnmount(() => {
    observer?.disconnect()
    wrapRef.value?.removeEventListener('wheel', onWheel)
  })
</script>

<style lang="scss" scoped>
  .editor-canvas {
    flex: 1;
    display: flex;
    align-items: center;
    justify-content: center;
    overflow: auto;
    background: var(--dv-surface-sunken);
    position: relative;
  }
  .canvas-viewport {
    position: relative;
    box-shadow: 0 0 0 1px var(--dv-border), var(--dv-shadow-lg);
  }
  .canvas-stage {
    position: relative;
    transform-origin: top left;
    overflow: hidden;
  }
  /* 舞台内嵌网页只作展示，鼠标事件留给画布，避免抢占选中与拖拽 */
  .canvas-stage :deep(iframe) {
    pointer-events: none;
  }
  .grid-overlay {
    position: absolute;
    inset: 0;
    pointer-events: none;
    z-index: 9999;
  }
  .zoom-bar {
    position: absolute;
    left: 10px;
    bottom: var(--dv-space-sm);
    display: flex;
    gap: 4px;
    z-index: 10000;
    .zb {
      min-width: 26px;
      height: 24px;
      padding: 0 6px;
      background: var(--dv-surface-1);
      border: 1px solid var(--dv-border);
      border-radius: var(--dv-radius-sm);
      color: var(--dv-text-2);
      font-size: var(--dv-font-xs);
      cursor: pointer;
      line-height: 22px;
      &:hover {
        color: var(--dv-primary);
        border-color: var(--dv-primary);
      }
    }
    .zb-pct {
      cursor: pointer;
      color: var(--dv-primary);
      border-color: var(--dv-border-strong);
    }
    .zb-fit.on {
      color: var(--dv-primary);
      border-color: var(--dv-primary);
      background: var(--dv-primary-soft);
    }
  }
  .canvas-zoom-tip {
    position: absolute;
    right: 10px;
    bottom: var(--dv-space-sm);
    font-size: var(--dv-font-xs);
    color: var(--dv-text-3);
  }
</style>
