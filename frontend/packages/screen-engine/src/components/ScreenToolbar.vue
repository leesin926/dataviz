<template>
  <div class="screen-toolbar">
    <div class="toolbar-left">
      <slot name="left" />
    </div>

    <div class="toolbar-center">
      <div class="toolbar-group">
        <button
          class="toolbar-btn"
          :class="{ 'is-active': previewMode }"
          title="预览"
          @click="emit('togglePreview')"
        >
          <span class="btn-icon">&#9655;</span>
        </button>
        <button
          class="toolbar-btn"
          :class="{ 'is-active': isFullscreen }"
          title="全屏"
          @click="emit('toggleFullscreen')"
        >
          <span class="btn-icon">&#x26F6;</span>
        </button>
      </div>

      <div class="toolbar-group">
        <button
          class="toolbar-btn"
          title="撤销"
          :disabled="!canUndo"
          @click="emit('undo')"
        >
          <span class="btn-icon">&#x21A9;</span>
        </button>
        <button
          class="toolbar-btn"
          title="重做"
          :disabled="!canRedo"
          @click="emit('redo')"
        >
          <span class="btn-icon">&#x21AA;</span>
        </button>
      </div>

      <div class="toolbar-group">
        <button class="toolbar-btn" title="保存" @click="emit('save')">
          <span class="btn-icon">&#128190;</span>
        </button>
        <button class="toolbar-btn" title="发布" @click="emit('publish')">
          <span class="btn-icon">&#128640;</span>
        </button>
      </div>
    </div>

    <div class="toolbar-right">
      <slot name="right" />
    </div>
  </div>
</template>

<script setup lang="ts">
withDefaults(
  defineProps<{
    previewMode?: boolean
    isFullscreen?: boolean
    canUndo?: boolean
    canRedo?: boolean
  }>(),
  {
    previewMode: false,
    isFullscreen: false,
    canUndo: false,
    canRedo: false,
  }
)

const emit = defineEmits<{
  (e: 'togglePreview'): void
  (e: 'toggleFullscreen'): void
  (e: 'undo'): void
  (e: 'redo'): void
  (e: 'save'): void
  (e: 'publish'): void
}>()
</script>

<style scoped>
.screen-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 48px;
  padding: 0 16px;
  background: #1e1e1e;
  border-bottom: 1px solid #333;
  color: #e0e0e0;
  z-index: 100;
}

.toolbar-left,
.toolbar-right {
  flex: 1;
  display: flex;
  align-items: center;
}

.toolbar-right {
  justify-content: flex-end;
}

.toolbar-center {
  display: flex;
  align-items: center;
  gap: 12px;
}

.toolbar-group {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 0 8px;
  border-right: 1px solid #444;
}

.toolbar-group:last-child {
  border-right: none;
}

.toolbar-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border: none;
  border-radius: 4px;
  background: transparent;
  color: #ccc;
  cursor: pointer;
  transition: all 0.2s;
}

.toolbar-btn:hover {
  background: #333;
  color: #fff;
}

.toolbar-btn.is-active {
  background: #409eff;
  color: #fff;
}

.toolbar-btn:disabled {
  opacity: 0.3;
  cursor: not-allowed;
}

.btn-icon {
  font-size: 14px;
}
</style>
