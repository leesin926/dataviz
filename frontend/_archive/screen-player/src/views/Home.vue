<template>
  <div class="home">
    <h1>大屏播放器</h1>
    <p>欢迎使用 DataViz 大屏播放器</p>
  </div>
</template>

<script setup lang="ts">
</script>

<style scoped lang="scss">
.home {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;

  h1 {
    font-size: 48px;
    margin-bottom: 20px;
  }

  p {
    font-size: 20px;
    opacity: 0.9;
  }
}
</style>
