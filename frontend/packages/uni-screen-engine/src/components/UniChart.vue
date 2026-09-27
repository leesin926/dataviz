<template>
  <view class="uni-chart">
    <canvas
      v-if="!error"
      :canvas-id="canvasId"
      :id="canvasId"
      class="uni-chart__cv"
      :style="{ width: width + 'px', height: height + 'px' }"
    />
    <view v-if="error" class="uni-chart__error">
      <text class="uni-chart__error-title">{{ component.name || '图表' }}取数失败</text>
      <text class="uni-chart__error-msg">{{ error }}</text>
    </view>
  </view>
</template>

<script setup lang="ts">
  /**
   * uCharts 适配层（4.3）：只消费 chartConfig 的语义字段，不碰 ECharts option。
   * 取数失败时**渲染错误文案而不是空图** —— 空图与"数据本来为空"在演示现场无法区分。
   */
  import { computed, getCurrentInstance, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
  import uCharts from '@qiun/ucharts'
  import { toUCharts } from '../core/chartAdapter'
  import { useComponentData } from '../core/dataSource'
  import { DEFAULT_ENV, type UniRendererProps } from './props'
  import { createCanvasContext, getViewport } from '../core/uniRuntime'
  import { scaledFontSize } from '../core/scale'

  const props = defineProps<UniRendererProps>()

  const instance = getCurrentInstance()
  // canvas-id 在同页面内必须唯一，且只允许字母数字下划线短横
  const canvasId = `usc-${String(props.component.id).replace(/[^A-Za-z0-9_-]/g, '')}`
  const viewport = getViewport()

  const { data, error } = useComponentData(
    computed(() => props.component),
    (props.env || DEFAULT_ENV),
  )

  let chart: InstanceType<typeof uCharts> | null = null
  let drawnType = ''
  let disposed = false
  let retry = 0

  function draw(): void {
    if (disposed || error.value || (props.width ?? 0) <= 0 || (props.height ?? 0) <= 0) return
    const context = createCanvasContext(canvasId, instance?.proxy)
    if (!context) {
      // App / 小程序端 canvas 节点挂载比 mounted 晚，有限次重试而不是静默不出图
      if (retry < 5) {
        retry += 1
        setTimeout(draw, 60 * retry)
      } else {
        error.value = '画布节点未就绪（canvas context 创建失败）'
      }
      return
    }
    retry = 0
    const payload = toUCharts(props.component.chartConfig, data.value || {})
    const opts = {
      type: payload.type,
      context,
      width: Math.round((props.width ?? 0) * viewport.pixelRatio),
      height: Math.round((props.height ?? 0) * viewport.pixelRatio),
      pixelRatio: viewport.pixelRatio,
      background: '#FFFFFF',
      padding: [8, 8, 8, 8],
      fontSize: scaledFontSize(11, (props.scale ?? 1), 9),
      legendFontSize: scaledFontSize(11, (props.scale ?? 1), 9),
      dataLabel: payload.type !== 'line' && payload.type !== 'area',
      categories: payload.categories,
      series: payload.series,
      color: payload.color,
      extra: payload.extra,
    }
    try {
      // 同类型走 updateData（轮询场景避免反复重建实例），换类型只能重建
      if (chart && drawnType === payload.type) chart.updateData(opts)
      else chart = new uCharts(opts)
      drawnType = payload.type
    } catch (e) {
      error.value = `图表渲染失败：${(e as Error).message}`
    }
  }

  onMounted(() => void nextTick(draw))

  watch(
    () => [data.value, (props.width ?? 0), (props.height ?? 0), props.component.chartConfig],
    () => void nextTick(draw),
    { deep: true },
  )

  onBeforeUnmount(() => {
    disposed = true
    chart = null
  })
</script>

<style scoped>
  .uni-chart {
    width: 100%;
    height: 100%;
    overflow: hidden;
  }
  .uni-chart__cv {
    display: block;
  }
  .uni-chart__error {
    padding: 6px;
  }
  .uni-chart__error-title {
    display: block;
    font-size: 12px;
    color: #f56c6c;
  }
  .uni-chart__error-msg {
    font-size: 11px;
    color: #909399;
  }
</style>
