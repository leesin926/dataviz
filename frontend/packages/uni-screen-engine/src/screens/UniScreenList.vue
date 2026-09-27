<template>
  <view class="uni-screen-list">
    <view v-if="loading && !records.length" class="uni-screen-list__tip">加载中…</view>
    <view v-else-if="error" class="uni-screen-list__tip uni-screen-list__tip--err" @click="reload">
      <text class="uni-screen-list__err-title">列表加载失败</text>
      <text class="uni-screen-list__err-msg">{{ error }}（点击重试）</text>
    </view>
    <view v-else-if="!records.length" class="uni-screen-list__tip">
      <text class="uni-screen-list__err-title">暂无已发布大屏</text>
      <text class="uni-screen-list__err-msg">请先在设计端发布大屏后再到本端查看</text>
    </view>

    <view v-else class="uni-screen-list__grid">
      <view
        v-for="item in records"
        :key="item.id"
        class="uni-screen-list__card"
        @click="open(item)"
      >
        <image v-if="item.cover" :src="item.cover" mode="aspectFill" class="uni-screen-list__cover" />
        <view v-else class="uni-screen-list__cover uni-screen-list__cover--empty">
          <text class="uni-screen-list__cover-text">{{ (item.name || '').slice(0, 2) }}</text>
        </view>
        <view class="uni-screen-list__body">
          <text class="uni-screen-list__name">{{ item.name }}</text>
          <text class="uni-screen-list__meta">{{ item.width }}×{{ item.height }} · {{ item.updatedAt || item.status }}</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
  /**
   * 三端共用的大屏列表页（5.2 / 5.3）。
   *
   * 传 `platform` 只为拿该端的画布尺寸：卡片上的 width×height 若一律是 pc 的 1920×1080，
   * 手机端就显示了一个它根本没用过的尺寸。
   * **仍然不按端过滤**：该端没有独立变体时回退 pc 配置是既定语义（D53），
   * 即每一屏在本端都可渲染，列表的过滤条件只有"已发布"。
   */
  import { onMounted, ref } from 'vue'
  import type { Screen, ScreenPlatform } from '@dataviz/shared-types'
  import { navigateTo, uniRequest } from '../core/uniRuntime'

  interface ListBody {
    code?: number
    message?: string
    data?: { list?: Screen[]; total?: number }
  }

  const props = withDefaults(
    defineProps<{
      platform: ScreenPlatform
      baseUrl?: string
      accessToken?: string
    }>(),
    { baseUrl: '/api', accessToken: '' },
  )

  const records = ref<Screen[]>([])
  const loading = ref(false)
  const error = ref('')

  async function reload(): Promise<void> {
    loading.value = true
    error.value = ''
    try {
      const body = (await uniRequest({
        url: `${props.baseUrl}/screen/list?status=published&platform=${props.platform}&pageNum=1&pageSize=50`,
        header: props.accessToken ? { Authorization: `Bearer ${props.accessToken}` } : undefined,
      })) as ListBody
      if (body.code !== 0 && body.code !== 200) throw new Error(body.message || '接口返回失败')
      records.value = body.data?.list || []
    } catch (e) {
      error.value = (e as Error).message || String(e)
    } finally {
      loading.value = false
    }
  }

  function open(item: Screen): void {
    navigateTo(`/pages/screen/screen?id=${item.id}&platform=${props.platform}`)
  }

  defineExpose({ reload })
  onMounted(reload)
</script>

<style scoped>
  .uni-screen-list {
    padding: 10px;
  }
  .uni-screen-list__tip {
    padding: 40px 12px;
    text-align: center;
    font-size: 13px;
    color: #909399;
  }
  .uni-screen-list__tip--err {
    color: #f56c6c;
  }
  .uni-screen-list__err-title {
    display: block;
    font-size: 14px;
    margin-bottom: 6px;
  }
  .uni-screen-list__err-msg {
    display: block;
    font-size: 12px;
    opacity: 0.8;
  }
  .uni-screen-list__grid {
    display: flex;
    flex-direction: row;
    flex-wrap: wrap;
    justify-content: space-between;
  }
  .uni-screen-list__card {
    width: 48%;
    margin-bottom: 12px;
    background-color: #ffffff;
    border: 1px solid rgba(0, 0, 0, 0.06);
    border-radius: 6px;
    overflow: hidden;
  }
  .uni-screen-list__cover {
    width: 100%;
    height: 90px;
  }
  .uni-screen-list__cover--empty {
    display: flex;
    align-items: center;
    justify-content: center;
    background-color: rgba(64, 158, 255, 0.08);
  }
  .uni-screen-list__cover-text {
    font-size: 20px;
    color: #409eff;
  }
  .uni-screen-list__body {
    padding: 8px;
  }
  .uni-screen-list__name {
    display: block;
    font-size: 14px;
    color: #303133;
    overflow: hidden;
    white-space: nowrap;
    text-overflow: ellipsis;
  }
  .uni-screen-list__meta {
    display: block;
    margin-top: 4px;
    font-size: 11px;
    color: #909399;
  }
</style>
