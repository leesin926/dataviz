import * as echarts from 'echarts'
import type { ECharts, EChartsOption } from 'echarts'
import type { ThemeName } from './ChartThemeManager'
import { ChartThemeManager } from './ChartThemeManager'

/**
 * ChartRenderer - 封装 ECharts 核心渲染逻辑
 * 提供 init、setOption、resize、dispose 等方法
 */
export class ChartRenderer {
  private instance: ECharts | null = null
  private container: HTMLElement
  private themeManager: ChartThemeManager
  private currentTheme: ThemeName = 'light'
  private resizeObserver: ResizeObserver | null = null

  constructor(container: HTMLElement, theme: ThemeName = 'light') {
    this.container = container
    this.themeManager = new ChartThemeManager()
    this.currentTheme = theme
    this.initInstance()
  }

  /**
   * 初始化 ECharts 实例
   */
  private initInstance(): void {
    if (this.instance) {
      this.instance.dispose()
    }
    this.instance = echarts.init(this.container, this.currentTheme)
  }

  /**
   * 设置图表配置
   */
  setOption(option: EChartsOption, notMerge = false, lazyUpdate = false): void {
    if (!this.instance) {
      throw new Error('ChartRenderer: instance not initialized')
    }
    this.instance.setOption(option, notMerge, lazyUpdate)
  }

  /**
   * 切换主题
   */
  setTheme(theme: ThemeName): void {
    if (this.currentTheme === theme) return
    this.currentTheme = theme
    this.initInstance()
  }

  /**
   * 获取当前主题
   */
  getTheme(): ThemeName {
    return this.currentTheme
  }

  /**
   * 调整图表尺寸
   */
  resize(opts?: { width?: number; height?: number; silent?: boolean }): void {
    this.instance?.resize(opts)
  }

  /**
   * 销毁实例
   */
  dispose(): void {
    this.removeResizeObserver()
    this.instance?.dispose()
    this.instance = null
  }

  /**
   * 获取 ECharts 实例
   */
  getInstance(): ECharts | null {
    return this.instance
  }

  /**
   * 绑定事件
   */
  on(eventName: string, handler: (params: unknown) => void): void {
    this.instance?.on(eventName, handler)
  }

  /**
   * 解绑事件
   */
  off(eventName: string, handler?: (params: unknown) => void): void {
    this.instance?.off(eventName, handler)
  }

  /**
   * 显示加载动画
   */
  showLoading(opts?: Record<string, unknown>): void {
    this.instance?.showLoading(opts)
  }

  /**
   * 隐藏加载动画
   */
  hideLoading(): void {
    this.instance?.hideLoading()
  }

  /**
   * 清空图表
   */
  clear(): void {
    this.instance?.clear()
  }

  /**
   * 导出图片
   */
  getDataURL(opts?: { type?: 'png' | 'jpeg' | 'svg'; pixelRatio?: number; backgroundColor?: string }): string {
    if (!this.instance) return ''
    return this.instance.getDataURL(opts)
  }

  /**
   * 启用自动 resize 监听
   */
  enableAutoResize(): void {
    if (this.resizeObserver) return
    this.resizeObserver = new ResizeObserver(() => {
      this.resize()
    })
    this.resizeObserver.observe(this.container)
  }

  /**
   * 移除 resize 监听
   */
  private removeResizeObserver(): void {
    if (this.resizeObserver) {
      this.resizeObserver.disconnect()
      this.resizeObserver = null
    }
  }
}
