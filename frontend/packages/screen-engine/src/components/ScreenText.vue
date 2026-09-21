<template>
  <div class="screen-text" :style="textStyle">
    {{ content }}
  </div>
</template>

<script setup lang="ts">
  import { computed } from 'vue'
  import type { CSSProperties } from 'vue'
  import type { ScreenComponent } from '@dataviz/shared-types'
  import { CANVAS_INK } from '@dataviz/shared-types'
  import { pxOf, strOf } from './props'

  const props = defineProps<{
    component: ScreenComponent
  }>()

  const textStyle = computed<CSSProperties>(() => {
    const p = props.component.props || {}
    return {
      color: strOf(p.color, CANVAS_INK.normal),
      fontSize: pxOf(p.fontSize, 16),
      fontWeight: (p.fontWeight as string) || 'normal',
      textAlign: ((p.textAlign as string) || 'left') as CSSProperties['textAlign'],
      lineHeight: (p.lineHeight as string) || '1.5',
      padding: '8px',
      width: '100%',
      height: '100%',
      overflow: 'hidden',
      wordBreak: 'break-all',
    }
  })

  const content = computed(() => (props.component.props?.text as string) || '文本')
</script>

<style scoped>
  .screen-text {
    box-sizing: border-box;
  }
</style>
