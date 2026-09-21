<template>
  <div ref="wrapRef" class="screen-engine-wrap">
    <div
      class="screen-engine"
      :style="screenStyle"
    >
      <template v-for="comp in visibleComponents" :key="comp.id">
        <component
          :is="getComponentByType(comp.type)"
          :component="comp"
          :style="componentStyle(comp)"
          @click="handleComponentClick(comp, $event)"
        />
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
  import { computed, ref, onMounted, onBeforeUnmount } from 'vue'
  import type { Screen, ScreenComponent } from '@dataviz/shared-types'
  import { CANVAS_BG_COLOR } from '@dataviz/shared-types'
  import { resolveScreenRenderer } from './render/componentRegistry'
  import { RenderPipeline } from './render/RenderPipeline'

  const props = withDefaults(
    defineProps<{
      screen: Screen
      editMode?: boolean
      data?: Record<string, unknown>
    }>(),
    {
      editMode: false,
    },
  )

  const emit = defineEmits<{
    (e: 'component:click', comp: ScreenComponent, evt: MouseEvent): void
    (e: 'ready'): void
  }>()

  const wrapRef = ref<HTMLDivElement | null>(null)
  const pipeline = new RenderPipeline()

  const visibleComponents = computed(() =>
    props.screen.components.filter((c) => c.visible).sort((a, b) => a.zIndex - b.zIndex),
  )

  // 展示端自适应：实时按容器尺寸缩放并居中，坐标与编辑器画布（文档空间）一一对应
  const wrapSize = ref({ w: 0, h: 0 })
  let observer: ResizeObserver | null = null
  const mode = computed(() => props.screen.config?.adaptationMode || 'scale')

  const fitScale = computed(() => {
    const { width, height } = props.screen
    if (!wrapSize.value.w || !wrapSize.value.h) return 1
    if (mode.value === 'fixed-width') return wrapSize.value.w / width
    if (mode.value === 'responsive') return 1
    return Math.min(wrapSize.value.w / width, wrapSize.value.h / height)
  })

  const offsetX = computed(() => {
    if (mode.value === 'responsive') return 0
    return Math.max(0, (wrapSize.value.w - props.screen.width * fitScale.value) / 2)
  })
  const offsetY = computed(() => {
    if (mode.value !== 'scale') return 0
    return Math.max(0, (wrapSize.value.h - props.screen.height * fitScale.value) / 2)
  })

  const screenStyle = computed(() => {
    const bgImg = props.screen.config?.backgroundImage
    return {
      width: `${props.screen.width}px`,
      height: `${props.screen.height}px`,
      backgroundColor: CANVAS_BG_COLOR,
      backgroundImage: bgImg ? `url(${bgImg})` : undefined,
      backgroundSize: props.screen.config?.backgroundSize || 'cover',
      backgroundRepeat: props.screen.config?.backgroundRepeat || 'no-repeat',
      position: 'absolute' as const,
      left: `${offsetX.value}px`,
      top: `${offsetY.value}px`,
      transform: `scale(${fitScale.value})`,
      transformOrigin: 'top left',
      overflow: 'hidden',
    }
  })

  function componentStyle(comp: ScreenComponent) {
    return {
      position: 'absolute' as const,
      left: `${comp.x}px`,
      top: `${comp.y}px`,
      width: `${comp.w}px`,
      height: `${comp.h}px`,
      zIndex: comp.zIndex,
      cursor: props.editMode ? 'move' : 'default',
    }
  }

  function getComponentByType(type: string): unknown {
    return resolveScreenRenderer(type)
  }

  function handleComponentClick(comp: ScreenComponent, evt: MouseEvent) {
    emit('component:click', comp, evt)
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
    }
    emit('ready')
  })

  onBeforeUnmount(() => {
    observer?.disconnect()
    observer = null
    pipeline.dispose()
  })
</script>

<style scoped>
  .screen-engine-wrap {
    position: relative;
    width: 100%;
    height: 100%;
    overflow: hidden;
  }
  .screen-engine {
    font-family: 'PingFang SC', 'Microsoft YaHei', sans-serif;
    color: #e0e6ed;
    box-sizing: border-box;
  }
</style>
