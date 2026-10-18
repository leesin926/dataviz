<template>
  <view class="screen-list" :class="layoutClass">
    <UniScreenList ref="list" :platform="SCREEN_PLATFORM" :base-url="BASE_URL" :access-token="token" />
  </view>
</template>

<script setup lang="ts">
  /** 大屏列表页（5.2）：登录后展示已发布大屏，点击进入原生展示页；页面本体在 @dataviz/uni-screen-engine */
  import { ref } from 'vue'
  import { onShow, onPullDownRefresh } from '@dcloudio/uni-app'
  import UniScreenList from '@dataviz/uni-screen-engine/src/screens/UniScreenList.vue'
  import { isLogged, getToken } from '../../utils/auth'
  import { BASE_URL } from '../../utils/request'
  import { layoutClass, SCREEN_PLATFORM } from '../../utils/device'

  const token = ref('')
  const list = ref<{ reload: () => Promise<void> } | null>(null)

  onShow(() => {
    if (!isLogged()) {
      uni.reLaunch({ url: '/pages/login/login' })
      return
    }
    // 首帧 onShow 早于子组件挂载，list 还是 null；真正的首次加载由共用页组件 onMounted 发起
    token.value = getToken()
    if (list.value) void list.value.reload()
  })

  onPullDownRefresh(() => {
    list.value?.reload().then(() => uni.stopPullDownRefresh())
  })
</script>

<style lang="scss" scoped>
  .screen-list {
    min-height: 100vh;
    background: #f5f6fa;
  }
  /* 平板布局下列表限宽居中，卡片放大 */
  .layout-tablet :deep(.uni-screen-list) {
    max-width: 960px;
    margin: 0 auto;
    padding: 20px;
  }
  .layout-tablet :deep(.uni-screen-list__card) {
    width: 32%;
  }
  .layout-tablet :deep(.uni-screen-list__cover) {
    height: 130px;
  }
</style>
