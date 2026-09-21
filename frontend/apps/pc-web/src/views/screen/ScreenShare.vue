<template>
  <div class="screen-share">
    <ScreenEngine v-if="screen" :screen="screen" />
    <div v-else-if="loading" class="state">
      <el-icon class="is-loading"><Loading /></el-icon>
      <span>{{ t('common.loading') }}</span>
    </div>
    <div v-else class="state">
      <p class="msg">{{ errorMsg }}</p>
    </div>
  </div>
</template>

<script setup lang="ts">
  import { ref, onMounted } from 'vue'
  import { useRoute } from 'vue-router'
  import { useI18n } from 'vue-i18n'
  import { Loading } from '@element-plus/icons-vue'
  import type { Screen, ScreenPlatform } from '@dataviz/shared-types'
  import { ScreenEngine } from '@dataviz/screen-engine'
  import { getSharedScreen } from '@dataviz/api-client'

  const route = useRoute()
  const { t } = useI18n()

  const code = route.params.code as string
  const platform = route.query.platform as ScreenPlatform | undefined
  const screen = ref<Screen | null>(null)
  const loading = ref(true)
  const errorMsg = ref(t('screen.shareNotFound'))

  onMounted(async () => {
    try {
      screen.value = await getSharedScreen(code, platform)
      if (screen.value?.name) document.title = screen.value.name
    } catch (e) {
      errorMsg.value = (e as Error).message || t('screen.shareNotFound')
    } finally {
      loading.value = false
    }
  })
</script>

<style lang="scss" scoped>
  .screen-share {
    position: relative;
    width: 100vw;
    height: 100vh;
    overflow: hidden;
    background: var(--dv-screen-bg, #000);

    .state {
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 8px;
      width: 100%;
      height: 100%;
      color: var(--dv-text-3);
      font-size: 14px;

      .msg {
        margin: 0;
      }
    }
  }
</style>
