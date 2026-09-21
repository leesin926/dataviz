<template>
  <div class="screen-unknown">
    <span class="su-type">{{ component.type }}</span>
    <span class="su-name">{{ component.name }}</span>
  </div>
</template>

<script setup lang="ts">
  import type { ScreenComponent } from '@dataviz/shared-types'

  defineProps<{
    component: ScreenComponent
  }>()
</script>

<style scoped>
  .screen-unknown {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 2px;
    width: 100%;
    height: 100%;
    box-sizing: border-box;
    color: #5b6a80;
    font-size: 12px;
    border: 1px dashed rgba(22, 32, 47, 0.18);
    background: rgba(22, 32, 47, 0.04);
  }
  .su-type {
    font-family: Consolas, monospace;
    color: #2f7cff;
  }
  .su-name {
    opacity: 0.75;
  }
</style>
