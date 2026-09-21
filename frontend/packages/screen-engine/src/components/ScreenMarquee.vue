<template>
  <div class="screen-marquee">
    <div class="mq-track" :style="trackStyle">
      <span v-for="(txt, i) in loopTexts" :key="i" class="mq-item" :style="itemStyle">
        <i class="mq-dot" :style="{ background: color }" />{{ txt }}
      </span>
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
  const color = computed(() => strOf(p.value.color, CANVAS_INK.accent))
  const texts = computed(() => {
    const raw = Array.isArray(p.value.texts) ? (p.value.texts as unknown[]).map(String) : []
    return raw.length ? raw : ['暂无滚动文本']
  })
  // 复制一份实现无缝衔接
  const loopTexts = computed(() => [...texts.value, ...texts.value])

  const trackStyle = computed(() => ({
    animationDuration: `${Math.max(4, numOf(p.value.speed, 24))}s`,
  }))
  const itemStyle = computed(() => ({
    color: strOf(p.value.textColor, CANVAS_INK.normal),
    fontSize: pxOf(p.value.fontSize, 16),
  }))
</script>

<style scoped>
  .screen-marquee {
    width: 100%;
    height: 100%;
    display: flex;
    align-items: center;
    overflow: hidden;
    box-sizing: border-box;
  }
  .mq-track {
    display: inline-flex;
    align-items: center;
    white-space: nowrap;
    animation-name: mq-scroll;
    animation-timing-function: linear;
    animation-iteration-count: infinite;
    will-change: transform;
  }
  .mq-track:hover {
    animation-play-state: paused;
  }
  .mq-item {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    padding-right: 48px;
  }
  .mq-dot {
    width: 6px;
    height: 6px;
    border-radius: 50%;
    flex-shrink: 0;
  }
  @keyframes mq-scroll {
    0% {
      transform: translateX(0);
    }
    100% {
      transform: translateX(-50%);
    }
  }
</style>
