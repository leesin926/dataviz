<template>
  <view class="uni-screen-view">
    <view v-if="loading && !screen" class="uni-screen-view__tip">加载中…</view>
    <view v-else-if="error" class="uni-screen-view__tip uni-screen-view__tip--err">
      <text class="uni-screen-view__err-title">大屏加载失败</text>
      <text class="uni-screen-view__err-msg">{{ error }}</text>
      <text class="uni-screen-view__retry" @click="load">点击重试</text>
    </view>
    <UniScreenEngine
      v-else-if="screen"
      :screen="screen"
      :platform="platform"
      :base-url="baseUrl"
      :access-token="accessToken"
    />
  </view>
</template>

<script setup lang="ts">
  /** 三端共用的大屏展示页（5.2）：按端取展平配置，交给原生渲染器 */
  import { onMounted, onUnmounted, ref } from 'vue'
  import type { Screen, ScreenPlatform } from '@dataviz/shared-types'
  import { setNavigationBarTitle, stopPullDownRefresh, uniRequest } from '../core/uniRuntime'
  import { refreshGlobalMourning, watchGlobalMourningPush } from '../core/mourning'
  import UniScreenEngine from '../UniScreenEngine.vue'

  interface R<T> {
    code?: number
    message?: string
    data?: T
  }

  const props = withDefaults(
    defineProps<{
      screenId: string | number
      platform: ScreenPlatform
      baseUrl?: string
      accessToken?: string
    }>(),
    { baseUrl: '/api', accessToken: '' },
  )

  const screen = ref<Screen | null>(null)
  const loading = ref(false)
  const error = ref('')

  async function load(): Promise<void> {
    loading.value = true
    error.value = ''
    try {
      const body = (await uniRequest({
        url: `${props.baseUrl}/screen/${props.screenId}?platform=${props.platform}`,
        header: props.accessToken ? { Authorization: `Bearer ${props.accessToken}` } : undefined,
      })) as R<Screen>
      if (body.code !== 0 && body.code !== 200) throw new Error(body.message || '接口返回失败')
      if (!body.data) throw new Error('大屏配置为空')
      screen.value = body.data
      setNavigationBarTitle(body.data.name || '大屏')
      await refreshGlobalMourning(props.baseUrl)
    } catch (e) {
      error.value = (e as Error).message || String(e)
    } finally {
      loading.value = false
      stopPullDownRefresh()
    }
  }

  defineExpose({ load })

  /** 免登推送通道：卸载时必须断开，否则页面销毁后重连计时器会一直挂着 */
  let unsubscribeMourningPush: (() => void) | null = null
  onMounted(() => {
    load()
    unsubscribeMourningPush = watchGlobalMourningPush(props.baseUrl)
  })
  onUnmounted(() => {
    if (unsubscribeMourningPush) unsubscribeMourningPush()
    unsubscribeMourningPush = null
  })
</script>

<style scoped>
  .uni-screen-view {
    min-height: 100vh;
    background-color: #ffffff;
  }
  .uni-screen-view__tip {
    padding: 60px 16px;
    text-align: center;
    font-size: 13px;
    color: #909399;
  }
  .uni-screen-view__tip--err {
    color: #f56c6c;
  }
  .uni-screen-view__err-title {
    display: block;
    font-size: 14px;
    margin-bottom: 6px;
  }
  .uni-screen-view__err-msg {
    display: block;
    font-size: 12px;
    opacity: 0.85;
  }
  .uni-screen-view__retry {
    display: block;
    margin-top: 12px;
    font-size: 13px;
    color: #409eff;
  }
</style>
