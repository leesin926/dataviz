<template>
  <div ref="chartContainer" class="chart-component" :class="{ 'is-loading': loading }"></div>
</template>

<script setup lang="ts">
import { ref, watch, onMounted, onBeforeUnmount, computed } from 'vue'
import type { EChartsOption } from 'echarts'
import type { ChartConfig } from '@dataviz/shared-types'
import type { ThemeName } from '../core/ChartThemeManager'
import { ChartRenderer } from '../core/ChartRenderer'
import { BarChart } from '../charts/BarChart'
import { LineChart } from '../charts/LineChart'
import { PieChart } from '../charts/PieChart'
import { ScatterChart } from '../charts/ScatterChart'
import { MapChart } from '../charts/MapChart'
import { GaugeChart } from '../charts/GaugeChart'
import { RadarChart } from '../charts/RadarChart'

interface ChartDataInput {
  dimensions: string[]
  series: Array<{ name: string; data: number[] }>
}

const props = withDefaults(
  defineProps<{
    type: string
    data: ChartDataInput | Record<string, unknown>
    config?: ChartConfig
    options?: EChartsOption
    theme?: ThemeName
    loading?: boolean
  }>(),
  {
    theme: 'light',
    loading: false,
  }
)

const emit = defineEmits<{
  (e: 'click', params: unknown): void
  (e: 'dblclick', params: unknown): void
  (e: 'mouseover', params: unknown): void
  (e: 'mouseout', params: unknown): void
}>()

const chartContainer = ref<HTMLElement | null>(null)
let renderer: ChartRenderer | null = null
let resizeObserver: ResizeObserver | null = null

/**
 * 构建图表配置
 */
const chartOption = computed<EChartsOption | null>(() => {
  if (!props.config) return props.options || null

  const config = props.config
  const data = props.data as ChartDataInput

  switch (props.type) {
    case 'bar':
      return BarChart.buildOption(config, data)
    case 'line':
      return LineChart.buildOption(config, data)
    case 'pie':
      return PieChart.buildOption(config, props.data as never)
    case 'scatter':
      return ScatterChart.buildOption(config, props.data as never)
    case 'map':
      return MapChart.buildOption(config, props.data as never)
    case 'gauge':
      return GaugeChart.buildOption(config, props.data as never)
    case 'radar':
      return RadarChart.buildOption(config, props.data as never)
    default:
      return props.options || null
  }
})

/**
 * 渲染图表
 */
function renderChart(): void {
  if (!renderer || !chartOption.value) return

  const option = props.options
    ? { ...chartOption.value, ...props.options }
    : chartOption.value

  renderer.setOption(option, true)
}

/**
 * 绑定图表事件
 */
function bindEvents(): void {
  if (!renderer) return

  renderer.on('click', (params) => emit('click', params))
  renderer.on('dblclick', (params) => emit('dblclick', params))
  renderer.on('mouseover', (params) => emit('mouseover', params))
  renderer.on('mouseout', (params) => emit('mouseout', params))
}

/**
 * 监听数据和配置变化
 */
watch(
  () => [props.type, props.data, props.options, props.config],
  () => renderChart(),
  { deep: true }
)

watch(
  () => props.theme,
  (newTheme) => {
    if (renderer && newTheme) {
      renderer.setTheme(newTheme)
      renderChart()
    }
  }
)

watch(
  () => props.loading,
  (isLoading) => {
    if (!renderer) return
    if (isLoading) {
      renderer.showLoading()
    } else {
      renderer.hideLoading()
    }
  }
)

onMounted(() => {
  if (!chartContainer.value) return

  renderer = new ChartRenderer(chartContainer.value, props.theme)
  bindEvents()
  renderChart()

  // 自动 resize
  resizeObserver = new ResizeObserver(() => {
    renderer?.resize()
  })
  resizeObserver.observe(chartContainer.value)
})

onBeforeUnmount(() => {
  resizeObserver?.disconnect()
  renderer?.dispose()
  renderer = null
})

defineExpose({
  getRenderer: () => renderer,
  setOption: (option: EChartsOption) => renderer?.setOption(option),
  resize: () => renderer?.resize(),
  getDataURL: (opts?: { type?: string }) => renderer?.getDataURL(opts as never) || '',
})
</script>

<style scoped>
.chart-component {
  width: 100%;
  height: 100%;
  min-height: 200px;
}

.chart-component.is-loading {
  opacity: 0.6;
}
</style>
