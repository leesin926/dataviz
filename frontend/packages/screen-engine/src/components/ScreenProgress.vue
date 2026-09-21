<template>
  <div class="screen-progress">
    <div class="sp-head">
      <span class="sp-label" :style="{ color: textColor }">{{ label }}</span>
      <span v-if="showValue" class="sp-pct" :style="{ color }">{{ percent }}%</span>
    </div>
    <div class="sp-track" :style="trackStyle">
      <div class="sp-fill" :style="fillStyle" />
    </div>
  </div>
</template>

<script setup lang="ts">
  import { computed } from 'vue'
  import type { ScreenComponent } from '@dataviz/shared-types'
  import { CANVAS_INK } from '@dataviz/shared-types'
  import { boolOf, numOf, pxOf, strOf } from './props'

  const props = defineProps<{
    component: ScreenComponent
  }>()

  const p = computed(() => props.component.props || {})
  const label = computed(() => strOf(p.value.label, '进度'))
  const color = computed(() => strOf(p.value.color, CANVAS_INK.accent))
  const showValue = computed(() => boolOf(p.value.showValue, true))
  const percent = computed(() => Math.max(0, Math.min(100, numOf(p.value.percent, 0))))
  const textColor = computed(() => strOf(p.value.textColor, CANVAS_INK.normal))

  const trackStyle = computed(() => ({
    height: pxOf(p.value.height, 12),
    background: strOf(p.value.trackColor, CANVAS_INK.faint),
  }))
  const fillStyle = computed(() => ({
    width: `${percent.value}%`,
    background: `linear-gradient(90deg, ${color.value}88, ${color.value})`,
    boxShadow: `0 0 8px ${color.value}66`,
  }))
</script>

<style scoped>
  .screen-progress {
    display: flex;
    flex-direction: column;
    justify-content: center;
    gap: 6px;
    width: 100%;
    height: 100%;
    box-sizing: border-box;
    padding: 4px 8px;
  }
  .sp-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    font-size: 12px;
  }
  .sp-pct {
    font-family: 'DIN Alternate', Arial, sans-serif;
    font-variant-numeric: tabular-nums;
  }
  .sp-track {
    position: relative;
    width: 100%;
    border-radius: 999px;
    overflow: hidden;
  }
  .sp-fill {
    height: 100%;
    border-radius: 999px;
    transition: width 0.6s ease;
  }
</style>
