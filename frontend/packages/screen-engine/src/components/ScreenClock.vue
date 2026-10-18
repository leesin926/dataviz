<template>
  <div class="screen-clock" :style="clockStyle">
    <div class="time">{{ time }}</div>
    <div class="date">{{ date }}</div>
  </div>
</template>

<script setup lang="ts">
  import { ref, computed } from 'vue'
  import type { ScreenComponent } from '@dataviz/shared-types'
  import { CANVAS_INK } from '@dataviz/shared-types'
  import { DATE_FORMATS, formatDate } from '@dataviz/shared-utils'
  import type { DateFormat } from '@dataviz/shared-utils'
  import { pxOf, strOf, useInterval } from './props'

  const props = defineProps<{
    component: ScreenComponent
  }>()

  const time = ref('')
  const date = ref('')

  const p = computed(() => props.component.props || {})

  const clockStyle = computed(() => ({
    color: strOf(p.value.color, CANVAS_INK.normal),
    fontSize: pxOf(p.value.fontSize, 32),
    textAlign: 'center' as const,
    width: '100%',
    height: '100%',
    display: 'flex',
    flexDirection: 'column' as const,
    justifyContent: 'center',
    alignItems: 'center',
  }))

  useInterval(() => {
    const now = new Date()
    time.value = formatDate(now, 'HH:mm:ss')
    // dateFormat 是设计器里的自由文本，不在枚举内就退回默认，不能直接把 string 塞给 formatDate
    const raw = strOf(p.value.dateFormat, 'YYYY-MM-DD')
    const format = (DATE_FORMATS as readonly string[]).includes(raw) ? (raw as DateFormat) : 'YYYY-MM-DD'
    date.value = formatDate(now, format)
  }, 1000)
</script>

<style scoped>
  .screen-clock .time {
    font-family: 'DIN', 'Arial', sans-serif;
    font-weight: bold;
    letter-spacing: 2px;
  }
  .screen-clock .date {
    font-size: 0.6em;
    opacity: 0.8;
    margin-top: 4px;
  }
</style>
