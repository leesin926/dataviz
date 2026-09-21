import type { Component } from 'vue'
import { isChartPreset } from '@dataviz/shared-types'
import ScreenChart from '../components/ScreenChart.vue'
import ScreenText from '../components/ScreenText.vue'
import ScreenMarquee from '../components/ScreenMarquee.vue'
import ScreenCountdown from '../components/ScreenCountdown.vue'
import ScreenClock from '../components/ScreenClock.vue'
import ScreenImage from '../components/ScreenImage.vue'
import ScreenVideo from '../components/ScreenVideo.vue'
import ScreenIframe from '../components/ScreenIframe.vue'
import ScreenBorder from '../components/ScreenBorder.vue'
import ScreenDecoration from '../components/ScreenDecoration.vue'
import ScreenTable from '../components/ScreenTable.vue'
import ScreenScrollBoard from '../components/ScreenScrollBoard.vue'
import ScreenNumberFlop from '../components/ScreenNumberFlop.vue'
import ScreenStatCard from '../components/ScreenStatCard.vue'
import ScreenProgress from '../components/ScreenProgress.vue'
import ScreenRanking from '../components/ScreenRanking.vue'
import ScreenMap from '../components/ScreenMap.vue'
import ScreenFlyline from '../components/ScreenFlyline.vue'
import ScreenWaterPolo from '../components/ScreenWaterPolo.vue'
import ScreenUnknown from '../components/ScreenUnknown.vue'

/**
 * 组件类型 → 渲染器注册表（web 端）。
 * 编辑器画布与展示端 ScreenEngine 共用同一份，避免两端类型表分叉；
 * 自定义组件开发 = shared-types 注册 definition + 此处（或运行时）注册 renderer。
 */
const renderers = new Map<string, Component>()

export function registerScreenRenderer(type: string, impl: Component): void {
  renderers.set(type, impl)
}

export function resolveScreenRenderer(type: string): Component {
  const hit = renderers.get(type)
  if (hit) return hit
  // 图表预设类组件（含运行时注册的自定义图表）统一由 ScreenChart 承载
  if (isChartPreset(type)) return ScreenChart
  return ScreenUnknown
}

const BUILTIN_RENDERERS: Array<[string, Component]> = [
  ['chart', ScreenChart],
  ['text', ScreenText],
  ['marquee', ScreenMarquee],
  ['countdown', ScreenCountdown],
  ['clock', ScreenClock],
  ['image', ScreenImage],
  ['video', ScreenVideo],
  ['iframe', ScreenIframe],
  ['border', ScreenBorder],
  ['decoration', ScreenDecoration],
  ['table', ScreenTable],
  ['scrollBoard', ScreenScrollBoard],
  ['numberFlop', ScreenNumberFlop],
  ['statCard', ScreenStatCard],
  ['progress', ScreenProgress],
  ['ranking', ScreenRanking],
  ['map', ScreenMap],
  ['flyline', ScreenFlyline],
  ['waterPolo', ScreenWaterPolo],
]

BUILTIN_RENDERERS.forEach(([type, impl]) => registerScreenRenderer(type, impl))

export { ScreenUnknown }
