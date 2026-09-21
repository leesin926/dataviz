<template>
  <div class="screen-water-polo">
    <svg class="wp" viewBox="0 0 100 100" preserveAspectRatio="none">
      <defs>
        <clipPath :id="clipId">
          <circle cx="50" cy="50" r="46" />
        </clipPath>
      </defs>
      <circle cx="50" cy="50" r="46" :fill="bgColor" :stroke="color" stroke-opacity="0.55" stroke-width="1.5" />
      <g :clip-path="`url(#${clipId})`">
        <path :d="wave" class="wp-wave wp-back" :fill="color" />
        <path :d="wave" class="wp-wave wp-front" :fill="color" />
      </g>
      <text
        x="50"
        y="48"
        text-anchor="middle"
        :fill="textColor"
        :font-size="numSize"
        class="wp-num"
      >
        {{ percent }}%
      </text>
      <text
        v-if="label"
        x="50"
        y="64"
        text-anchor="middle"
        :fill="textColor"
        :font-size="labelSize"
        opacity="0.8"
      >
        {{ label }}
      </text>
    </svg>
  </div>
</template>

<script setup lang="ts">
  import { computed, getCurrentInstance } from 'vue'
  import type { ScreenComponent } from '@dataviz/shared-types'
  import { CANVAS_INK } from '@dataviz/shared-types'
  import { numOf, strOf } from './props'

  const uid = getCurrentInstance()?.uid ?? 0

  const props = defineProps<{
    component: ScreenComponent
  }>()

  const p = computed(() => props.component.props || {})
  const clipId = `wp-clip-${uid}`
  const color = computed(() => strOf(p.value.color, CANVAS_INK.accent))
  const bgColor = computed(() => strOf(p.value.bgColor, CANVAS_INK.faint))
  const textColor = computed(() => strOf(p.value.textColor, CANVAS_INK.normal))
  const label = computed(() => strOf(p.value.label))
  const percent = computed(() => Math.max(0, Math.min(100, numOf(p.value.percent, 0))))
  const numSize = computed(() => String(Math.max(10, 26 - Math.max(0, percent.value - 60) * 0.1)))
  const labelSize = computed(() => '11')

  // 波浪基准线按百分比落位（圆内纵向范围 4~96），路径宽 200 便于无缝平移
  const wave = computed(() => {
    const level = 96 - (percent.value / 100) * 92
    const amp = 4
    let d = `M0 ${level}`
    for (let x = 0; x < 200; x += 25) {
      d += ` q 6.25 ${-amp} 12.5 0 q 6.25 ${amp} 12.5 0`
    }
    return `${d} L200 100 L0 100 Z`
  })
</script>

<style scoped>
  .screen-water-polo {
    width: 100%;
    height: 100%;
    display: flex;
    align-items: center;
    justify-content: center;
  }
  .wp {
    width: 100%;
    height: 100%;
  }
  .wp-wave {
    animation: wp-roll 3s linear infinite;
  }
  .wp-back {
    opacity: 0.28;
    animation-duration: 4.5s;
    animation-direction: reverse;
  }
  .wp-front {
    opacity: 0.62;
  }
  .wp-num {
    font-family: 'DIN Alternate', Arial, sans-serif;
    font-weight: 700;
  }
  @keyframes wp-roll {
    0% {
      transform: translateX(0);
    }
    100% {
      transform: translateX(-100px);
    }
  }
</style>
