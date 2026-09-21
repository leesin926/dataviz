<template>
  <div class="screen-decoration" :class="`dv-${variant}`" :style="rootStyle">
    <template v-if="variant === 'flow'">
      <span class="dv-flow-base" />
      <span class="dv-flow-shine" />
    </template>
    <template v-else-if="variant === 'bars'">
      <span v-for="i in 18" :key="i" class="dv-bar" :style="{ animationDelay: `${(i % 9) * 0.12}s` }" />
    </template>
    <template v-else-if="variant === 'corner'">
      <span v-for="c in corners" :key="c" class="dv-cn" :class="c" />
    </template>
    <template v-else>
      <span class="dv-div-line" />
      <span class="dv-diamond" />
      <span class="dv-div-line is-reverse" />
    </template>
  </div>
</template>

<script setup lang="ts">
  import { computed } from 'vue'
  import type { CSSProperties } from 'vue'
  import type { ScreenComponent } from '@dataviz/shared-types'
  import { CANVAS_INK } from '@dataviz/shared-types'
  import { strOf } from './props'

  const props = defineProps<{
    component: ScreenComponent
  }>()

  const corners = ['tl', 'tr', 'bl', 'br']
  const p = computed(() => props.component.props || {})
  const variant = computed(() => strOf(p.value.variant, 'flow'))
  const color = computed(() => strOf(p.value.color, CANVAS_INK.accent))
  const colorSecond = computed(() => strOf(p.value.colorSecond, CANVAS_INK.muted))

  // CSS 变量注入（类型上绕开 CSSProperties 的白名单）
  const rootStyle = computed(() => ({
    '--dv-deco-a': color.value,
    '--dv-deco-b': colorSecond.value,
    '--dv-deco-fill': CANVAS_INK.faint,
  }) as CSSProperties)
</script>

<style scoped>
  .screen-decoration {
    position: relative;
    width: 100%;
    height: 100%;
    box-sizing: border-box;
    overflow: hidden;
  }
  /* flow：渐变流动条 */
  .dv-flow-base,
  .dv-flow-shine {
    position: absolute;
    left: 0;
    right: 0;
    top: 50%;
    height: 3px;
    margin-top: -1.5px;
    border-radius: 999px;
  }
  .dv-flow-base {
    background: linear-gradient(90deg, transparent, var(--dv-deco-a), var(--dv-deco-b), transparent);
    opacity: 0.45;
  }
  .dv-flow-shine {
    width: 30%;
    right: auto;
    background: linear-gradient(90deg, transparent, var(--dv-deco-b), transparent);
    animation: dv-flow 2.4s linear infinite;
  }
  @keyframes dv-flow {
    0% {
      transform: translateX(-100%);
    }
    100% {
      transform: translateX(340%);
    }
  }
  /* bars：律动柱 */
  .dv-bars {
    display: flex;
    align-items: center;
    padding: 0 4px;
  }
  .dv-bar {
    flex: 1;
    height: 40%;
    margin: 0 1px;
    border-radius: 1px;
    background: linear-gradient(180deg, var(--dv-deco-b), var(--dv-deco-a));
    animation: dv-beat 1.4s ease-in-out infinite;
  }
  @keyframes dv-beat {
    0%,
    100% {
      transform: scaleY(0.35);
      opacity: 0.5;
    }
    50% {
      transform: scaleY(1);
      opacity: 1;
    }
  }
  /* corner：四角括号 */
  .dv-cn {
    position: absolute;
    width: 14px;
    height: 14px;
    border: 2px solid var(--dv-deco-a);
  }
  .dv-cn.tl {
    left: 0;
    top: 0;
    border-right: 0;
    border-bottom: 0;
  }
  .dv-cn.tr {
    right: 0;
    top: 0;
    border-left: 0;
    border-bottom: 0;
  }
  .dv-cn.bl {
    left: 0;
    bottom: 0;
    border-right: 0;
    border-top: 0;
  }
  .dv-cn.br {
    right: 0;
    bottom: 0;
    border-left: 0;
    border-top: 0;
  }
  /* divider：中心菱形分隔 */
  .dv-divider {
    display: flex;
    align-items: center;
    padding: 0 6px;
  }
  .dv-div-line {
    flex: 1;
    height: 1px;
    background: linear-gradient(90deg, transparent, var(--dv-deco-a));
  }
  .dv-div-line.is-reverse {
    background: linear-gradient(90deg, var(--dv-deco-a), transparent);
  }
  .dv-diamond {
    width: 8px;
    height: 8px;
    margin: 0 8px;
    border: 1px solid var(--dv-deco-b);
    transform: rotate(45deg);
    background: var(--dv-deco-fill);
  }
</style>
