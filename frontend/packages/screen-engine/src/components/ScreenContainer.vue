<template>
  <div ref="screenRef" class="screen-container" :style="containerStyle">
    <div class="screen-content" :style="contentStyle">
      <slot />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { CANVAS_BG_COLOR } from '@dataviz/shared-types'
import { useScreenScale } from '../composables/useScreenScale'

const props = withDefaults(
  defineProps<{
    width?: number
    height?: number
    mode?: 'scale' | 'fixed-width' | 'responsive'
    backgroundColor?: string
    backgroundImage?: string
  }>(),
  {
    width: 1920,
    height: 1080,
    mode: 'scale',
    backgroundColor: CANVAS_BG_COLOR,
  }
)

const { containerRef: screenRef, containerStyle: scaleStyle } = useScreenScale({
  designWidth: props.width,
  designHeight: props.height,
  mode: props.mode,
})

const containerStyle = computed(() => ({
  ...scaleStyle.value,
  backgroundColor: props.backgroundColor,
  backgroundImage: props.backgroundImage ? `url(${props.backgroundImage})` : undefined,
  backgroundSize: 'cover',
  backgroundPosition: 'center',
}))

const contentStyle = computed(() => ({
  width: `${props.width}px`,
  height: `${props.height}px`,
}))
</script>

<style scoped>
.screen-container {
  position: relative;
  width: 100%;
  height: 100vh;
  overflow: hidden;
}

.screen-content {
  position: relative;
  transform-origin: left top;
}
</style>
