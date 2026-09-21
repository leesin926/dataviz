<template>
  <div class="home">
    <h1>DataViz 桌面端</h1>
    <p>欢迎使用 DataViz 桌面应用</p>
    <div v-if="appVersion" class="version">版本: {{ appVersion }}</div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'

const appVersion = ref<string>('')

onMounted(async () => {
  if (window.electronAPI) {
    appVersion.value = await window.electronAPI.getAppVersion()
  }
})
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

  .version {
    margin-top: 30px;
    font-size: 14px;
    opacity: 0.7;
  }
}
</style>
