<template>
  <div class="screen-flyline">
    <div v-show="state === 'ready'" ref="mapRef" class="map-canvas"></div>
    <div v-if="state === 'loading'" class="tip">
      <span class="dot" />正在加载飞线地图
    </div>
    <div v-else-if="state === 'error'" class="tip is-error">
      <span>{{ errorText }}</span>
      <button class="retry" @click="render">重试</button>
    </div>
  </div>
</template>

<script setup lang="ts">
  import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
  import * as echarts from 'echarts'
  import type { ECharts } from 'echarts'
  import type { ScreenComponent } from '@dataviz/shared-types'
  import { CANVAS_INK } from '@dataviz/shared-types'
  import { loadGeoMap } from '../render/geo'
  import { numOf, strOf } from './props'

  interface GeoPoint {
    name: string
    lon: number
    lat: number
    value: number
  }
  interface GeoRoute {
    from: string
    to: string
    value: number
  }

  const props = defineProps<{
    component: ScreenComponent
  }>()

  const mapRef = ref<HTMLDivElement | null>(null)
  const state = ref<'loading' | 'ready' | 'error'>('loading')
  const errorText = ref('地图数据加载失败')
  let chart: ECharts | null = null

  const p = computed(() => props.component.props || {})
  const geoUrl = computed(() => strOf(p.value.geoUrl))
  const mapName = computed(() => strOf(p.value.mapName, 'china'))

  const points = computed(() =>
    Array.isArray(p.value.points) ? (p.value.points as unknown as GeoPoint[]) : [],
  )
  const routes = computed(() =>
    Array.isArray(p.value.routes) ? (p.value.routes as unknown as GeoRoute[]) : [],
  )

  async function render() {
    if (!geoUrl.value) {
      state.value = 'error'
      errorText.value = '未配置 GeoJSON 地址（geoUrl）'
      return
    }
    state.value = 'loading'
    try {
      await loadGeoMap(mapName.value, geoUrl.value)
    } catch (e) {
      state.value = 'error'
      errorText.value = `地图数据加载失败：${(e as Error).message}`
      return
    }
    state.value = 'ready'
    await nextTick()
    if (!mapRef.value) return
    if (!chart || chart.isDisposed()) chart = echarts.init(mapRef.value)
    draw()
  }

  function draw() {
    if (!chart) return
    const lineColor = strOf(p.value.lineColor, CANVAS_INK.accent)
    const maxVal = points.value.reduce((m, x) => Math.max(m, numOf(x.value, 0)), 1)
    // 必须显式给 coords：lines 的 from/to 名称只会在 geo 的 region（省级）里查，
    // 城市名查不到会抛 "Invalid coords undefined" 并让整块渲染不出
    const pointMap = new Map(points.value.map((pt) => [pt.name, pt]))
    const lineData: Array<{ name: string; coords: number[][] }> = []
    for (const r of routes.value) {
      const from = pointMap.get(r.from)
      const to = pointMap.get(r.to)
      if (!from || !to) continue
      lineData.push({
        name: `${from.name} → ${to.name}`,
        coords: [
          [numOf(from.lon, 0), numOf(from.lat, 0)],
          [numOf(to.lon, 0), numOf(to.lat, 0)],
        ],
      })
    }
    chart.setOption(
      {
        backgroundColor: 'transparent',
        tooltip: {
          backgroundColor: CANVAS_INK.surface,
          borderColor: CANVAS_INK.line,
          textStyle: { color: CANVAS_INK.normal },
        },
        geo: {
          map: mapName.value,
          roam: true,
          zoom: 1.1,
          nameProperty: 'name',
          itemStyle: {
            areaColor: strOf(p.value.color, '#123152'),
            borderColor: strOf(p.value.borderColor, '#2a6fa8'),
            borderWidth: 1,
          },
          emphasis: { label: { show: false }, itemStyle: { areaColor: '#1a4a76' } },
        },
        series: [
          {
            type: 'effectScatter',
            coordinateSystem: 'geo',
            zlevel: 2,
            rippleEffect: { brushType: 'stroke', scale: 3 },
            showEffectOn: 'render',
            symbolSize: (val: number[]) => 6 + (val[2] / maxVal) * 10,
            label: { show: true, formatter: '{b}', position: 'right', color: CANVAS_INK.muted, fontSize: 11 },
            itemStyle: { color: lineColor, shadowBlur: 8, shadowColor: lineColor },
            data: points.value.map((pt) => ({
              name: pt.name,
              value: [numOf(pt.lon, 0), numOf(pt.lat, 0), numOf(pt.value, 0)],
            })),
          },
          {
            type: 'lines',
            coordinateSystem: 'geo',
            zlevel: 1,
            effect: { show: true, trailLength: 0.35, symbol: 'arrow', symbolSize: 5, color: lineColor },
            lineStyle: { color: lineColor, width: 1, opacity: 0.45, curveness: 0.25 },
            data: lineData,
          },
        ],
      },
      { notMerge: true },
    )
    chart.resize()
  }

  function handleResize() {
    chart?.resize()
  }

  onMounted(() => {
    render()
    window.addEventListener('resize', handleResize)
  })

  onBeforeUnmount(() => {
    window.removeEventListener('resize', handleResize)
    chart?.dispose()
    chart = null
  })

  watch(
    () => [geoUrl.value, mapName.value, p.value.color, p.value.borderColor, p.value.lineColor, p.value.points, p.value.routes],
    () => render(),
    { deep: true },
  )
</script>

<style scoped>
  .screen-flyline {
    position: relative;
    width: 100%;
    height: 100%;
    overflow: hidden;
  }
  .map-canvas {
    width: 100%;
    height: 100%;
  }
  .tip {
    position: absolute;
    inset: 0;
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 8px;
    color: #5b6a80;
    font-size: 12px;
    border: 1px dashed rgba(22, 32, 47, 0.18);
  }
  .tip.is-error {
    color: #b7791f;
    border-color: rgba(183, 121, 31, 0.45);
  }
  .tip .dot {
    width: 8px;
    height: 8px;
    border-radius: 50%;
    background: #2f7cff;
    animation: fly-pulse 1s ease-in-out infinite;
  }
  .tip .retry {
    padding: 2px 10px;
    font-size: 12px;
    color: #b7791f;
    background: transparent;
    border: 1px solid rgba(183, 121, 31, 0.5);
    border-radius: 3px;
    cursor: pointer;
  }
  @keyframes fly-pulse {
    0%,
    100% {
      opacity: 0.25;
    }
    50% {
      opacity: 1;
    }
  }
</style>
