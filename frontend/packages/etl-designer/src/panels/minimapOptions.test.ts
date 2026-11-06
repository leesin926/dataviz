import { describe, expect, it } from 'vitest'
import {
  MINIMAP_FALLBACK_SIZE,
  MINIMAP_MIN_SIZE,
  MINIMAP_PADDING,
  buildMiniMapOptions,
  resolveMiniMapSize,
} from './minimapOptions'

describe('resolveMiniMapSize', () => {
  it('原样采用已布局的容器尺寸', () => {
    expect(resolveMiniMapSize({ width: 178, height: 118 })).toEqual({ width: 178, height: 118 })
  })

  it('四舍五入到整像素，避免插件容器出现小数边框', () => {
    expect(resolveMiniMapSize({ width: 178.6, height: 118.2 })).toEqual({ width: 179, height: 118 })
  })

  it('面板尚未布局（宽高为 0）时退回设计尺寸，而不是算出 NaN 比例', () => {
    expect(resolveMiniMapSize({ width: 0, height: 0 })).toEqual(MINIMAP_FALLBACK_SIZE)
  })

  it('缺失 / 负值 / 非有限值同样退回设计尺寸', () => {
    expect(resolveMiniMapSize(null)).toEqual(MINIMAP_FALLBACK_SIZE)
    expect(resolveMiniMapSize(undefined)).toEqual(MINIMAP_FALLBACK_SIZE)
    expect(resolveMiniMapSize({})).toEqual(MINIMAP_FALLBACK_SIZE)
    expect(resolveMiniMapSize({ width: -40, height: -1 })).toEqual(MINIMAP_FALLBACK_SIZE)
    expect(resolveMiniMapSize({ width: Number.NaN, height: Number.POSITIVE_INFINITY })).toEqual(
      MINIMAP_FALLBACK_SIZE,
    )
  })

  it('低于最小可读尺寸时夹紧，而不是画成一个点', () => {
    expect(resolveMiniMapSize({ width: 30, height: 20 })).toEqual(MINIMAP_MIN_SIZE)
  })

  it('只夹紧更小的那一轴，另一轴保持测量值', () => {
    const size = resolveMiniMapSize({ width: 240, height: 12 })
    expect(size).toEqual({ width: 240, height: MINIMAP_MIN_SIZE.height })
  })
})

describe('buildMiniMapOptions', () => {
  const container = document.createElement('div')

  it('组装出插件构造函数接受的选项形状', () => {
    expect(buildMiniMapOptions(container, { width: 180, height: 120 })).toEqual({
      container,
      width: 180,
      height: 120,
      padding: MINIMAP_PADDING,
      scalable: true,
    })
  })

  it('容器引用原样透传（插件把 widget 挂到这个节点上，卸载时才由插件移除）', () => {
    expect(buildMiniMapOptions(container, null).container).toBe(container)
  })

  it('padding 含在 width 之内，所以尺寸不能被 padding 吃掉', () => {
    const options = buildMiniMapOptions(container, MINIMAP_MIN_SIZE)
    expect(options.width).toBeGreaterThanOrEqual(MINIMAP_MIN_SIZE.width)
    expect(options.height).toBeGreaterThanOrEqual(MINIMAP_MIN_SIZE.height)
    expect(options.padding).toBeLessThan(options.height)
  })

  it('未布局时给出的仍是可用的兜底尺寸（配合 scalable 保留缩放手柄）', () => {
    const options = buildMiniMapOptions(container, { width: 0, height: 0 })
    expect(options).toMatchObject({ width: 180, height: 120, scalable: true })
  })
})
