<template>
  <div ref="chartRef" class="chart-engine" :style="{ width, height }"></div>
</template>

<script setup lang="ts">
  import { ref, watch, onMounted, onBeforeUnmount, computed } from 'vue'
  import type { ChartConfig } from '@dataviz/shared-types'
  import { ChartType } from '@dataviz/shared-types'
  import { EChartsRenderer } from './renderer/EChartsRenderer'
  import { CanvasRenderer } from './renderer/CanvasRenderer'
  import { DataAdapter } from './adapters/DataAdapter'
  import { ChartConfigAdapter } from './adapters/ChartConfigAdapter'

  interface QueryResultLike {
    columns: Array<{ field: string; type: string }>
    rows: Record<string, unknown>[]
  }

  const props = withDefaults(
    defineProps<{
      config: ChartConfig
      data: QueryResultLike
      width?: string
      height?: string
      theme?: 'light' | 'dark' | 'brand'
      loading?: boolean
    }>(),
    {
      width: '100%',
      height: '300px',
      theme: 'light',
      loading: false,
    },
  )

  const emit = defineEmits<{
    (e: 'click', payload: unknown): void
    (e: 'dblclick', payload: unknown): void
    (e: 'brush', payload: unknown): void
  }>()

  const chartRef = ref<HTMLDivElement | null>(null)
  let renderer: EChartsRenderer | CanvasRenderer | null = null
  let rendererTheme = ''
  let rendererIsCanvas = false

  const normalizedConfig = computed(() => ChartConfigAdapter.normalize(props.config))

  function renderChart() {
    if (!chartRef.value) return
    const cfg = normalizedConfig.value
    const theme = props.config.theme || props.theme || 'light'
    const wantCanvas = cfg.chartType === ChartType.KPI_CARD || cfg.chartType === ChartType.TABLE
    // echarts 主题只在 init 时生效，因此仅在类型/主题切换时重建实例，数据更新复用实例
    if (!renderer || wantCanvas !== rendererIsCanvas || (!wantCanvas && theme !== rendererTheme)) {
      renderer?.dispose()
      renderer = wantCanvas
        ? new CanvasRenderer(chartRef.value)
        : new EChartsRenderer(chartRef.value, theme)
      rendererIsCanvas = wantCanvas
      rendererTheme = theme
      bindEvents()
    }
    const chartData = DataAdapter.transform(cfg, props.data)
    renderer.render(cfg, chartData)
  }

  function bindEvents() {
    if (!renderer) return
    renderer.on('click', (payload) => emit('click', payload))
    renderer.on('dblclick', (payload) => emit('dblclick', payload))
    renderer.on('brush', (payload) => emit('brush', payload))
  }

  watch(
    () => [props.config, props.data, props.theme],
    () => {
      renderChart()
    },
    { deep: true },
  )

  watch(
    () => props.loading,
    (val) => renderer?.setLoading?.(val),
  )

  onMounted(() => {
    renderChart()
    window.addEventListener('resize', handleResize)
  })

  onBeforeUnmount(() => {
    window.removeEventListener('resize', handleResize)
    renderer?.dispose()
    renderer = null
  })

  function handleResize() {
    renderer?.resize?.()
  }

  defineExpose({
    getRenderer: () => renderer,
    exportImage: (type = 'png') => renderer?.exportImage?.(type) ?? null,
  })
</script>

<style scoped>
  .chart-engine {
    display: block;
    position: relative;
  }
</style>
