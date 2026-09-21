<template>
  <div class="screen-stat-card" :style="cardStyle">
    <div class="sc-bar" :style="{ background: accent }" />
    <div class="sc-body">
      <div class="sc-title" :style="titleStyle">{{ title }}</div>
      <div class="sc-line">
        <span class="sc-value" :style="valueStyle">{{ valueText }}</span>
        <span v-if="unit" class="sc-unit">{{ unit }}</span>
      </div>
      <div v-if="trend !== null" class="sc-trend" :style="trendStyle">
        {{ trend > 0 ? '▲' : trend < 0 ? '▼' : '—' }} {{ Math.abs(trend) }}%
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
  import { computed } from 'vue'
  import type { ScreenComponent } from '@dataviz/shared-types'
  import { CANVAS_INK } from '@dataviz/shared-types'
  import { groupNumber, numOf, pxOf, strOf } from './props'

  const props = defineProps<{
    component: ScreenComponent
  }>()

  const p = computed(() => props.component.props || {})
  const title = computed(() => strOf(p.value.title, '指标'))
  const unit = computed(() => strOf(p.value.unit))
  const accent = computed(() => strOf(p.value.accent, CANVAS_INK.accent))
  const trend = computed<number | null>(() =>
    typeof p.value.trend === 'number' && Number.isFinite(p.value.trend) ? p.value.trend : null,
  )
  const valueText = computed(() => groupNumber(numOf(p.value.value, 0), true))

  const cardStyle = computed(() => ({
    color: strOf(p.value.color, CANVAS_INK.strong),
    background: strOf(p.value.bgColor, CANVAS_INK.faint),
  }))
  const titleStyle = computed(() => ({ color: `${accent.value}` }))
  const valueStyle = computed(() => ({ fontSize: pxOf(p.value.fontSize, 26) }))
  const trendStyle = computed(() => ({
    color: (trend.value ?? 0) > 0 ? CANVAS_INK.ok : (trend.value ?? 0) < 0 ? CANVAS_INK.danger : CANVAS_INK.muted,
  }))
</script>

<style scoped>
  .screen-stat-card {
    display: flex;
    width: 100%;
    height: 100%;
    box-sizing: border-box;
    border-radius: 6px;
    overflow: hidden;
  }
  .sc-bar {
    width: 3px;
    flex-shrink: 0;
  }
  .sc-body {
    flex: 1;
    display: flex;
    flex-direction: column;
    justify-content: center;
    gap: 4px;
    padding: 8px 12px;
    min-width: 0;
  }
  .sc-title {
    font-size: 12px;
    opacity: 0.9;
    letter-spacing: 0.5px;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }
  .sc-line {
    display: flex;
    align-items: baseline;
    gap: 4px;
  }
  .sc-value {
    font-family: 'DIN Alternate', Arial, sans-serif;
    font-weight: 700;
    font-variant-numeric: tabular-nums;
  }
  .sc-unit {
    font-size: 12px;
    opacity: 0.7;
  }
  .sc-trend {
    font-size: 12px;
    font-variant-numeric: tabular-nums;
  }
</style>
