import * as echarts from 'echarts'
import type { EChartsOption } from 'echarts'

/**
 * 主题名称
 */
export type ThemeName = 'light' | 'dark' | 'brand' | string

/**
 * 主题配置
 */
export interface ThemeConfig {
  name: ThemeName
  backgroundColor?: string
  textStyle?: {
    fontFamily?: string
    color?: string
  }
  colorPalette?: string[]
  tooltip?: {
    backgroundColor?: string
    borderColor?: string
    textStyle?: {
      color?: string
    }
  }
}

/**
 * 内置主题配置
 */
const BUILTIN_THEMES: Record<string, ThemeConfig> = {
  light: {
    name: 'light',
    backgroundColor: '#ffffff',
    textStyle: {
      fontFamily: 'PingFang SC, Microsoft YaHei, sans-serif',
      color: '#333333',
    },
    colorPalette: [
      '#5470c6', '#91cc75', '#fac858', '#ee6666', '#73c0de',
      '#3ba272', '#fc8452', '#9a60b4', '#ea7ccc',
    ],
    tooltip: {
      backgroundColor: 'rgba(255,255,255,0.96)',
      borderColor: '#eeeeee',
      textStyle: { color: '#333333' },
    },
  },
  dark: {
    name: 'dark',
    backgroundColor: '#141414',
    textStyle: {
      fontFamily: 'PingFang SC, Microsoft YaHei, sans-serif',
      color: '#dddddd',
    },
    colorPalette: [
      '#5470c6', '#91cc75', '#fac858', '#ee6666', '#73c0de',
      '#3ba272', '#fc8452', '#9a60b4', '#ea7ccc',
    ],
    tooltip: {
      backgroundColor: 'rgba(20,20,20,0.96)',
      borderColor: '#333333',
      textStyle: { color: '#dddddd' },
    },
  },
  brand: {
    name: 'brand',
    backgroundColor: '#0a1929',
    textStyle: {
      fontFamily: 'PingFang SC, Microsoft YaHei, sans-serif',
      color: '#e0e6ed',
    },
    colorPalette: [
      '#3aa2ff', '#4dd6a8', '#ffb547', '#ff6b6b', '#9d7cff',
      '#3ad3e6', '#ff9f43', '#e84393', '#00cec9',
    ],
    tooltip: {
      backgroundColor: 'rgba(10,25,41,0.96)',
      borderColor: '#1e3a5f',
      textStyle: { color: '#e0e6ed' },
    },
  },
}

/**
 * ChartThemeManager - 图表主题管理器
 * 负责主题注册、切换、获取
 */
export class ChartThemeManager {
  private themes: Map<string, ThemeConfig> = new Map()
  private currentTheme: ThemeName = 'light'

  constructor() {
    // 注册内置主题
    Object.values(BUILTIN_THEMES).forEach((theme) => {
      this.registerTheme(theme)
    })
  }

  /**
   * 注册自定义主题
   */
  registerTheme(config: ThemeConfig): void {
    this.themes.set(config.name, config)
    // 同步注册到 ECharts
    const echartsTheme = this.buildEChartsTheme(config)
    echarts.registerTheme(config.name, echartsTheme)
  }

  /**
   * 获取主题配置
   */
  getTheme(name: ThemeName): ThemeConfig | undefined {
    return this.themes.get(name)
  }

  /**
   * 获取当前主题
   */
  getCurrentTheme(): ThemeName {
    return this.currentTheme
  }

  /**
   * 设置当前主题
   */
  setCurrentTheme(name: ThemeName): void {
    this.currentTheme = name
  }

  /**
   * 切换主题
   */
  switchTheme(name: ThemeName): ThemeConfig | undefined {
    const theme = this.themes.get(name)
    if (theme) {
      this.currentTheme = name
    }
    return theme
  }

  /**
   * 获取所有已注册主题名称
   */
  getThemeNames(): string[] {
    return Array.from(this.themes.keys())
  }

  /**
   * 构建 ECharts 主题对象
   */
  private buildEChartsTheme(config: ThemeConfig): EChartsOption {
    return {
      backgroundColor: config.backgroundColor || '#ffffff',
      textStyle: config.textStyle || {},
      color: config.colorPalette || [],
      tooltip: {
        backgroundColor: config.tooltip?.backgroundColor || 'rgba(255,255,255,0.96)',
        borderColor: config.tooltip?.borderColor || '#eeeeee',
        textStyle: config.tooltip?.textStyle || { color: '#333333' },
      },
    }
  }
}
