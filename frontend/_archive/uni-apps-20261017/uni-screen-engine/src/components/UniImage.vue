<template>
  <view class="uni-image">
    <image v-if="src && !error" :src="src" :mode="fitMode" class="uni-image__img" @error="onError" />
    <text v-else class="uni-image__tip">{{ error || '未配置图片地址' }}</text>
  </view>
</template>

<script setup lang="ts">
  /** 图片（4.2）：objectFit → uni 的 image mode 映射，加载失败显式提示 */
  import { computed, ref } from 'vue'
  import { strOf, type UniRendererProps } from './props'

  const props = defineProps<UniRendererProps>()

  const error = ref('')
  const p = computed(() => props.component.props || {})
  const src = computed(() => strOf(p.value.src, ''))
  const fitMode = computed(() => {
    const fit = strOf(p.value.objectFit, 'cover')
    if (fit === 'contain') return 'aspectFit'
    if (fit === 'fill') return 'scaleToFill'
    return 'aspectFill'
  })

  function onError(): void {
    // uni 的 @error 事件不带可读信息，只记来源，避免把签名链接整条甩在屏上
    error.value = `图片加载失败：${src.value.split('?')[0]}`
  }
</script>

<style scoped>
  .uni-image {
    width: 100%;
    height: 100%;
    overflow: hidden;
  }
  .uni-image__img {
    width: 100%;
    height: 100%;
  }
  .uni-image__tip {
    display: block;
    padding: 6px;
    font-size: 12px;
    color: #909399;
  }
</style>
