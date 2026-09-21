/**
 * 颜色工具
 */

export interface RGBA {
  r: number
  g: number
  b: number
  a: number
}

export interface HSLA {
  h: number
  s: number
  l: number
  a: number
}

/**
 * hex 转 RGBA
 */
export function hexToRgba(hex: string): RGBA {
  const clean = hex.replace('#', '')
  const full =
    clean.length === 3
      ? clean
          .split('')
          .map((c) => c + c)
          .join('')
      : clean
  const bigint = parseInt(full, 16)
  return {
    r: (bigint >> 16) & 255,
    g: (bigint >> 8) & 255,
    b: bigint & 255,
    a: 1,
  }
}

/**
 * RGBA 转 hex
 */
export function rgbaToHex(rgba: RGBA): string {
  const toHex = (v: number) => Math.max(0, Math.min(255, Math.round(v))).toString(16).padStart(2, '0')
  return `#${toHex(rgba.r)}${toHex(rgba.g)}${toHex(rgba.b)}`
}

/**
 * RGBA 转 HSLA
 */
export function rgbaToHsla(rgba: RGBA): HSLA {
  const r = rgba.r / 255
  const g = rgba.g / 255
  const b = rgba.b / 255
  const max = Math.max(r, g, b)
  const min = Math.min(r, g, b)
  let h = 0
  let s = 0
  const l = (max + min) / 2
  if (max !== min) {
    const d = max - min
    s = l > 0.5 ? d / (2 - max - min) : d / (max + min)
    switch (max) {
      case r:
        h = ((g - b) / d + (g < b ? 6 : 0)) / 6
        break
      case g:
        h = ((b - r) / d + 2) / 6
        break
      case b:
        h = ((r - g) / d + 4) / 6
        break
    }
  }
  return { h: h * 360, s: s * 100, l: l * 100, a: rgba.a }
}

/**
 * 调节颜色亮度
 */
export function adjustBrightness(hex: string, amount: number): string {
  const rgba = hexToRgba(hex)
  rgba.r = Math.max(0, Math.min(255, rgba.r + amount))
  rgba.g = Math.max(0, Math.min(255, rgba.g + amount))
  rgba.b = Math.max(0, Math.min(255, rgba.b + amount))
  return rgbaToHex(rgba)
}

/**
 * 生成渐变颜色数组
 */
export function generateGradient(startHex: string, endHex: string, steps: number): string[] {
  const start = hexToRgba(startHex)
  const end = hexToRgba(endHex)
  const result: string[] = []
  for (let i = 0; i < steps; i++) {
    const t = i / (steps - 1)
    const rgba: RGBA = {
      r: start.r + (end.r - start.r) * t,
      g: start.g + (end.g - start.g) * t,
      b: start.b + (end.b - start.b) * t,
      a: 1,
    }
    result.push(rgbaToHex(rgba))
  }
  return result
}

/**
 * 默认调色板 (ECharts 风格)
 */
export const DEFAULT_PALETTE = [
  '#5470c6',
  '#91cc75',
  '#fac858',
  '#ee6666',
  '#73c0de',
  '#3ba272',
  '#fc8452',
  '#9a60b4',
  '#ea7ccc',
]

/**
 * 按索引取色 (循环)
 */
export function getColorByIndex(index: number, palette: string[] = DEFAULT_PALETTE): string {
  return palette[index % palette.length]
}
