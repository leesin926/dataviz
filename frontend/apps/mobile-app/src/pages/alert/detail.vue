<template>
  <view class="detail-page" :class="layoutClass">
    <view class="page-inner">
      <view class="detail-card">
        <view class="detail-head">
          <text class="severity-tag" :style="{ backgroundColor: severityColor(event.severity) }">
            {{ severityLabel(event.severity) }}
          </text>
          <text class="detail-status" :style="{ color: statusColor(event.status) }">
            {{ statusLabel(event.status) }}
          </text>
        </view>
        <view class="detail-rule">{{ event.ruleName }}</view>

        <view class="info-row">
          <text class="info-label">告警ID</text>
          <text class="info-value">{{ event.id }}</text>
        </view>
        <view class="info-row">
          <text class="info-label">规则ID</text>
          <text class="info-value">{{ event.ruleId }}</text>
        </view>
        <view class="info-row" v-if="event.triggerValue !== undefined && event.triggerValue !== null">
          <text class="info-label">触发值</text>
          <text class="info-value">{{ event.triggerValue }}</text>
        </view>
        <view class="info-row">
          <text class="info-label">触发时间</text>
          <text class="info-value">{{ formatTime(event.createTime) }}</text>
        </view>
        <view class="info-row" v-if="event.notifiedAt">
          <text class="info-label">通知时间</text>
          <text class="info-value">{{ formatTime(event.notifiedAt) }}</text>
        </view>
        <view class="info-row" v-if="event.resolvedAt">
          <text class="info-label">解决时间</text>
          <text class="info-value">{{ formatTime(event.resolvedAt) }}</text>
        </view>

        <view class="msg-block">
          <text class="info-label">告警内容</text>
          <text class="msg-text">{{ event.message || '（无告警详情）' }}</text>
        </view>
      </view>

      <view class="action-row" v-if="event.status !== 'RESOLVED'">
        <button
          v-if="event.status === 'PENDING'"
          class="action-btn action-ack"
          :loading="acting"
          @click="handleAck"
        >确认告警</button>
        <button class="action-btn action-resolve" :loading="acting" @click="handleResolve">标记解决</button>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { acknowledgeEvent, resolveEvent, type AlertEvent } from '../../utils/alert'
import {
  layoutClass,
  severityLabel,
  severityColor,
  statusLabel,
  statusColor,
  formatTime,
} from '../../utils/device'

const event = ref<AlertEvent>({
  id: 0,
  ruleId: 0,
  ruleName: '',
  severity: '',
  status: '',
  createTime: '',
})
const acting = ref(false)

onLoad((options) => {
  const raw = (options?.data as string) || ''
  if (raw) {
    try {
      event.value = JSON.parse(decodeURIComponent(raw)) as AlertEvent
    } catch (e) {
      console.error('解析告警数据失败', e)
    }
  }
})

async function handleAck() {
  await runAction(() => acknowledgeEvent(event.value.id), '已确认', 'ACKNOWLEDGED')
}

async function handleResolve() {
  await runAction(() => resolveEvent(event.value.id), '已标记解决', 'RESOLVED')
}

async function runAction(fn: () => Promise<void>, okText: string, newStatus: string) {
  if (acting.value) return
  acting.value = true
  try {
    await fn()
    event.value.status = newStatus
    uni.showToast({ title: okText, icon: 'success' })
  } catch (e) {
    uni.showToast({ title: (e as Error).message || '操作失败', icon: 'none' })
  } finally {
    acting.value = false
  }
}
</script>

<style lang="scss" scoped>
.detail-page {
  min-height: 100vh;
  background: #f5f6fa;
}
.page-inner {
  padding: 24rpx;
  box-sizing: border-box;
}

.layout-phone .page-inner {
  max-width: 100%;
}
@media screen and (orientation: landscape) and (max-height: 520px) {
  .layout-phone .page-inner {
    max-width: 75%;
    margin: 0 auto;
  }
}
@media screen and (min-width: 768px) {
  .layout-phone .page-inner {
    max-width: 600px;
    margin: 0 auto;
  }
}

.layout-tablet .page-inner {
  max-width: 760px;
  margin: 0 auto;
  padding: 24px;
}
.layout-tablet .detail-rule {
  font-size: 20px;
}
.layout-tablet .info-label {
  font-size: 14px;
}
.layout-tablet .info-value,
.layout-tablet .msg-text {
  font-size: 14px;
}
.layout-tablet .severity-tag {
  font-size: 13px;
  padding: 3px 10px;
}
.layout-tablet .action-btn {
  width: 200px;
  height: 44px;
  line-height: 44px;
  font-size: 15px;
}

.detail-card {
  background: #fff;
  border-radius: 16rpx;
  padding: 32rpx;
  box-shadow: 0 4rpx 12rpx rgba(0, 0, 0, 0.04);
}
.detail-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.severity-tag {
  color: #fff;
  font-size: 24rpx;
  padding: 6rpx 18rpx;
  border-radius: 8rpx;
}
.detail-status {
  font-size: 28rpx;
  font-weight: bold;
}
.detail-rule {
  margin: 24rpx 0;
  font-size: 36rpx;
  font-weight: bold;
  color: #303133;
}
.info-row {
  display: flex;
  justify-content: space-between;
  padding: 16rpx 0;
  border-bottom: 2rpx solid #f0f2f5;
}
.info-label {
  font-size: 26rpx;
  color: #909399;
  flex-shrink: 0;
}
.info-value {
  font-size: 26rpx;
  color: #303133;
  text-align: right;
  word-break: break-all;
}
.msg-block {
  margin-top: 24rpx;
}
.msg-text {
  display: block;
  margin-top: 12rpx;
  font-size: 28rpx;
  color: #606266;
  line-height: 1.6;
  word-break: break-all;
}
.action-row {
  display: flex;
  justify-content: center;
  gap: 32rpx;
  margin-top: 48rpx;
  padding-bottom: calc(32rpx + env(safe-area-inset-bottom));
}
.action-btn {
  flex: 1;
  max-width: 320rpx;
  height: 84rpx;
  line-height: 84rpx;
  border-radius: 12rpx;
  font-size: 30rpx;
  color: #fff;
  border: none;
}
.action-ack {
  background: #e6a23c;
}
.action-resolve {
  background: #67c23a;
}
</style>
