<template>
  <div class="screen-preview" @dblclick="exitFullscreen">
    <ScreenEngine v-if="screen" :screen="screen" @ready="onReady" />
    <!-- 预览会自动进全屏，退出后必须留一条回设计器的路（见决策 D23） -->
    <div class="preview-bar">
      <el-button size="small" :icon="Back" @click="backToEditor">{{ t('screen.backToEditor') }}</el-button>
      <el-button size="small" :icon="FullScreen" @click="toggleFullscreen">
        {{ isFullscreen ? t('screen.exitFullscreen') : t('screen.enterFullscreen') }}
      </el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
  import { onBeforeUnmount, onMounted, ref } from 'vue'
  import { useRoute, useRouter } from 'vue-router'
  import type { Screen, ScreenPlatform } from '@dataviz/shared-types'
  import { ScreenEngine } from '@dataviz/screen-engine'
  import { getScreen, getScreenForPlatform } from '@dataviz/api-client'
  import { ElMessage } from 'element-plus'
  import { Back, FullScreen } from '@element-plus/icons-vue'
  import { useI18n } from 'vue-i18n'
  import { getPreviewDraft, resolvePreviewScreen } from './editor/previewHandoff'

  const route = useRoute()
  const router = useRouter()
  const { t } = useI18n()
  const id = route.params.id as string
  const platform = (route.query.platform as ScreenPlatform | undefined) || 'pc'
  const fromDraft = route.query.draft === '1'
  const screen = ref<Screen | null>(null)
  const isFullscreen = ref(false)

  async function loadScreen() {
    // 编辑器「预览」按钮：直接吃内存草稿，未保存的大屏同样能看到全部组件
    if (fromDraft) {
      const draft = getPreviewDraft()
      if (draft) {
        screen.value = resolvePreviewScreen(draft, platform)
        return
      }
      ElMessage.warning(t('screen.draftStale'))
      if (id === 'draft') return
    }
    try {
      screen.value = platform !== 'pc' ? await getScreenForPlatform(id, platform) : await getScreen(id)
    } catch (e) {
      ElMessage.error((e as Error).message)
    }
  }

  function onReady() {
    if (document.documentElement.requestFullscreen) {
      document.documentElement.requestFullscreen().catch(() => {
        /* 浏览器可能拒绝自动全屏，用户仍可用工具条手动进入 */
      })
    }
  }

  function toggleFullscreen() {
    if (document.fullscreenElement) exitFullscreen()
    else document.documentElement.requestFullscreen?.().catch(() => undefined)
  }

  function exitFullscreen() {
    if (document.fullscreenElement) {
      document.exitFullscreen().catch(() => undefined)
    }
  }

  /** 返回设计器：草稿预览带上 restore，设计器用同一份快照继续编辑，不丢未保存改动 */
  function backToEditor() {
    exitFullscreen()
    const query = fromDraft ? { restore: '1', platform } : { platform }
    router.push({ path: id === 'draft' ? '/screen/editor' : `/screen/editor/${id}`, query })
  }

  function onFullscreenChange() {
    isFullscreen.value = !!document.fullscreenElement
  }

  onMounted(() => {
    document.addEventListener('fullscreenchange', onFullscreenChange)
    loadScreen()
  })

  onBeforeUnmount(() => {
    document.removeEventListener('fullscreenchange', onFullscreenChange)
  })
</script>

<style lang="scss" scoped>
  .screen-preview {
    position: relative;
    width: 100vw;
    height: 100vh;
    overflow: hidden;
    background: #000;
  }
  /* 全屏时页面即整屏内容，固定定位的工具条依然可见，Esc 退出后也在 */
  .preview-bar {
    position: fixed;
    top: 10px;
    right: 12px;
    z-index: 10;
    display: flex;
    gap: 8px;
    opacity: 0.35;
    transition: opacity 0.2s;
    &:hover {
      opacity: 1;
    }
  }
</style>
