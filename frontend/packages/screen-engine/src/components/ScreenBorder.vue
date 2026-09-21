<template>
  <div class="screen-border" :class="`bv-${variant}`" :style="borderStyle">
    <template v-if="variant === 'corner'">
      <span v-for="c in corners" :key="c" class="bd-corner" :class="c" :style="cornerStyle" />
    </template>
    <div class="border-content">
      <slot></slot>
    </div>
  </div>
</template>

<script setup lang="ts">
  import { computed } from 'vue'
  import type { CSSProperties } from 'vue'
  import type { ScreenComponent } from '@dataviz/shared-types'
  import { CANVAS_INK } from '@dataviz/shared-types'
  import { numOf, strOf } from './props'

  const props = defineProps<{
    component: ScreenComponent
  }>()

  const corners = ['tl', 'tr', 'bl', 'br']
  const p = computed(() => props.component.props || {})
  const variant = computed(() => strOf(p.value.variant, 'glow'))
  const color = computed(() => strOf(p.value.borderColor, CANVAS_INK.accent))
  const width = computed(() => numOf(p.value.borderWidth, 1))
  // radius 为新键名，borderRadius 兼容历史配置
  const radius = computed(() => numOf(p.value.radius ?? p.value.borderRadius, 4))

  const borderStyle = computed<CSSProperties>(() => {
    const base: CSSProperties = {
      width: '100%',
      height: '100%',
      boxSizing: 'border-box',
      borderRadius: `${radius.value}px`,
    }
    if (variant.value === 'line') base.border = `${width.value}px solid ${color.value}`
    else if (variant.value === 'dash')
      base.border = `${width.value}px dashed ${color.value}`
    else if (variant.value === 'glow') {
      base.border = `${width.value}px solid ${color.value}55`
      base.boxShadow = `inset 0 0 12px ${color.value}33, 0 0 10px ${color.value}22`
      base.background = 'rgba(10,25,41,0.28)'
    } else {
      base.border = `1px solid ${color.value}22`
      base.background = 'rgba(10,25,41,0.22)'
    }
    return base
  })

  const cornerStyle = computed(() => ({
    borderColor: color.value,
    width: `${Math.max(10, width.value * 8)}px`,
    height: `${Math.max(10, width.value * 8)}px`,
    borderTopWidth: `${width.value}px`,
    borderRightWidth: `${width.value}px`,
    borderBottomWidth: `${width.value}px`,
    borderLeftWidth: `${width.value}px`,
  }))
</script>

<style scoped>
  .screen-border {
    position: relative;
    padding: 4px;
  }
  .border-content {
    width: 100%;
    height: 100%;
    position: relative;
  }
  .bd-corner {
    position: absolute;
    border-style: solid;
  }
  .bd-corner.tl {
    left: -1px;
    top: -1px;
    border-right: 0 !important;
    border-bottom: 0 !important;
  }
  .bd-corner.tr {
    right: -1px;
    top: -1px;
    border-left: 0 !important;
    border-bottom: 0 !important;
  }
  .bd-corner.bl {
    left: -1px;
    bottom: -1px;
    border-right: 0 !important;
    border-top: 0 !important;
  }
  .bd-corner.br {
    right: -1px;
    bottom: -1px;
    border-left: 0 !important;
    border-top: 0 !important;
  }
</style>
