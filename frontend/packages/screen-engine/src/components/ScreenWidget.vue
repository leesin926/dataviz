<template>
  <div
    class="screen-widget"
    :class="{ 'is-selected': selected, 'is-locked': locked, 'is-editing': editing }"
    :style="widgetStyle"
    @mousedown="handleMouseDown"
  >
    <div class="widget-content">
      <slot />
    </div>

    <!-- Resize handles -->
    <template v-if="editing && selected">
      <div
        v-for="handle in resizeHandles"
        :key="handle.position"
        class="resize-handle"
        :class="`resize-handle--${handle.position}`"
        @mousedown.stop="startResize($event, handle.position)"
      />
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'

const props = withDefaults(
  defineProps<{
    x: number
    y: number
    width: number
    height: number
    selected?: boolean
    locked?: boolean
    editing?: boolean
    /** 画布缩放系数：指针位移是屏幕像素，需除以它换算回文档坐标 */
    scale?: number
  }>(),
  {
    selected: false,
    locked: false,
    editing: false,
    scale: 1,
  }
)

const emit = defineEmits<{
  (e: 'select'): void
  (e: 'move', x: number, y: number): void
  (e: 'resize', width: number, height: number): void
  (e: 'moveEnd', x: number, y: number): void
  (e: 'resizeEnd', width: number, height: number): void
}>()

const isDragging = ref(false)
/** 起始指针（屏幕像素）与起始几何（文档坐标）：位移按 scale 换算，避免缩放态下坐标空间混用 */
const dragStart = ref({ px: 0, py: 0, x: 0, y: 0 })

const resizeHandles = [
  { position: 'top-left' },
  { position: 'top-right' },
  { position: 'bottom-left' },
  { position: 'bottom-right' },
  { position: 'top' },
  { position: 'bottom' },
  { position: 'left' },
  { position: 'right' },
]

const widgetStyle = computed(() => ({
  left: `${props.x}px`,
  top: `${props.y}px`,
  width: `${props.width}px`,
  height: `${props.height}px`,
}))

function handleMouseDown(e: MouseEvent): void {
  if (props.locked || !props.editing) return
  e.preventDefault()
  // 阻断冒泡：画布容器上的 mousedown 会清空选中，选中态不能因点击组件自身而丢失
  e.stopPropagation()
  emit('select')

  isDragging.value = true
  dragStart.value = { px: e.clientX, py: e.clientY, x: props.x, y: props.y }

  document.addEventListener('mousemove', handleMouseMove)
  document.addEventListener('mouseup', handleMouseUp)
}

function handleMouseMove(e: MouseEvent): void {
  if (!isDragging.value) return
  const s = props.scale || 1
  const newX = dragStart.value.x + (e.clientX - dragStart.value.px) / s
  const newY = dragStart.value.y + (e.clientY - dragStart.value.py) / s
  emit('move', Math.max(0, newX), Math.max(0, newY))
}

function handleMouseUp(): void {
  if (isDragging.value) {
    emit('moveEnd', props.x, props.y)
  }
  isDragging.value = false
  document.removeEventListener('mousemove', handleMouseMove)
  document.removeEventListener('mouseup', handleMouseUp)
}

function startResize(e: MouseEvent, _position: string): void {
  if (props.locked) return
  e.preventDefault()

  const s = props.scale || 1
  const startX = e.clientX
  const startY = e.clientY
  const startW = props.width
  const startH = props.height

  function onMove(moveE: MouseEvent): void {
    const dw = (moveE.clientX - startX) / s
    const dh = (moveE.clientY - startY) / s
    emit('resize', Math.max(50, startW + dw), Math.max(50, startH + dh))
  }

  function onUp(): void {
    emit('resizeEnd', props.width, props.height)
    document.removeEventListener('mousemove', onMove)
    document.removeEventListener('mouseup', onUp)
  }

  document.addEventListener('mousemove', onMove)
  document.addEventListener('mouseup', onUp)
}
</script>

<style scoped>
.screen-widget {
  position: absolute;
  box-sizing: border-box;
}

.screen-widget.is-selected {
  outline: 2px solid var(--dv-primary, #409eff);
  outline-offset: -1px;
}

.screen-widget.is-locked {
  opacity: 0.7;
  cursor: not-allowed;
}

.screen-widget.is-editing {
  cursor: move;
}

.widget-content {
  width: 100%;
  height: 100%;
  overflow: hidden;
}

.resize-handle {
  position: absolute;
  width: 8px;
  height: 8px;
  background: var(--dv-primary, #409eff);
  border: 1px solid var(--dv-surface-1, #fff);
  border-radius: 50%;
  z-index: 10;
}

.resize-handle--top-left { top: -4px; left: -4px; cursor: nw-resize; }
.resize-handle--top-right { top: -4px; right: -4px; cursor: ne-resize; }
.resize-handle--bottom-left { bottom: -4px; left: -4px; cursor: sw-resize; }
.resize-handle--bottom-right { bottom: -4px; right: -4px; cursor: se-resize; }
.resize-handle--top { top: -4px; left: 50%; margin-left: -4px; cursor: n-resize; }
.resize-handle--bottom { bottom: -4px; left: 50%; margin-left: -4px; cursor: s-resize; }
.resize-handle--left { top: 50%; left: -4px; margin-top: -4px; cursor: w-resize; }
.resize-handle--right { top: 50%; right: -4px; margin-top: -4px; cursor: e-resize; }
</style>
