<template>
  <view class="spike">
    <view class="block">
      <text class="title">ucharts core + uni.createCanvasContext bar（4.3 目标架构验证）</text>
      <canvas canvas-id="core-bar" id="core-bar" class="cv" />
    </view>
    <view class="block">
      <text class="title">ucharts core line</text>
      <canvas canvas-id="core-line" id="core-line" class="cv" />
    </view>
    <view class="block">
      <text class="title">ucharts core gauge</text>
      <canvas canvas-id="core-gauge" id="core-gauge" class="cv" />
    </view>
    <view class="block">
      <text class="title">ucharts core pie/ring</text>
      <canvas canvas-id="core-pie" id="core-pie" class="cv" />
    </view>
    <view class="block">
      <text class="title">原生 canvas 自绘（退路基线）</text>
      <canvas canvas-id="spike-native" id="spike-native" class="cv" />
    </view>
  </view>
</template>

<script setup lang="ts">
  import { getCurrentInstance } from 'vue'
  import { onReady } from '@dcloudio/uni-app'
  import uCharts from '@qiun/ucharts'

  const categories = ['一月', '二月', '三月', '四月', '五月', '六月']
  const series = [
    { name: 'sales', data: [120, 200, 150, 260, 190, 310] },
    { name: 'profit', data: [32, 54, 40, 78, 50, 96] },
  ]
  const color = ['#5470c6', '#91cc75']

  onReady(() => {
    const instance = getCurrentInstance()?.proxy
    const W = 330
    const H = 240
    const mk = (id: string, opts: Record<string, unknown>) => {
      const context = uni.createCanvasContext(id, instance)
      return new uCharts({
        type: undefined,
        context,
        width: W * 2,
        height: H * 2,
        pixelRatio: 2,
        background: '#FFFFFF',
        extra: { bar: {}, line: {}, pie: {}, ring: {}, rose: {}, gauge: {}, area: {} },
        ...opts,
      } as any)
    }
    mk('core-bar', { type: 'bar', categories, series, color, padding: [15, 15, 30, 15] })
    mk('core-line', { type: 'line', categories, series, color, dataLabel: false })
    mk('core-gauge', {
      type: 'gauge',
      series: [{ name: '完成率', data: 66 }],
      color: ['#3fdaff'],
      gauge: { splitNumber: 5 },
    })
    mk('core-pie', {
      type: 'ring',
      series: [{ name: '占比', data: [{ name: 'A', value: 45 }, { name: 'B', value: 30 }, { name: 'C', value: 25 }] }],
      color: ['#5470c6', '#91cc75', '#f6bd16'],
    })
    const ctx = uni.createCanvasContext('spike-native', instance)
    ctx.setFillStyle('#3fdaff')
    series[0].data.forEach((v, i) => {
      ctx.fillRect(15 + i * 50, 160 - v / 3, 30, v / 3)
    })
    ctx.draw()
  })
</script>

<style scoped>
  .spike {
    padding: 12px;
  }
  .block {
    margin-bottom: 20px;
  }
  .title {
    display: block;
    font-size: 13px;
    color: #666;
    margin-bottom: 6px;
  }
  .cv {
    width: 330px;
    height: 240px;
    background: #f7f8fa;
  }
</style>
