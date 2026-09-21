/**
 * useScreenScale - 大屏缩放 composable
 * 提供响应式缩放能力
 */
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { ScreenLayout } from '../core/ScreenLayout'

export interface UseScreenScaleOptions {
  designWidth?: number
  designHeight?: number
  mode?: 'scale' | 'fixed-width' | 'responsive'
}

export function useScreenScale(options: UseScreenScaleOptions = {}) {
  const {
    designWidth = 1920,
    designHeight = 1080,
    mode = 'scale',
  } = options

  const containerRef = ref<HTMLElement | null>(null)
  const containerWidth = ref(0)
  const containerHeight = ref(0)
  let resizeObserver: ResizeObserver | null = null

  const layout = new ScreenLayout(designWidth, designHeight)
  layout.setScaleMode(mode)

  const scale = computed(() => {
    layout.setContainerSize(containerWidth.value, containerHeight.value)
    return layout.calculateScale()
  })

  const scaleValue = computed(() => scale.value.scale)
  const scaleX = computed(() => scale.value.scaleX)
  const scaleY = computed(() => scale.value.scaleY)
  const remBase = computed(() => layout.calculateRemBase())

  const containerStyle = computed(() => {
    layout.setContainerSize(containerWidth.value, containerHeight.value)
    return layout.getContainerStyle()
  })

  function updateSize(): void {
    if (!containerRef.value) return
    const rect = containerRef.value.getBoundingClientRect()
    containerWidth.value = rect.width
    containerHeight.value = rect.height
  }

  onMounted(() => {
    updateSize()
    resizeObserver = new ResizeObserver(() => {
      updateSize()
    })
    if (containerRef.value) {
      resizeObserver.observe(containerRef.value)
    }
  })

  onBeforeUnmount(() => {
    resizeObserver?.disconnect()
  })

  return {
    containerRef,
    scale,
    scaleValue,
    scaleX,
    scaleY,
    remBase,
    containerStyle,
    designWidth,
    designHeight,
  }
}
