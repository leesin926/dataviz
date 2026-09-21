<template>
  <view class="alert-page" :class="layoutClass">
    <view class="page-inner">
      <view class="filter-row">
        <text
          v-for="f in statusFilters"
          :key="f.value"
          :class="['filter-tab', statusFilter === f.value ? 'filter-tab-active' : '']"
          @click="switchFilter(f.value)"
        >{{ f.label }}</text>
      </view>

      <view v-if="loading && !records.length" class="empty">加载中…</view>
      <view v-else-if="!records.length" class="empty">
        <text class="empty-title">暂无告警</text>
        <text class="empty-sub">当前筛选条件下没有告警事件</text>
      </view>

      <view v-else class="event-list">
        <view v-for="item in records" :key="item.id" class="event-item" @click="goDetail(item)">
          <view class="event-top">
            <text class="severity-tag" :style="{ backgroundColor: severityColor(item.severity) }">
              {{ severityLabel(item.severity) }}
            </text>
            <text class="event-rule">{{ item.ruleName }}</text>
            <text class="event-status" :style="{ color: statusColor(item.status) }">
              {{ statusLabel(item.status) }}
            </text>
          </view>
          <view class="event-msg">{{ item.message || '（无告警详情）' }}</view>
          <view class="event-time">{{ formatTime(item.createTime) }}</view>
        </view>
      </view>

      <view v-if="records.length" class="load-tip">
        {{ finished ? '没有更多了' : loadingMore ? '加载中…' : '上拉加载更多' }}
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onShow, onPullDownRefresh, onReachBottom } from '@dcloudio/uni-app'
import { isLogged } from '../../utils/auth'
import { listEvents, type AlertEvent } from '../../utils/alert'
import {
  layoutClass,
  severityLabel,
  severityColor,
  statusLabel,
  statusColor,
  formatTime,
} from '../../utils/device'

const statusFilters = [
  { label: '全部', value: '' },
  { label: '待处理', value: 'PENDING' },
  { label: '已确认', value: 'ACKNOWLEDGED' },
  { label: '已解决', value: 'RESOLVED' },
]

const PAGE_SIZE = 20

const statusFilter = ref('')
const records = ref<AlertEvent[]>([])
const total = ref(0)
const pageNum = ref(1)
const loading = ref(false)
const loadingMore = ref(false)
const finished = ref(false)

onShow(() => {
  if (!isLogged()) {
    uni.reLaunch({ url: '/pages/login/login' })
    return
  }
  load(true)
})

async function load(reset: boolean) {
  if (loading.value || loadingMore.value) return
  if (reset) {
    pageNum.value = 1
    finished.value = false
    loading.value = true
  } else {
    if (finished.value) return
    loadingMore.value = true
  }
  try {
    const res = await listEvents({
      pageNum: pageNum.value,
      pageSize: PAGE_SIZE,
      status: statusFilter.value || undefined,
    })
    const list = res.records || []
    records.value = reset ? list : records.value.concat(list)
    total.value = res.total || 0
    finished.value = records.value.length >= total.value
    pageNum.value += 1
  } catch (e) {
    uni.showToast({ title: (e as Error).message || '加载失败', icon: 'none' })
  } finally {
    loading.value = false
    loadingMore.value = false
    uni.stopPullDownRefresh()
  }
}

function switchFilter(value: string) {
  if (statusFilter.value === value) return
  statusFilter.value = value
  records.value = []
  load(true)
}

function goDetail(item: AlertEvent) {
  uni.navigateTo({ url: '/pages/alert/detail?data=' + encodeURIComponent(JSON.stringify(item)) })
}

onPullDownRefresh(() => {
  load(true)
})

onReachBottom(() => {
  load(false)
})
</script>

<style lang="scss" scoped>
.alert-page {
  min-height: 100vh;
  background: #f5f6fa;
}
.page-inner {
  padding: 24rpx;
  box-sizing: border-box;
}

/* ---------- 手机布局：rpx 随屏宽等比缩放 ---------- */
.layout-phone .page-inner {
  max-width: 100%;
}
/* 横屏手机：内容限宽，避免拉伸变形 */
@media screen and (orientation: landscape) and (max-height: 520px) {
  .layout-phone .page-inner {
    max-width: 75%;
    margin: 0 auto;
  }
}
/* 小程序里在宽设备（如平板跑微信小程序）上兜底限宽 */
@media screen and (min-width: 768px) {
  .layout-phone .page-inner {
    max-width: 600px;
    margin: 0 auto;
  }
}

/* ---------- 平板布局：px 定尺寸 + 居中限宽 + 双栏 ---------- */
.layout-tablet .page-inner {
  max-width: 960px;
  margin: 0 auto;
  padding: 20px;
}
.layout-tablet .event-list {
  display: flex;
  flex-wrap: wrap;
}
.layout-tablet .event-item {
  width: calc(50% - 8px);
  margin-bottom: 16px;
}
.layout-tablet .event-item:nth-child(even) {
  margin-left: 16px;
}
.layout-tablet .filter-tab {
  font-size: 14px;
  padding: 8px 18px;
}
.layout-tablet .event-rule {
  font-size: 16px;
}
.layout-tablet .event-msg {
  font-size: 13px;
}
.layout-tablet .event-time {
  font-size: 12px;
}
.layout-tablet .severity-tag {
  font-size: 12px;
  padding: 2px 8px;
}
.layout-tablet .event-status {
  font-size: 13px;
}

/* ---------- 通用 ---------- */
.filter-row {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
  margin-bottom: 24rpx;
}
.filter-tab {
  padding: 10rpx 28rpx;
  font-size: 26rpx;
  color: #606266;
  background: #fff;
  border-radius: 32rpx;
  border: 2rpx solid #e4e7ed;
}
.filter-tab-active {
  color: #fff;
  background: #667eea;
  border-color: #667eea;
}
.empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 160rpx 0;
}
.empty-title {
  font-size: 32rpx;
  color: #909399;
}
.empty-sub {
  margin-top: 12rpx;
  font-size: 24rpx;
  color: #c0c4cc;
}
.event-item {
  background: #fff;
  border-radius: 16rpx;
  padding: 24rpx;
  margin-bottom: 20rpx;
  box-shadow: 0 4rpx 12rpx rgba(0, 0, 0, 0.04);
}
.event-top {
  display: flex;
  align-items: center;
  gap: 12rpx;
}
.severity-tag {
  color: #fff;
  font-size: 22rpx;
  padding: 4rpx 14rpx;
  border-radius: 8rpx;
  flex-shrink: 0;
}
.event-rule {
  flex: 1;
  font-size: 30rpx;
  font-weight: bold;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.event-status {
  font-size: 24rpx;
  flex-shrink: 0;
}
.event-msg {
  margin-top: 14rpx;
  font-size: 26rpx;
  color: #606266;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.event-time {
  margin-top: 12rpx;
  font-size: 22rpx;
  color: #c0c4cc;
}
.load-tip {
  text-align: center;
  padding: 24rpx 0 40rpx;
  font-size: 24rpx;
  color: #909399;
}
</style>
