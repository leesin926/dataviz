/**
 * 网格吸附逻辑
 */
export class GridSnap {
  private size: number

  constructor(size = 8) {
    this.size = size
  }

  setSize(size: number): void {
    this.size = Math.max(1, size)
  }

  getSize(): number {
    return this.size
  }

  /**
   * 吸附到网格
   */
  snap(value: number): number {
    return Math.round(value / this.size) * this.size
  }

  /**
   * 吸附坐标
   */
  snapPoint(x: number, y: number): { x: number; y: number } {
    return {
      x: this.snap(x),
      y: this.snap(y),
    }
  }

  /**
   * 吸附尺寸
   */
  snapSize(w: number, h: number): { w: number; h: number } {
    return {
      w: Math.max(this.size, this.snap(w)),
      h: Math.max(this.size, this.snap(h)),
    }
  }

  /**
   * 吸附时辅助对齐线计算
   */
  computeAlignLines(
    moving: { x: number; y: number; w: number; h: number },
    others: Array<{ x: number; y: number; w: number; h: number }>,
    threshold = 4,
  ): { vertical: number[]; horizontal: number[] } {
    const vertical: number[] = []
    const horizontal: number[] = []
    const movingCenterX = moving.x + moving.w / 2
    const movingCenterY = moving.y + moving.h / 2
    for (const o of others) {
      const ocx = o.x + o.w / 2
      const ocy = o.y + o.h / 2
      if (Math.abs(movingCenterX - ocx) <= threshold) vertical.push(ocx)
      if (Math.abs(movingCenterY - ocy) <= threshold) horizontal.push(ocy)
      if (Math.abs(moving.x - o.x) <= threshold) vertical.push(o.x)
      if (Math.abs(moving.y - o.y) <= threshold) horizontal.push(o.y)
      if (Math.abs(moving.x + moving.w - (o.x + o.w)) <= threshold) vertical.push(o.x + o.w)
      if (Math.abs(moving.y + moving.h - (o.y + o.h)) <= threshold) horizontal.push(o.y + o.h)
    }
    return { vertical, horizontal }
  }
}
