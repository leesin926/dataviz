/**
 * 小地图插件参数的纯函数部分。
 *
 * 单独成模块的原因：X6 的 `MiniMap` 需要真实布局才能实例化（happy-dom 里 clientWidth 恒为 0），
 * 而"面板给了多大的盒子、插件就该画多大"这一步是可判定对错的算术，把它抽出来才能在没有浏览器的
 * 情况下被测试覆盖。
 */

/** `.etl-minimap` 面板的设计尺寸：容器尚未布局（宽或高为 0）时退化到它 */
export const MINIMAP_FALLBACK_SIZE = { width: 180, height: 120 }

/** 小于这个尺寸节点方块就糊成一片，按最小值兜底（面板 overflow: hidden 负责裁掉多余部分） */
export const MINIMAP_MIN_SIZE = { width: 80, height: 60 }

/** 插件容器内边距；`.x6-widget-minimap` 自带 box-sizing: border-box，所以含在 width/height 之内 */
export const MINIMAP_PADDING = 6

export interface MiniMapSize {
  width: number
  height: number
}

/**
 * 传给 `new MiniMap()` 的选项。
 * x6 v3 把插件并进了主包但没有把 `MiniMapOptions` 透出根入口（`plugin/minimap/type.d.ts` 内部类型），
 * 这里按构造函数签名 `Partial<MiniMapOptions>` 声明结构等价的子集，避免 deep-import 包内路径。
 */
export interface MiniMapPluginOptions {
  container: HTMLElement
  width: number
  height: number
  padding: number
  scalable: boolean
}

/**
 * 把测得的面板尺寸规整成插件可用的像素尺寸：
 * 非有限值 / 非正值（未布局、display:none）退回设计尺寸，其余取整并夹紧到最小可读尺寸。
 */
export function resolveMiniMapSize(size: Partial<MiniMapSize> | null | undefined): MiniMapSize {
  return {
    width: clampSize(size?.width, MINIMAP_FALLBACK_SIZE.width, MINIMAP_MIN_SIZE.width),
    height: clampSize(size?.height, MINIMAP_FALLBACK_SIZE.height, MINIMAP_MIN_SIZE.height),
  }
}

/**
 * 组装插件选项。`scalable: true` 保留右下角手柄，拖它等比缩放主画布；
 * 点小地图任意位置居中、拖视口平移主画布都由插件自身处理。
 */
export function buildMiniMapOptions(
  container: HTMLElement,
  size: Partial<MiniMapSize> | null,
): MiniMapPluginOptions {
  const resolved = resolveMiniMapSize(size)
  return {
    container,
    width: resolved.width,
    height: resolved.height,
    padding: MINIMAP_PADDING,
    scalable: true,
  }
}

function clampSize(value: number | undefined, fallback: number, min: number): number {
  return Math.max(toPixels(value, fallback), min)
}

/** 非有限值 / 非正值（未布局、display:none）退回兜底尺寸，其余取整 */
function toPixels(value: number | undefined, fallback: number): number {
  if (typeof value !== 'number' || !Number.isFinite(value) || value <= 0) return fallback
  return Math.round(value)
}
