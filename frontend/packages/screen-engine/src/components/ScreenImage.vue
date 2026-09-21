<template>
  <div class="screen-image">
    <img :src="src" :alt="alt" :style="imgStyle" @error="handleError" />
  </div>
</template>

<script setup lang="ts">
  import { computed, ref } from 'vue'
  import type { CSSProperties } from 'vue'
  import type { ScreenComponent } from '@dataviz/shared-types'

  const props = defineProps<{
    component: ScreenComponent
  }>()

  const errored = ref(false)

  const src = computed(() => {
    if (errored.value) return ''
    return (props.component.props?.src as string) || ''
  })

  const alt = computed(() => (props.component.props?.alt as string) || '图片')

  const imgStyle = computed<CSSProperties>(() => {
    const p = props.component.props || {}
    return {
      width: '100%',
      height: '100%',
      objectFit: ((p.objectFit as string) || 'contain') as CSSProperties['objectFit'],
    }
  })

  function handleError() {
    errored.value = true
  }
</script>

<style scoped>
  .screen-image {
    width: 100%;
    height: 100%;
    overflow: hidden;
  }
</style>
