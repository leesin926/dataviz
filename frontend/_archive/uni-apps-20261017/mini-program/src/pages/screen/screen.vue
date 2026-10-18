<template>
  <view class="screen-page" :class="layoutClass">
    <UniScreenShow
      v-if="screenId"
      ref="view"
      :screen-id="screenId"
      :platform="SCREEN_PLATFORM"
      :base-url="BASE_URL"
      :access-token="token"
    />
  </view>
</template>

<script setup lang="ts">
  /** 大屏展示页（5.2）：只负责取路由参数与登录态，渲染与取数在 @dataviz/uni-screen-engine */
  import { ref } from 'vue'
  import { onLoad, onShow, onPullDownRefresh } from '@dcloudio/uni-app'
  import UniScreenShow from '@dataviz/uni-screen-engine/src/screens/UniScreenShow.vue'
  import { isLogged, getToken } from '../../utils/auth'
  import { BASE_URL } from '../../utils/request'
  import { layoutClass, SCREEN_PLATFORM } from '../../utils/device'

  const screenId = ref('')
  const token = ref('')
  const view = ref<{ load: () => Promise<void> } | null>(null)

  onLoad((query) => {
    if (!isLogged()) {
      uni.reLaunch({ url: '/pages/login/login' })
      return
    }
    token.value = getToken()
    screenId.value = String(query?.id || '')
    if (!screenId.value) uni.showToast({ title: '缺少大屏 id', icon: 'none' })
  })

  onShow(() => {
    if (isLogged()) token.value = getToken()
  })

  onPullDownRefresh(() => {
    view.value?.load().then(() => uni.stopPullDownRefresh())
  })
</script>

<style lang="scss" scoped>
  .screen-page {
    min-height: 100vh;
    background: #ffffff;
  }
</style>
