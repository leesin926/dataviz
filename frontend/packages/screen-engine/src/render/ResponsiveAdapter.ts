/**
 * 响应式适配 (根据视口动态调整组件位置/尺寸)
 */
export class ResponsiveAdapter {
  private breakpointMap: Record<string, { cols: number; scale: number }> = {
    xs: { cols: 4, scale: 0.5 },
    sm: { cols: 8, scale: 0.7 },
    md: { cols: 12, scale: 0.85 },
    lg: { cols: 16, scale: 1 },
    xl: { cols: 24, scale: 1 },
  }

  /**
   * 根据视口宽度获取当前断点
   */
  getCurrentBreakpoint(width: number): string {
    if (width < 576) return 'xs'
    if (width < 768) return 'sm'
    if (width < 992) return 'md'
    if (width < 1200) return 'lg'
    return 'xl'
  }

  /**
   * 根据断点返回 cols / scale
   */
  getConfig(width: number): { cols: number; scale: number } {
    const bp = this.getCurrentBreakpoint(width)
    return this.breakpointMap[bp]
  }

  /**
   * 计算响应式尺寸
   */
  computeSize(designWidth: number, designHeight: number, viewportWidth: number): { width: number; height: number; scale: number } {
    const { scale } = this.getConfig(viewportWidth)
    return {
      width: designWidth * scale,
      height: designHeight * scale,
      scale,
    }
  }
}
