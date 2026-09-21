<template>
  <div class="screen-scroll-board" :style="boardStyle">
    <div class="sb-row sb-head">
      <span v-for="(col, i) in columns" :key="i">{{ col }}</span>
    </div>
    <div class="sb-body">
      <div class="sb-rows" :style="animationStyle">
        <div v-for="(row, r) in loopRows" :key="r" class="sb-row">
          <span v-for="(cell, c) in row" :key="c">{{ cell }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
  import { computed } from 'vue'
  import type { ScreenComponent } from '@dataviz/shared-types'
  import { CANVAS_INK } from '@dataviz/shared-types'
  import { numOf, pxOf, strOf } from './props'

  const props = defineProps<{
    component: ScreenComponent
  }>()

  const p = computed(() => props.component.props || {})
  const columns = computed(() => (p.value.columns as unknown[]) || [])
  const rows = computed(() => (p.value.rows as unknown[][]) || [])
  // 复制一份实现无缝循环
  const loopRows = computed(() => [...rows.value, ...rows.value])

  const boardStyle = computed(() => ({
    fontSize: pxOf(p.value.fontSize, 14),
    color: strOf(p.value.color, CANVAS_INK.normal),
  }))

  const animationStyle = computed(() => ({
    animation: `sb-scroll ${numOf(p.value.interval, 3) * (rows.value.length || 1)}s linear infinite`,
  }))
</script>

<style scoped>
  .screen-scroll-board {
    width: 100%;
    height: 100%;
    overflow: hidden;
    font-size: 12px;
    display: flex;
    flex-direction: column;
  }
  .sb-head {
    color: inherit;
    background: rgba(22, 32, 47, 0.05);
    flex-shrink: 0;
  }
  .sb-body {
    flex: 1;
    overflow: hidden;
  }
  .sb-row {
    display: flex;
    justify-content: space-between;
    padding: 5px 8px;
    border-bottom: 1px solid rgba(22, 32, 47, 0.08);
  }
  @keyframes sb-scroll {
    0% {
      transform: translateY(0);
    }
    100% {
      transform: translateY(-50%);
    }
  }
</style>
