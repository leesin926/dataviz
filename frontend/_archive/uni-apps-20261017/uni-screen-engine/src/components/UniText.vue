<template>
  <view class="uni-text" :style="textStyle">
    <text :style="textStyle">{{ content }}</text>
  </view>
</template>

<script setup lang="ts">
  /** 文本（4.2）：props 键与 PC 端 ScreenText 对齐（text/color/fontSize/textAlign） */
  import { computed } from 'vue'
  import { CANVAS_INK } from '@dataviz/shared-types'
  import { scaledFontSize } from '../core/scale'
  import { numOf, strOf, type UniRendererProps } from './props'

  const props = defineProps<UniRendererProps>()

  const content = computed(() => strOf(props.component.props?.text, props.component.name || '文本'))

  const textStyle = computed(() => {
    const p = props.component.props || {}
    return {
      color: strOf(p.color, CANVAS_INK.normal),
      fontSize: `${scaledFontSize(numOf(p.fontSize, 16), props.scale ?? 1)}px`,
      textAlign: strOf(p.textAlign, 'left'),
      lineHeight: '1.5',
    }
  })
</script>

<style scoped>
  .uni-text {
    width: 100%;
    height: 100%;
    padding: 8px;
    box-sizing: border-box;
    overflow: hidden;
    display: flex;
    align-items: center;
  }
</style>
