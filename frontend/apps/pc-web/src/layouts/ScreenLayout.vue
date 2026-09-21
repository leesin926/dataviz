<template>
  <div class="screen-layout">
    <router-view />
  </div>
</template>

<script setup lang="ts">
  // 全屏布局 (大屏预览等)
</script>

<style lang="scss" scoped>
  .screen-layout {
    width: 100vw;
    height: 100vh;
    overflow: hidden;
    background: #0a1929;
  }
</style>
