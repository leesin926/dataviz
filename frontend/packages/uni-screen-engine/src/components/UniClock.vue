<template>
  <view class="uni-clock" :style="{ color, fontSize: size + 'px' }">
    <text class="uni-clock__time" :style="{ color, fontSize: size + 'px' }">{{ time }}</text>
    <text v-if="dateText" class="uni-clock__date" :style="{ color, fontSize: dateSize + 'px' }">{{ dateText }}</text>
  </view>
</template>

<script setup lang="ts">
  /** 时钟：每秒自走，与 PC 端 ScreenClock 同口径 */
  import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
  import { CANVAS_INK } from '@dataviz/shared-types'
  import { scaledFontSize } from '../core/scale'
  import { numOf, strOf, type UniRendererProps } from './props'

  const props = defineProps<UniRendererProps>()

  const p = computed(() => props.component.props || {})
  const color = computed(() => strOf(p.value.color, CANVAS_INK.normal))
  const size = computed(() => scaledFontSize(numOf(p.value.fontSize, 32), (props.scale ?? 1)))
  const dateSize = computed(() => Math.max(9, Math.round(size.value * 0.45)))
  const pattern = computed(() => (p.value.dateFormat === false ? '' : strOf(p.value.dateFormat, 'YYYY-MM-DD')))

  const now = ref(Date.now())
  let timer: ReturnType<typeof setInterval> | null = null

  const pad = (n: number) => (n < 10 ? `0${n}` : `${n}`)
  const time = computed(() => {
    const d = new Date(now.value)
    return `${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
  })
  const dateText = computed(() => {
    if (!pattern.value) return ''
    const d = new Date(now.value)
    return pattern.value
      .replace('YYYY', String(d.getFullYear()))
      .replace('MM', pad(d.getMonth() + 1))
      .replace('DD', pad(d.getDate()))
  })

  onMounted(() => {
    timer = setInterval(() => {
      now.value = Date.now()
    }, 1000)
  })

  onBeforeUnmount(() => {
    if (timer) clearInterval(timer)
  })
</script>

<style scoped>
  .uni-clock {
    width: 100%;
    height: 100%;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
  }
  .uni-clock__time {
    font-variant-numeric: tabular-nums;
  }
</style>
