<template>
  <div class="screen-ranking" :style="{ color: textColor, fontSize: pxOf(p.fontSize, 13) }">
    <div v-for="(item, i) in items" :key="`${item.name}-${i}`" class="rk-row">
      <span class="rk-index" :class="{ top: i < 3 }" :style="indexStyle(i)">{{ i + 1 }}</span>
      <span class="rk-name">{{ item.name }}</span>
      <span class="rk-track">
        <span class="rk-bar" :style="barStyle(item, i)" />
      </span>
      <span v-if="showValue" class="rk-value">{{ item.value }}</span>
    </div>
    <div v-if="!items.length" class="rk-empty">暂无数据</div>
  </div>
</template>

<script setup lang="ts">
  import { computed } from 'vue'
  import type { ScreenComponent } from '@dataviz/shared-types'
  import { CANVAS_INK } from '@dataviz/shared-types'
  import { boolOf, numOf, pxOf, strOf } from './props'

  interface RankItem {
    name: string
    value: number
  }

  const props = defineProps<{
    component: ScreenComponent
  }>()

  const p = computed(() => props.component.props || {})
  const textColor = computed(() => strOf(p.value.color, CANVAS_INK.normal))
  const barColor = computed(() => strOf(p.value.barColor, CANVAS_INK.accent))
  const showValue = computed(() => boolOf(p.value.showValue, true))
  const items = computed<RankItem[]>(() => {
    const raw = Array.isArray(p.value.items) ? (p.value.items as unknown as RankItem[]) : []
    return [...raw].sort((a, b) => numOf(b?.value, 0) - numOf(a?.value, 0))
  })
  const max = computed(() => items.value.reduce((m, x) => Math.max(m, numOf(x.value, 0)), 1))

  function indexStyle(i: number) {
    return i < 3 ? { background: barColor.value, color: CANVAS_INK.onAccent } : {}
  }
  function barStyle(item: RankItem, i: number) {
    const ratio = numOf(item.value, 0) / max.value
    return {
      width: `${Math.max(2, ratio * 100)}%`,
      opacity: String(Math.max(0.4, 1 - i * 0.08)),
      background: `linear-gradient(90deg, ${barColor.value}66, ${barColor.value})`,
    }
  }
</script>

<style scoped>
  .screen-ranking {
    display: flex;
    flex-direction: column;
    justify-content: center;
    gap: 8px;
    width: 100%;
    height: 100%;
    box-sizing: border-box;
    padding: 6px 8px;
    overflow: hidden;
  }
  .rk-row {
    display: flex;
    align-items: center;
    gap: 8px;
    min-width: 0;
  }
  .rk-index {
    flex-shrink: 0;
    width: 18px;
    height: 18px;
    line-height: 18px;
    text-align: center;
    font-size: 11px;
    border-radius: 3px;
    background: rgba(22, 32, 47, 0.06);
    font-variant-numeric: tabular-nums;
  }
  .rk-index.top {
    font-weight: 700;
  }
  .rk-name {
    flex-shrink: 0;
    width: 5em;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }
  .rk-track {
    flex: 1;
    height: 8px;
    border-radius: 999px;
    background: rgba(22, 32, 47, 0.05);
    overflow: hidden;
  }
  .rk-bar {
    display: block;
    height: 100%;
    border-radius: 999px;
    transition: width 0.5s ease;
  }
  .rk-value {
    flex-shrink: 0;
    min-width: 3em;
    text-align: right;
    font-family: 'DIN Alternate', Arial, sans-serif;
    font-variant-numeric: tabular-nums;
  }
  .rk-empty {
    text-align: center;
    opacity: 0.6;
    font-size: 12px;
  }
</style>
