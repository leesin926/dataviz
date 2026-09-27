/**
 * 布局换算（4.1）：设计稿坐标 → 设备像素。
 *
 * 不用 CSS transform 缩放整块舞台 —— uni 的 canvas 按**真实像素**取尺寸，
 * 容器 transform 缩放会让 ucharts 出糊图且触摸坐标错位，所以逐组件乘比例。
 * 三种 adaptationMode 的口径与 web 展示端 `ScreenEngine.vue` 保持一致。
 */

import type { Screen, ScreenComponent } from '@dataviz/shared-types'
import type { Viewport } from './uniRuntime'

export interface StageMetrics {
  /** 设计稿 px → 设备 px 的比例 */
  scale: number
  /** 舞台在视口内的居中偏移 */
  offsetX: number
  offsetY: number
  stageWidth: number
  stageHeight: number
}

export function computeStage(screen: Screen, viewport: Viewport): StageMetrics {
  const designW = Number(screen.width) || viewport.width
  const designH = Number(screen.height) || viewport.height
  const mode = screen.config?.adaptationMode || 'scale'

  let scale: number
  if (mode === 'fixed-width') scale = viewport.width / designW
  else if (mode === 'responsive') scale = 1
  else scale = Math.min(viewport.width / designW, viewport.height / designH)

  if (!Number.isFinite(scale) || scale <= 0) scale = 1

  const stageWidth = designW * scale
  const stageHeight = designH * scale
  return {
    scale,
    offsetX: Math.max(0, (viewport.width - stageWidth) / 2),
    offsetY: Math.max(0, (viewport.height - stageHeight) / 2),
    stageWidth,
    stageHeight,
  }
}

/** 组件盒子（设备像素，绝对定位用） */
export function componentBox(comp: ScreenComponent, m: StageMetrics): {
  left: number
  top: number
  width: number
  height: number
  zIndex: number
} {
  const px = (v: number) => Math.max(0, Math.round((Number(v) || 0) * m.scale))
  return {
    left: px(comp.x) + m.offsetX,
    top: px(comp.y) + m.offsetY,
    width: px(comp.w),
    height: px(comp.h),
    zIndex: Number(comp.zIndex) || 1,
  }
}

/** 字号：按缩放取整，并留 9px 下限（手机上再小就不可读） */
export function scaledFontSize(designPx: number, scale: number, min = 9): number {
  return Math.max(min, Math.round((Number(designPx) || 12) * scale))
}
