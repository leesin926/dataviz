<template>
  <div class="screen-chart" ref="wrapRef">
    <ChartEngine
      v-if="component.chartConfig && geoReady"
      :config="component.chartConfig"
      :data="chartData"
      width="100%"
      height="100%"
      :theme="component.chartConfig.theme || 'brand'"
      :loading="loading"
    />
    <div v-else-if="geoTip" class="chart-placeholder is-warn">{{ geoTip }}</div>
    <div v-else class="chart-placeholder">
      <span>未配置图表</span>
    </div>
  </div>
</template>

<script setup lang="ts">
  import { computed, onMounted, ref, watch } from 'vue'
  import type { ScreenComponent } from '@dataviz/shared-types'
  import { ChartType, DEFAULT_GEO_URL } from '@dataviz/shared-types'
  import { ChartEngine } from '@dataviz/chart-engine'
  import { executeQuery } from '@dataviz/api-client'
  import { loadGeoMap } from '../render/geo'
  import { strOf } from './props'

  const props = defineProps<{
    component: ScreenComponent
  }>()

  const wrapRef = ref<HTMLDivElement | null>(null)
  const loading = ref(false)
  const geoReady = ref(true)
  const geoTip = ref('')
  const chartData = ref<{ columns: Array<{ field: string; type: string }>; rows: Record<string, unknown>[] }>({
    columns: [],
    rows: [],
  })

  const cfg = computed(() => props.component.chartConfig)
  const needsGeo = computed(() => cfg.value?.chartType === ChartType.MAP)

  /** 地图类图表需要先注册 GeoJSON，否则 ECharts 直接抛错导致整块空白 */
  async function ensureGeo(): Promise<void> {
    if (!needsGeo.value) {
      geoReady.value = true
      geoTip.value = ''
      return
    }
    const mapName = strOf(cfg.value?.options?.mapName, 'china')
    const url = strOf(props.component.props?.geoUrl, DEFAULT_GEO_URL)
    geoReady.value = false
    geoTip.value = '正在加载地图数据…'
    try {
      await loadGeoMap(mapName, url)
      geoReady.value = true
      geoTip.value = ''
    } catch (e) {
      geoTip.value = `地图数据加载失败：${(e as Error).message}`
    }
  }

  async function loadData() {
    const req = props.component.request
    if (req?.sourceType === 'static') {
      const sd = req.staticData as { columns?: Array<{ field: string; type: string }>; rows?: Record<string, unknown>[] } | undefined
      if (sd && Array.isArray(sd.rows)) {
        chartData.value = { columns: sd.columns || [], rows: sd.rows }
      }
      return
    }
    const datasetId = cfg.value?.datasetId
    if (!datasetId) return
    loading.value = true
    try {
      const dims = (cfg.value?.dimensions || []).map((d) => ({ field: d.field, alias: d.alias }))
      const measures = (cfg.value?.measures || []).map((m) => ({
        field: m.field,
        alias: m.alias,
        aggregation: m.aggregation,
      }))
      const result = await executeQuery({ datasetId, dimensions: dims, measures })
      chartData.value = { columns: result.columns, rows: result.rows }
    } catch (e) {
      console.error('[ScreenChart] load data error:', e)
    } finally {
      loading.value = false
    }
  }

  async function refresh() {
    await ensureGeo()
    await loadData()
  }

  onMounted(() => refresh())

  watch(
    () => [props.component.chartConfig, props.component.request, props.component.props],
    () => refresh(),
    { deep: true },
  )
</script>

<style scoped>
  .screen-chart {
    width: 100%;
    height: 100%;
  }
  .chart-placeholder {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 100%;
    height: 100%;
    color: #5b6a80;
    font-size: 12px;
    border: 1px dashed #dfe5ef;
  }
  .chart-placeholder.is-warn {
    color: #b7791f;
    border-color: rgba(183, 121, 31, 0.45);
  }
</style>
