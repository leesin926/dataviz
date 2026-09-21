<template>
  <div class="screen-number-flop" :style="wrapStyle">
    <span class="nf-value" :style="valueStyle">{{ display }}</span>
    <span v-if="unit" class="nf-unit" :style="unitStyle">{{ unit }}</span>
  </div>
</template>

<script setup lang="ts">
  import { computed, onBeforeUnmount, ref, watch } from 'vue'
  import type { ScreenComponent } from '@dataviz/shared-types'
  import { CANVAS_INK } from '@dataviz/shared-types'
  import { boolOf, groupNumber, numOf, pxOf, strOf } from './props'

  const props = defineProps<{
    component: ScreenComponent
  }>()

  const p = computed(() => props.component.props || {})
  const unit = computed(() => strOf(p.value.unit))
  const target = computed(() => numOf(p.value.value, 0))
  const display = ref('0')
  let raf = 0

  const wrapStyle = computed(() => ({
    color: strOf(p.value.color, CANVAS_INK.accent),
    background: strOf(p.value.bgColor, 'transparent'),
  }))
  const valueStyle = computed(() => ({ fontSize: pxOf(p.value.fontSize, 30) }))
  const unitStyle = computed(() => ({ fontSize: pxOf(numOf(p.value.fontSize, 30) * 0.45, 13) }))

  function animate(to: number) {
    cancelAnimationFrame(raf)
    const group = boolOf(p.value.group, true)
    const final = () => {
      display.value = groupNumber(to, group)
    }
    // 后台标签页不派发 rAF，直接落到终值，避免数值卡在 0
    if (document.hidden) {
      final()
      return
    }
    const from = numOf(display.value.replace(/,/g, ''), 0)
    const decimals = Number.isInteger(to) ? 0 : 2
    const duration = 900
    const start = performance.now()
    const step = (now: number) => {
      const t = Math.min(1, (now - start) / duration)
      const eased = 1 - Math.pow(1 - t, 3)
      const current = from + (to - from) * eased
      display.value = groupNumber(
        decimals ? Number(current.toFixed(decimals)) : Math.round(current),
        group,
      )
      if (t < 1) raf = requestAnimationFrame(step)
      else final()
    }
    raf = requestAnimationFrame(step)
  }

  watch(target, (v) => animate(v), { immediate: true })
  onBeforeUnmount(() => cancelAnimationFrame(raf))
</script>

<style scoped>
  .screen-number-flop {
    display: flex;
    align-items: baseline;
    justify-content: center;
    gap: 6px;
    width: 100%;
    height: 100%;
    padding: 4px 10px;
    box-sizing: border-box;
    border-radius: 4px;
    font-family: 'DIN Alternate', 'Bebas Neue', Arial, sans-serif;
    font-weight: 700;
    font-variant-numeric: tabular-nums;
    letter-spacing: 1px;
  }
  .nf-unit {
    opacity: 0.75;
    font-weight: 400;
  }
</style>
