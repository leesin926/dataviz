<template>
  <div class="blank-layout">
    <router-view />
  </div>
</template>

<script setup lang="ts">
  // 空白布局 (登录页等)
</script>

<style lang="scss" scoped>
  .blank-layout {
    width: 100%;
    height: 100vh;
    overflow: auto;
  }
</style>
