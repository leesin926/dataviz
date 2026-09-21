/**
 * ScreenLayout - 大屏布局计算系统
 * 负责网格布局、响应式缩放、rem 计算
 */
export class ScreenLayout {
  private designWidth: number
  private designHeight: number
  private containerWidth: number = 0
  private containerHeight: number = 0
  private scaleMode: 'scale' | 'fixed-width' | 'responsive' = 'scale'

  constructor(designWidth = 1920, designHeight = 1080) {
    this.designWidth = designWidth
    this.designHeight = designHeight
  }

  /**
   * 设置容器尺寸
   */
  setContainerSize(width: number, height: number): void {
    this.containerWidth = width
    this.containerHeight = height
  }

  /**
   * 设置缩放模式
   */
  setScaleMode(mode: 'scale' | 'fixed-width' | 'responsive'): void {
    this.scaleMode = mode
  }

  /**
   * 计算缩放比例
   */
  calculateScale(): { scaleX: number; scaleY: number; scale: number } {
    if (this.containerWidth === 0 || this.containerHeight === 0) {
      return { scaleX: 1, scaleY: 1, scale: 1 }
    }

    const scaleX = this.containerWidth / this.designWidth
    const scaleY = this.containerHeight / this.designHeight

    switch (this.scaleMode) {
      case 'scale':
        // 等比缩放，取较小值保证完整显示
        return { scaleX, scaleY, scale: Math.min(scaleX, scaleY) }
      case 'fixed-width':
        // 固定宽度，高度自适应
        return { scaleX, scaleY: scaleX, scale: scaleX }
      case 'responsive':
        // 响应式，独立缩放
        return { scaleX, scaleY, scale: 1 }
      default:
        return { scaleX, scaleY, scale: Math.min(scaleX, scaleY) }
    }
  }

  /**
   * 计算 rem 基准值
   */
  calculateRemBase(): number {
    const { scale } = this.calculateScale()
    return 100 * scale
  }

  /**
   * 将设计稿坐标转换为实际坐标
   */
  transformPosition(x: number, y: number): { x: number; y: number } {
    const { scaleX, scaleY } = this.calculateScale()
    return {
      x: x * scaleX,
      y: y * scaleY,
    }
  }

  /**
   * 将设计稿尺寸转换为实际尺寸
   */
  transformSize(width: number, height: number): { width: number; height: number } {
    const { scaleX, scaleY } = this.calculateScale()
    return {
      width: width * scaleX,
      height: height * scaleY,
    }
  }

  /**
   * 获取 CSS transform 字符串
   */
  getTransformString(): string {
    const { scale } = this.calculateScale()
    return `scale(${scale})`
  }

  /**
   * 获取容器样式
   */
  getContainerStyle(): Record<string, string> {
    const { scaleX, scaleY } = this.calculateScale()

    if (this.scaleMode === 'responsive') {
      return {
        width: `${this.containerWidth}px`,
        height: `${this.containerHeight}px`,
      }
    }

    return {
      width: `${this.designWidth}px`,
      height: `${this.designHeight}px`,
      transform: `scale(${scaleX}, ${scaleY})`,
      transformOrigin: 'left top',
    }
  }

  /**
   * 计算网格布局
   */
  calculateGridLayout(
    cols: number,
    rows: number,
    gap: number = 0
  ): Array<{ x: number; y: number; width: number; height: number }> {
    const cellWidth = (this.designWidth - gap * (cols - 1)) / cols
    const cellHeight = (this.designHeight - gap * (rows - 1)) / rows
    const layout: Array<{ x: number; y: number; width: number; height: number }> = []

    for (let row = 0; row < rows; row++) {
      for (let col = 0; col < cols; col++) {
        layout.push({
          x: col * (cellWidth + gap),
          y: row * (cellHeight + gap),
          width: cellWidth,
          height: cellHeight,
        })
      }
    }

    return layout
  }

  /**
   * 获取设计稿尺寸
   */
  getDesignSize(): { width: number; height: number } {
    return { width: this.designWidth, height: this.designHeight }
  }
}
