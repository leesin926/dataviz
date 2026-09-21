/**
 * 缩放适配模式 (等比缩放整个大屏)
 */
export class ScaleAdapter {
  private resizeObserver: ResizeObserver | null = null

  /**
   * 将目标尺寸容器按缩放方式适配到视口
   */
  static adapt(
    container: HTMLElement,
    designWidth: number,
    designHeight: number,
    mode: 'scale' | 'fixed-width' | 'responsive' = 'scale',
  ): void {
    if (mode === 'scale') {
      ScaleAdapter.scaleFit(container, designWidth, designHeight)
    } else if (mode === 'fixed-width') {
      ScaleAdapter.fixedWidthFit(container, designWidth)
    } else {
      // responsive: 默认不做处理
    }
  }

  /**
   * 等比缩放 (保持宽高比)
   */
  private static scaleFit(container: HTMLElement, designWidth: number, designHeight: number): void {
    const apply = () => {
      const vw = container.parentElement?.clientWidth || window.innerWidth
      const vh = container.parentElement?.clientHeight || window.innerHeight
      const scaleX = vw / designWidth
      const scaleY = vh / designHeight
      const scale = Math.min(scaleX, scaleY)
      container.style.transform = `scale(${scale})`
      container.style.transformOrigin = 'top left'
      container.style.width = `${designWidth}px`
      container.style.height = `${designHeight}px`
    }
    apply()
    window.addEventListener('resize', apply)
  }

  /**
   * 固定宽度 (只缩放高度)
   */
  private static fixedWidthFit(container: HTMLElement, designWidth: number): void {
    const apply = () => {
      const vw = container.parentElement?.clientWidth || window.innerWidth
      const scale = vw / designWidth
      container.style.transform = `scale(${scale})`
      container.style.transformOrigin = 'top left'
      container.style.width = `${designWidth}px`
    }
    apply()
    window.addEventListener('resize', apply)
  }

  /**
   * 监听容器尺寸变化
   */
  observe(container: HTMLElement, callback: () => void): void {
    this.resizeObserver = new ResizeObserver(callback)
    this.resizeObserver.observe(container)
  }

  dispose(): void {
    this.resizeObserver?.disconnect()
    this.resizeObserver = null
  }
}
