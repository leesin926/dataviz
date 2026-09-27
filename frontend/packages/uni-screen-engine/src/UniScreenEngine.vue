<template>
  <view class="uni-screen" :style="rootStyle">
    <view
      v-for="item in renderables"
      :key="item.component.id"
      class="uni-screen__comp"
      :style="item.style"
    >
      <!--
        小程序端不支持 `<component :is="">`（编译期直接报错），所以这里按 kind 静态分支。
        分支顺序与 registry 的 UniRendererKind 一致，新增渲染器要同时改两处。
      -->
      <UniChart
        v-if="item.kind === 'chart'"
        :component="item.component"
        :scale="metrics.scale"
        :env="env"
        :width="item.width"
        :height="item.height"
      />
      <UniText
        v-else-if="item.kind === 'text'"
        :component="item.component"
        :scale="metrics.scale"
        :env="env"
        :width="item.width"
        :height="item.height"
      />
      <UniClock
        v-else-if="item.kind === 'clock'"
        :component="item.component"
        :scale="metrics.scale"
        :env="env"
        :width="item.width"
        :height="item.height"
      />
      <UniImage
        v-else-if="item.kind === 'image'"
        :component="item.component"
        :scale="metrics.scale"
        :env="env"
        :width="item.width"
        :height="item.height"
      />
      <UniBorder
        v-else-if="item.kind === 'border'"
        :component="item.component"
        :scale="metrics.scale"
        :env="env"
        :width="item.width"
        :height="item.height"
      />
      <UniTable
        v-else-if="item.kind === 'table'"
        :component="item.component"
        :scale="metrics.scale"
        :env="env"
        :width="item.width"
        :height="item.height"
      />
      <UniScrollBoard
        v-else-if="item.kind === 'scrollBoard'"
        :component="item.component"
        :scale="metrics.scale"
        :env="env"
        :width="item.width"
        :height="item.height"
      />
      <UniUnknown v-else :component="item.component" :scale="metrics.scale" :width="item.width" :height="item.height" />
    </view>
    <view v-if="!renderables.length" class="uni-screen__empty">
      <text class="uni-screen__empty-text">{{ emptyText }}</text>
    </view>
  </view>
</template>

<script setup lang="ts">
  /**
   * uni 原生大屏渲染器（Phase 4 总入口）。
   *
   * 与 web 端 `ScreenEngine.vue` 的差异是刻意的：
   * ① 不整块 CSS transform 缩放，而是逐组件换算成设备像素（canvas 必须拿真实像素）；
   * ② 只渲染 `isUniNativeRenderable` 通过的组件，不可渲染的默认**整块跳过**（占位需显式开）；
   * ③ 取数走注入的 uni.request，不引 axios 版 api-client。
   */
  import { computed, onBeforeUnmount, provide, ref } from 'vue'
  import type { Screen, ScreenComponent, ScreenPlatform } from '@dataviz/shared-types'
  import { CANVAS_BG_COLOR, normalizeScreenInk } from '@dataviz/shared-types'
  import { componentBox, computeStage } from './core/scale'
  import { resolveRenderer } from './core/registry'
  import type { UniRendererKind } from './core/registry'
  import { getViewport } from './core/uniRuntime'
  import { globalMourning } from './core/mourning'
  import type { DataEnv } from './core/dataSource'
  import UniBorder from './components/UniBorder.vue'
  import UniChart from './components/UniChart.vue'
  import UniClock from './components/UniClock.vue'
  import UniImage from './components/UniImage.vue'
  import UniScrollBoard from './components/UniScrollBoard.vue'
  import UniTable from './components/UniTable.vue'
  import UniText from './components/UniText.vue'
  import UniUnknown from './components/UniUnknown.vue'

  const props = withDefaults(
    defineProps<{
      screen: Screen
      /** 本端标识：mobile / tablet（决定组件投放端过滤） */
      platform?: ScreenPlatform
      /** 网关基址，H5 用 '/api' 走代理，App/小程序用完整地址 */
      baseUrl?: string
      accessToken?: string
      /** 打开后不可渲染组件显示占位框（排障用），默认整块跳过 */
      showPlaceholder?: boolean
      /** 哀悼模式：整屏灰度（H5/App 支持 CSS filter，小程序端可能忽略） */
      grayMode?: boolean
    }>(),
    {
      platform: 'mobile',
      baseUrl: '/api',
      accessToken: '',
      showPlaceholder: false,
      grayMode: false,
    },
  )

  const viewport = getViewport()
  const normalized = computed(() => normalizeScreenInk(props.screen))
  const metrics = computed(() => computeStage(normalized.value, viewport))
  const emptyText = ref('该大屏在本端没有可原生渲染的组件')

  const env = computed<DataEnv>(() => ({ baseUrl: props.baseUrl, accessToken: props.accessToken }))

  /** 屏级刷新节拍：组件内 useComponentData 订阅它，实现 config.globalRefreshInterval */
  const tick = ref(0)
  provide('uniScreenTick', tick)
  let globalTimer: ReturnType<typeof setInterval> | null = null
  const globalInterval = Number(props.screen.config?.globalRefreshInterval) || 0
  if (globalInterval > 0) {
    globalTimer = setInterval(() => {
      tick.value += 1
    }, Math.max(5, globalInterval) * 1000)
  }
  onBeforeUnmount(() => {
    if (globalTimer) clearInterval(globalTimer)
  })

  const rootStyle = computed(() => {
    const cfg = normalized.value.config || {}
    const style: Record<string, string> = {
      position: 'relative',
      width: `${Math.max(viewport.width, metrics.value.stageWidth)}px`,
      // fixed-width/responsive 下内容可能高于视口，交给页面滚动而不是裁掉
      minHeight: `${Math.max(viewport.height, metrics.value.stageHeight)}px`,
      backgroundColor: cfg.backgroundColor || CANVAS_BG_COLOR,
    }
    if (cfg.backgroundImage) {
      style.backgroundImage = `url(${cfg.backgroundImage})`
      style.backgroundRepeat = cfg.backgroundRepeat || 'no-repeat'
      style.backgroundSize = cfg.backgroundSize || 'cover'
    }
    if (props.grayMode || globalMourning.value) style.filter = 'grayscale(100%)'
    return style
  })

  interface Renderable {
    component: ScreenComponent
    kind: UniRendererKind
    width: number
    height: number
    style: Record<string, string>
  }

  const renderables = computed<Renderable[]>(() => {
    const list = (normalized.value.components || []).filter((c) => c.visible !== false)
    const sorted = [...list].sort((a, b) => (Number(a.zIndex) || 0) - (Number(b.zIndex) || 0))
    const out: Renderable[] = []
    for (const comp of sorted) {
      const hit = resolveRenderer(comp, props.platform, props.showPlaceholder)
      if (!hit) continue
      const box = componentBox(comp, metrics.value)
      if (box.width <= 0 || box.height <= 0) continue
      out.push({
        component: comp,
        kind: hit.kind,
        width: box.width,
        height: box.height,
        style: {
          position: 'absolute',
          left: `${box.left}px`,
          top: `${box.top}px`,
          width: `${box.width}px`,
          height: `${box.height}px`,
          zIndex: String(box.zIndex),
        },
      })
    }
    return out
  })
</script>

<style scoped>
  .uni-screen {
    overflow: hidden;
  }
  .uni-screen__comp {
    box-sizing: border-box;
    overflow: hidden;
  }
  .uni-screen__empty {
    padding: 40px 12px;
    text-align: center;
  }
  .uni-screen__empty-text {
    font-size: 13px;
    color: #909399;
  }
</style>
