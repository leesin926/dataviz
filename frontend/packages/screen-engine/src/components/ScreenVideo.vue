<template>
  <div class="screen-video">
    <video
      v-if="src"
      ref="videoRef"
      :src="src"
      :autoplay="autoplay"
      :muted="muted"
      :loop="loop"
      :controls="controls"
      :style="videoStyle"
    />
    <div v-else class="video-placeholder">未配置视频源</div>
  </div>
</template>

<script setup lang="ts">
  import { computed, ref } from 'vue'
  import type { ScreenComponent } from '@dataviz/shared-types'

  const props = defineProps<{
    component: ScreenComponent
  }>()

  const videoRef = ref<HTMLVideoElement | null>(null)

  const src = computed(() => (props.component.props?.src as string) || '')
  const autoplay = computed(() => (props.component.props?.autoplay as boolean) ?? true)
  const muted = computed(() => (props.component.props?.muted as boolean) ?? true)
  const loop = computed(() => (props.component.props?.loop as boolean) ?? true)
  const controls = computed(() => (props.component.props?.controls as boolean) ?? false)

  const videoStyle = computed(() => ({
    width: '100%',
    height: '100%',
    objectFit: 'cover' as const,
  }))
</script>

<style scoped>
  .screen-video {
    width: 100%;
    height: 100%;
    overflow: hidden;
  }
  .video-placeholder {
    width: 100%;
    height: 100%;
    display: flex;
    align-items: center;
    justify-content: center;
    color: #cfd8e3;
    background: #0a1929;
    border: 1px dashed #334455;
  }
</style>
