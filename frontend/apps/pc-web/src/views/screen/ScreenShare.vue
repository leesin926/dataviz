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
  import { onMounted, onBeforeUnmount, ref } from 'vue'
  import { useRoute } from 'vue-router'
  import { useI18n } from 'vue-i18n'
  import { Loading } from '@element-plus/icons-vue'
  import type { Screen, ScreenPlatform } from '@dataviz/shared-types'
  import { ScreenEngine } from '@dataviz/screen-engine'
  import { getSharedScreen, subscribeScreenChanges } from '@dataviz/api-client'

  const route = useRoute()
  const { t } = useI18n()

  const code = route.params.code as string
  const platform = route.query.platform as ScreenPlatform | undefined
  const screen = ref<Screen | null>(null)
  const loading = ref(true)
  const errorMsg = ref(t('screen.shareNotFound'))
  let unsubscribe: (() => void) | null = null

  async function load() {
    loading.value = true
    try {
      screen.value = await getSharedScreen(code, platform)
      if (screen.value?.name) document.title = screen.value.name
      // 首帧拿到令牌才谈得上订阅：令牌就是这条通道的凭证，没有它连不上也不需要连
      bindPush(screen.value?.shareToken)
    } catch (e) {
      // 变更推送回来后走到这里 = 这张屏已被删除或下架，访客看到的是"链接不可用"而不是停在旧画面
      errorMsg.value = (e as Error).message || t('screen.shareNotFound')
      screen.value = null
    } finally {
      loading.value = false
    }
  }

  function bindPush(shareToken?: string) {
    if (!shareToken || unsubscribe) return
    unsubscribe = subscribeScreenChanges(shareToken, {
      onChanged: () => load(),
      onReconnect: () => load(),
    })
  }

  onMounted(load)
  onBeforeUnmount(() => {
    if (unsubscribe) {
      unsubscribe()
      unsubscribe = null
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
