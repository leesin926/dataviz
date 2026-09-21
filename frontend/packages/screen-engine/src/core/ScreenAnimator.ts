import type { ScreenAnimation } from '@dataviz/shared-types'

/**
 * 动画类型
 */
export type AnimationType = 'fade' | 'slide' | 'scale' | 'rotate' | 'bounce' | 'flash'

/**
 * 动画配置
 */
export interface AnimationConfig {
  type: AnimationType
  duration: number
  delay: number
  iteration?: number | 'infinite'
  easing?: string
}

/**
 * ScreenAnimator - 大屏动画控制器
 * 负责动画播放、过渡效果管理
 */
export class ScreenAnimator {
  private animations: Map<string, AnimationConfig[]> = new Map()
  private activeAnimations: Set<string> = new Set()
  private animationFrameId: number | null = null

  /**
   * 注册组件动画
   */
  registerAnimation(componentId: string, animations: ScreenAnimation[]): void {
    const configs: AnimationConfig[] = animations.map((a) => ({
      type: a.type,
      duration: a.duration,
      delay: a.delay,
      iteration: a.iteration,
      easing: a.easing,
    }))
    this.animations.set(componentId, configs)
  }

  /**
   * 移除组件动画
   */
  removeAnimation(componentId: string): void {
    this.animations.delete(componentId)
    this.activeAnimations.delete(componentId)
  }

  /**
   * 播放组件动画
   */
  playAnimation(componentId: string): void {
    const configs = this.animations.get(componentId)
    if (!configs || configs.length === 0) return

    this.activeAnimations.add(componentId)
  }

  /**
   * 停止组件动画
   */
  stopAnimation(componentId: string): void {
    this.activeAnimations.delete(componentId)
  }

  /**
   * 播放所有动画
   */
  playAll(): void {
    this.animations.forEach((_, id) => {
      this.playAnimation(id)
    })
  }

  /**
   * 停止所有动画
   */
  stopAll(): void {
    this.activeAnimations.clear()
  }

  /**
   * 获取组件动画 CSS
   */
  getAnimationStyle(componentId: string): Record<string, string> {
    if (!this.activeAnimations.has(componentId)) {
      return {}
    }

    const configs = this.animations.get(componentId)
    if (!configs || configs.length === 0) return {}

    const animations = configs.map((config) => {
      const name = this.getAnimationName(config.type)
      const iteration = config.iteration === 'infinite' ? 'infinite' : config.iteration || 1
      const easing = config.easing || 'ease'
      return `${name} ${config.duration}ms ${easing} ${config.delay}ms ${iteration}`
    })

    return {
      animation: animations.join(', '),
    }
  }

  /**
   * 获取 CSS keyframes
   */
  getKeyframes(): string {
    return `
      @keyframes fade {
        from { opacity: 0; }
        to { opacity: 1; }
      }
      @keyframes slide {
        from { transform: translateY(20px); opacity: 0; }
        to { transform: translateY(0); opacity: 1; }
      }
      @keyframes scale {
        from { transform: scale(0.8); opacity: 0; }
        to { transform: scale(1); opacity: 1; }
      }
      @keyframes rotate {
        from { transform: rotate(-10deg); opacity: 0; }
        to { transform: rotate(0); opacity: 1; }
      }
      @keyframes bounce {
        0%, 20%, 50%, 80%, 100% { transform: translateY(0); }
        40% { transform: translateY(-10px); }
        60% { transform: translateY(-5px); }
      }
      @keyframes flash {
        0%, 50%, 100% { opacity: 1; }
        25%, 75% { opacity: 0; }
      }
    `
  }

  /**
   * 获取动画名称
   */
  private getAnimationName(type: AnimationType): string {
    const names: Record<AnimationType, string> = {
      fade: 'fade',
      slide: 'slide',
      scale: 'scale',
      rotate: 'rotate',
      bounce: 'bounce',
      flash: 'flash',
    }
    return names[type] || 'fade'
  }

  /**
   * 销毁动画控制器
   */
  dispose(): void {
    if (this.animationFrameId !== null) {
      cancelAnimationFrame(this.animationFrameId)
    }
    this.animations.clear()
    this.activeAnimations.clear()
  }
}
