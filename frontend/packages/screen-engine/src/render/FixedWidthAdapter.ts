/**
 * 固定宽度适配 (宽度撑满,高度按比例)
 */
export class FixedWidthAdapter {
  /**
   * 应用固定宽度适配
   */
  static apply(container: HTMLElement, designWidth: number): () => void {
    const onResize = () => {
      const vw = container.parentElement?.clientWidth || window.innerWidth
      const scale = vw / designWidth
      container.style.transform = `scale(${scale})`
      container.style.transformOrigin = 'top left'
      container.style.width = `${designWidth}px`
      // 同时设置父容器高度,避免滚动条
      if (container.parentElement) {
        const ch = parseFloat(getComputedStyle(container).height)
        container.parentElement.style.height = `${ch * scale}px`
      }
    }
    onResize()
    window.addEventListener('resize', onResize)
    return () => window.removeEventListener('resize', onResize)
  }
}
