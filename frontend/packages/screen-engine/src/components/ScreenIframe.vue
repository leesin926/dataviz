<template>
  <div class="screen-iframe">
    <iframe
      v-if="src"
      :src="src"
      :title="title"
      class="if-frame"
      frameborder="0"
      :sandbox="sandbox"
      allow="fullscreen"
      referrerpolicy="no-referrer"
    />
    <div v-else class="if-placeholder">未配置网页地址</div>
  </div>
</template>

<script setup lang="ts">
  import { computed } from 'vue'
  import type { ScreenComponent } from '@dataviz/shared-types'
  import { boolOf, strOf } from './props'

  const props = defineProps<{
    component: ScreenComponent
  }>()

  const p = computed(() => props.component.props || {})
  const src = computed(() => strOf(p.value.src))
  const title = computed(() => strOf(p.value.title, '外部网页'))
  // 默认收紧权限：嵌入第三方页面只需脚本与同源表单能力
  const sandbox = computed(() =>
    boolOf(p.value.allowScripts, true) ? 'allow-scripts allow-same-origin allow-forms' : 'allow-same-origin',
  )
</script>

<style scoped>
  .screen-iframe {
    width: 100%;
    height: 100%;
    overflow: hidden;
    background: rgba(10, 25, 41, 0.4);
  }
  .if-frame {
    width: 100%;
    height: 100%;
    border: 0;
    display: block;
  }
  .if-placeholder {
    width: 100%;
    height: 100%;
    display: flex;
    align-items: center;
    justify-content: center;
    color: #556677;
    font-size: 12px;
    border: 1px dashed #334455;
    box-sizing: border-box;
  }
</style>
