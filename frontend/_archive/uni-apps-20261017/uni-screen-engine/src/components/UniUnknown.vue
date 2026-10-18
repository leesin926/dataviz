<template>
  <view class="uni-unknown" :style="{ color: '#909399', fontSize: size + 'px' }">
    <text class="uni-unknown__t" :style="{ fontSize: size + 'px' }">uni 端不支持</text>
    <text class="uni-unknown__s" :style="{ fontSize: Math.max(9, size - 2) + 'px' }">{{ label }}</text>
  </view>
</template>

<script setup lang="ts">
  /** 不可原生渲染组件的占位（4.5）：默认由引擎过滤掉，只有显式开启占位开关才会走到这里 */
  import { computed } from 'vue'
  import { scaledFontSize } from '../core/scale'
  import { numOf, type UniRendererProps } from './props'

  const props = defineProps<UniRendererProps>()

  const size = computed(() => scaledFontSize(numOf(props.component.props?.fontSize, 12), (props.scale ?? 1), 9))
  const label = computed(() => `${props.component.type} · ${props.component.name || props.component.id}`)
</script>

<style scoped>
  .uni-unknown {
    width: 100%;
    height: 100%;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    border: 1px dashed #dcdfe6;
    background-color: rgba(0, 0, 0, 0.02);
  }
  .uni-unknown__t {
    display: block;
  }
  .uni-unknown__s {
    display: block;
    margin-top: 2px;
    opacity: 0.7;
  }
</style>
