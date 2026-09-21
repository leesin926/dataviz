<template>
  <div class="screen-map">
    <div v-show="state === 'ready'" ref="mapRef" class="map-canvas"></div>
    <div v-if="state === 'loading'" class="map-tip">
      <span class="dot" />{{ loadingText }}
    </div>
    <div v-else-if="state === 'error'" class="map-tip is-error">
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

  const props = defineProps<{
    component: ScreenComponent
  }>()

  const mapRef = ref<HTMLDivElement | null>(null)
  const state = ref<'loading' | 'ready' | 'error'>('loading')
  const errorText = ref('地图数据加载失败')
  let chart: ECharts | null = null

  const p = computed(() => props.component.props || {})
  const loadingText = computed(() => `正在加载地图数据 · ${strOf(p.value.mapName, 'china')}`)

  const geoUrl = computed(() => strOf(p.value.geoUrl))
  const mapName = computed(() => strOf(p.value.mapName, 'china'))

  const demoData = computed(() => {
    const raw = p.value.data
    return Array.isArray(raw) ? (raw as Array<{ name: string; value: number }>) : []
  })

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
    const values = demoData.value.map((d) => numOf(d?.value, 0))
    const min = values.length ? Math.min(...values) : 0
    const max = values.length ? Math.max(...values) : 100
    const borderColor = strOf(p.value.borderColor, CANVAS_INK.accent)
    chart.setOption(
      {
        backgroundColor: 'transparent',
        tooltip: {
          trigger: 'item',
          backgroundColor: CANVAS_INK.surface,
          borderColor: CANVAS_INK.line,
          textStyle: { color: CANVAS_INK.normal },
          formatter: (arg: unknown) => {
            const o = arg as { name: string; value?: number }
            return `${o.name}：${o.value ?? 0}`
          },
        },
        visualMap: {
          min,
          max: max > min ? max : min + 1,
          left: 12,
          bottom: 12,
          calculable: true,
          seriesIndex: 0,
          textStyle: { color: CANVAS_INK.muted },
          inRange: { color: ['#123152', '#1a5fa8', strOf(p.value.activeColor, CANVAS_INK.accent)] },
        },
        series: [
          {
            type: 'map',
            map: mapName.value,
            roam: true,
            zoom: 1.1,
            nameProperty: 'name',
            data: demoData.value,
            label: { show: false, color: CANVAS_INK.normal },
            itemStyle: { borderColor, borderWidth: 1, areaColor: strOf(p.value.color, '#123152') },
            emphasis: {
              label: { show: true, color: CANVAS_INK.onAccent },
              itemStyle: { areaColor: strOf(p.value.activeColor, CANVAS_INK.accent) },
            },
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
    () => [geoUrl.value, mapName.value, p.value.color, p.value.borderColor, p.value.activeColor, p.value.data],
    () => render(),
    { deep: true },
  )
</script>

<style scoped>
  .screen-map {
    position: relative;
    width: 100%;
    height: 100%;
    overflow: hidden;
  }
  .map-canvas {
    width: 100%;
    height: 100%;
  }
  .map-tip {
    position: absolute;
    inset: 0;
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 8px;
    color: #5b6a80;
    font-size: 12px;
    border: 1px dashed rgba(22, 32, 47, 0.18);
    background: rgba(255, 255, 255, 0.72);
  }
  .map-tip.is-error {
    color: #b7791f;
    border-color: rgba(183, 121, 31, 0.45);
  }
  .map-tip .dot {
    width: 8px;
    height: 8px;
    border-radius: 50%;
    background: #2f7cff;
    animation: map-pulse 1s ease-in-out infinite;
  }
  .map-tip .retry {
    padding: 2px 10px;
    font-size: 12px;
    color: #b7791f;
    background: transparent;
    border: 1px solid rgba(183, 121, 31, 0.5);
    border-radius: 3px;
    cursor: pointer;
  }
  @keyframes map-pulse {
    0%,
    100% {
      opacity: 0.25;
    }
    50% {
      opacity: 1;
    }
  }
</style>
