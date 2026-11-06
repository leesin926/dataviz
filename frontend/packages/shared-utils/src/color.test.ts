import { describe, expect, it } from 'vitest'
import {
  DEFAULT_PALETTE,
  adjustBrightness,
  generateGradient,
  getColorByIndex,
  hexToRgba,
  rgbaToHex,
  rgbaToHsla,
} from './color'

describe('shared-utils/color —— hex 与 RGBA/HSLA 互转', () => {
  it('三位缩写 hex 按位翻倍展开：#fff → 255/255/255 且 alpha 恒为 1', () => {
    expect(hexToRgba('#fff')).toEqual({ r: 255, g: 255, b: 255, a: 1 })
  })

  it('六位 hex 可省略 #，并按字节拆出 RGB', () => {
    expect(hexToRgba('ff8000')).toEqual({ r: 255, g: 128, b: 0, a: 1 })
  })

  it('非法 hex 静默解析成纯黑，不报错也不返回 null（调用方拿不到任何异常信号）', () => {
    expect(hexToRgba('#zzzzzz')).toEqual({ r: 0, g: 0, b: 0, a: 1 })
    expect(hexToRgba('')).toEqual({ r: 0, g: 0, b: 0, a: 1 })
  })

  it('rgbaToHex 对越界分量先四舍五入再钳到 0-255，单位数补零', () => {
    expect(rgbaToHex({ r: -20, g: 300, b: 9, a: 1 })).toBe('#00ff09')
    expect(rgbaToHex({ r: 127.6, g: 0, b: 0, a: 1 })).toBe('#800000')
  })

  it('rgbaToHsla 输出 0-360 的色相与百分比化的饱和度/亮度', () => {
    expect(rgbaToHsla({ r: 255, g: 0, b: 0, a: 1 })).toEqual({ h: 0, s: 100, l: 50, a: 1 })
    expect(rgbaToHsla({ r: 0, g: 255, b: 0, a: 1 })).toEqual({ h: 120, s: 100, l: 50, a: 1 })
    expect(rgbaToHsla({ r: 128, g: 128, b: 128, a: 1 })).toEqual({ h: 0, s: 0, l: 50.19607843137255, a: 1 })
  })

  it('adjustBrightness 三通道等量平移并各自钳位', () => {
    expect(adjustBrightness('#808080', 40)).toBe('#a8a8a8')
    expect(adjustBrightness('#f0f0f0', 40)).toBe('#ffffff')
    expect(adjustBrightness('#0a0a0a', -20)).toBe('#000000')
  })
})

describe('shared-utils/color —— 渐变色带与取色', () => {
  it('generateGradient 端点精确等于起止色，中间按 i/(steps-1) 线性插值', () => {
    expect(generateGradient('#000000', '#ffffff', 5)).toEqual([
      '#000000',
      '#404040',
      '#808080',
      '#bfbfbf',
      '#ffffff',
    ])
  })

  it('generateGradient 的 steps=1 返回起始色端点本身，不再除以零算出 #NaNNaNNaN（API-148）', () => {
    // given：单点图例/单色系列是最常见的 steps=1 场景
    expect(generateGradient('#000000', '#ffffff', 1)).toEqual(['#000000'])
    // steps<=0 没有可生成的色
    expect(generateGradient('#000000', '#ffffff', 0)).toEqual([])
  })

  it('getColorByIndex 超出调色板长度时回绕，而不是越界取空', () => {
    expect(getColorByIndex(0)).toBe('#5470c6')
    expect(DEFAULT_PALETTE).toHaveLength(9)
    expect(getColorByIndex(9)).toBe('#5470c6')
    expect(getColorByIndex(11)).toBe('#fac858')
  })

  it('getColorByIndex 负索引按长度回绕（Python 风格），不再返回 undefined（API-148）', () => {
    expect(getColorByIndex(-1)).toBe('#ea7ccc')
    expect(getColorByIndex(-9)).toBe('#5470c6')
    expect(getColorByIndex(-10)).toBe('#ea7ccc')
    // 空调色板维持既有语义：越界/空表仍取到 undefined
    expect(getColorByIndex(0, [])).toBeUndefined()
  })
})
