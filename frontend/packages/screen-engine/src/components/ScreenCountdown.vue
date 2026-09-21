<template>
  <div class="screen-countdown" :style="{ color }">
    <span v-if="label" class="cd-label">{{ label }}</span>
    <span v-for="seg in segments" :key="seg.key" class="cd-seg">
      <span class="cd-box" :style="boxStyle">{{ seg.text }}</span>
      <span v-if="seg.key !== 's'" class="cd-colon">:</span>
    </span>
  </div>
</template>

<script setup lang="ts">
  import { computed, ref } from 'vue'
  import type { ScreenComponent } from '@dataviz/shared-types'
  import { CANVAS_INK } from '@dataviz/shared-types'
  import { numOf, pxOf, strOf, useInterval } from './props'

  const props = defineProps<{
    component: ScreenComponent
  }>()

  const p = computed(() => props.component.props || {})
  const label = computed(() => strOf(p.value.label))
  const color = computed(() => strOf(p.value.color, CANVAS_INK.warm))
  const total = computed(() => Math.max(1, numOf(p.value.seconds, 3600)))
  const remain = ref(total.value)

  const boxStyle = computed(() => ({
    background: strOf(p.value.boxColor, CANVAS_INK.faint),
    fontSize: pxOf(p.value.fontSize, 22),
  }))

  const segments = computed(() => {
    const s = Math.max(0, Math.floor(remain.value))
    const day = Math.floor(s / 86400)
    const keys = day > 0 ? ['d', 'h', 'm', 's'] : ['h', 'm', 's']
    const units =
      day > 0
        ? [day, Math.floor((s % 86400) / 3600), Math.floor((s % 3600) / 60), s % 60]
        : [Math.floor(s / 3600), Math.floor((s % 3600) / 60), s % 60]
    return keys.map((key, i) => ({ key, text: String(units[i]).padStart(2, '0') }))
  })

  useInterval(() => {
    remain.value = remain.value > 1 ? remain.value - 1 : total.value
  }, 1000)
</script>

<style scoped>
  .screen-countdown {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 8px;
    width: 100%;
    height: 100%;
    box-sizing: border-box;
  }
  .cd-label {
    font-size: 13px;
    opacity: 0.85;
  }
  .cd-seg {
    display: flex;
    align-items: center;
    gap: 6px;
  }
  .cd-box {
    min-width: 2em;
    padding: 2px 6px;
    text-align: center;
    border-radius: 4px;
    font-family: 'DIN Alternate', Arial, sans-serif;
    font-weight: 700;
    font-variant-numeric: tabular-nums;
    letter-spacing: 1px;
  }
  .cd-colon {
    opacity: 0.7;
  }
</style>
