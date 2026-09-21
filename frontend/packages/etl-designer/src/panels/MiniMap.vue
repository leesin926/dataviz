<template>
  <div class="minimap" ref="mapRef"></div>
</template>

<script setup lang="ts">
  import { ref } from 'vue'

  const mapRef = ref<HTMLDivElement | null>(null)

  // 小地图占位 - 实际应接入 X6 MiniMap 插件
</script>

<style scoped>
  .minimap {
    width: 100%;
    height: 100%;
    background: #fafafa;
  }
</style>
