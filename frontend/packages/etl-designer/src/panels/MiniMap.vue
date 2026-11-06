<template>
  <div class="minimap">
    <div ref="mapRef" class="minimap-canvas"></div>
    <p v-if="status === 'unavailable'" class="minimap-empty">小地图未接入：画布尚未初始化</p>
  </div>
</template>

<script setup lang="ts">
  import { nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
  import type { GraphManager } from '../graph/GraphManager'

  /**
   * 小地图由 X6 的 MiniMap 插件渲染：它与主画布共用同一个 Model，节点增删、拖动、缩放都会实时反映；
   * 点击小地图居中到该点、拖拽视口框平移主画布、拖右下角手柄缩放，全部由插件内部处理。
   * 接入动作交给 GraphManager —— graph 在父组件 onMounted 里才创建（晚于本组件 onMounted），
   * 本组件只负责把容器交出去并在卸载时摘掉插件。
   */
  const props = defineProps<{
    manager: GraphManager
  }>()

  const mapRef = ref<HTMLDivElement | null>(null)
  const status = ref<'pending' | 'attached' | 'unavailable'>('pending')

  onMounted(async () => {
    props.manager.attachMiniMap(mapRef.value)
    // 父组件的 onMounted 在本组件之后、同一个 flush 内执行，等一个 tick 才能确认 graph 是否真的装上
    await nextTick()
    status.value = props.manager.isMiniMapAttached() ? 'attached' : 'unavailable'
  })

  onBeforeUnmount(() => {
    props.manager.detachMiniMap()
    status.value = 'pending'
  })
</script>

<style scoped>
  .minimap {
    position: relative;
    width: 100%;
    height: 100%;
    overflow: hidden;
    background: #fff;
  }
  .minimap-canvas {
    width: 100%;
    height: 100%;
  }
  .minimap :deep(.x6-widget-minimap-viewport) {
    border-color: #5f95ff;
  }
  .minimap :deep(.x6-widget-minimap-viewport-zoom) {
    border-color: #5f95ff;
  }
  .minimap-empty {
    position: absolute;
    inset: 0;
    display: flex;
    align-items: center;
    justify-content: center;
    margin: 0;
    padding: 4px;
    font-size: 12px;
    line-height: 1.4;
    text-align: center;
    color: #999;
  }
</style>
