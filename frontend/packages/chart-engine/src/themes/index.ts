import * as echarts from 'echarts'
import type { EChartsOption } from 'echarts'
import { CANVAS_INK, CHART_PALETTE } from '@dataviz/shared-types'

export type ThemeName = 'light' | 'dark' | 'brand'

const lightTheme: EChartsOption = {
  textStyle: { fontFamily: 'PingFang SC, Microsoft YaHei, sans-serif', color: '#333' },
  title: { textStyle: { color: '#333' } },
  legend: { textStyle: { color: '#666' } },
  tooltip: {
    backgroundColor: 'rgba(255,255,255,0.96)',
    borderColor: '#eee',
    textStyle: { color: '#333' },
  },
  color: ['#5470c6', '#91cc75', '#fac858', '#ee6666', '#73c0de', '#3ba272', '#fc8452', '#9a60b4', '#ea7ccc'],
}

const darkTheme: EChartsOption = {
  // 透明底：图表不能盖住大屏背景，否则与画布撞色成黑色方块
  backgroundColor: 'transparent',
  textStyle: { fontFamily: 'PingFang SC, Microsoft YaHei, sans-serif', color: '#ddd' },
  title: { textStyle: { color: '#eee' } },
  legend: { textStyle: { color: '#aaa' } },
  tooltip: {
    backgroundColor: 'rgba(20,20,20,0.96)',
    borderColor: '#333',
    textStyle: { color: '#ddd' },
  },
  color: ['#5470c6', '#91cc75', '#fac858', '#ee6666', '#73c0de', '#3ba272', '#fc8452', '#9a60b4', '#ea7ccc'],
}

const brandTheme: EChartsOption = {
  // 大屏主题：底透明，让画布背景透出，避免图表块与背景撞色
  backgroundColor: 'transparent',
  // 墨色全部取自 CANVAS_INK：画布锁白后，浅底必须用深色文字，否则白字白底看不见
  textStyle: { fontFamily: 'PingFang SC, Microsoft YaHei, sans-serif', color: CANVAS_INK.normal },
  title: { textStyle: { color: CANVAS_INK.strong } },
  legend: { textStyle: { color: CANVAS_INK.muted } },
  tooltip: {
    backgroundColor: CANVAS_INK.surface,
    borderColor: CANVAS_INK.line,
    textStyle: { color: CANVAS_INK.normal },
  },
  color: [...CHART_PALETTE],
  // 主题级坐标轴默认样式（ECharts 会以此作为 categoryAxis/valueAxis 的基础配置）
  categoryAxis: {
    axisLine: { lineStyle: { color: CANVAS_INK.line } },
    axisTick: { show: false },
    axisLabel: { color: CANVAS_INK.muted },
    splitLine: { show: false },
  },
  valueAxis: {
    axisLine: { show: false },
    axisTick: { show: false },
    axisLabel: { color: CANVAS_INK.muted },
    splitLine: { lineStyle: { color: CANVAS_INK.faint } },
  },
  radar: {
    axisName: { color: CANVAS_INK.muted },
    splitLine: { lineStyle: { color: CANVAS_INK.faint } },
    axisLine: { lineStyle: { color: CANVAS_INK.line } },
    splitArea: { areaStyle: { color: ['transparent', CANVAS_INK.faint] } },
  },
}

export const BUILT_IN_THEMES = {
  light: lightTheme,
  dark: darkTheme,
  brand: brandTheme,
} as const

export function getTheme(name: ThemeName): EChartsOption {
  return BUILT_IN_THEMES[name as keyof typeof BUILT_IN_THEMES] || lightTheme
}

let registered = false

/**
 * 把内置主题注册进 echarts，使 echarts.init(dom, name) 能拿到主题级默认值。
 * 必须注册：仅靠 setOption 展开主题对象会覆盖 option 内同名键（如 legend），
 * 导致图例/坐标轴文字退回 echarts 默认的深灰色，在大屏深色底上看不清。
 */
export function registerTheme(): void {
  if (registered) return
  registered = true
  for (const [name, option] of Object.entries(BUILT_IN_THEMES)) {
    echarts.registerTheme(name, option)
  }
}
