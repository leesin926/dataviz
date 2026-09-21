import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { getLocal, setLocal } from '@dataviz/shared-utils'

export interface AppSettings {
  theme: 'light' | 'dark'
  language: 'zh-CN' | 'en-US'
  sidebarCollapsed: boolean
  primaryColor: string
  showBreadcrumb: boolean
  showLogo: boolean
  fixedHeader: boolean
}

export const useAppStore = defineStore('app', () => {
  const settings = ref<AppSettings>({
    theme: 'light',
    language: 'zh-CN',
    sidebarCollapsed: false,
    primaryColor: '#2f7cff',
    showBreadcrumb: true,
    showLogo: true,
    fixedHeader: true,
  })

  const sidebarCollapsed = computed(() => settings.value.sidebarCollapsed)
  const theme = computed(() => settings.value.theme)

  function initialize() {
    const saved = getLocal<Partial<AppSettings>>('app_settings')
    if (saved) {
      settings.value = { ...settings.value, ...saved }
    }
    applyTheme(settings.value.theme)
  }

  /** 暗色：html[data-dv-theme='dark'] 命中 shared-styles 令牌，同时开 Element Plus 的 .dark */
  function applyTheme(theme: 'light' | 'dark') {
    const el = document.documentElement
    if (theme === 'dark') {
      el.setAttribute('data-dv-theme', 'dark')
      el.classList.add('dark')
    } else {
      el.removeAttribute('data-dv-theme')
      el.classList.remove('dark')
    }
  }

  function toggleSidebar() {
    settings.value.sidebarCollapsed = !settings.value.sidebarCollapsed
    persist()
  }

  function setTheme(theme: 'light' | 'dark') {
    settings.value.theme = theme
    applyTheme(theme)
    persist()
  }

  function setLanguage(lang: 'zh-CN' | 'en-US') {
    settings.value.language = lang
    persist()
  }

  function setPrimaryColor(color: string) {
    settings.value.primaryColor = color
    document.documentElement.style.setProperty('--el-color-primary', color)
    persist()
  }

  function persist() {
    setLocal('app_settings', settings.value)
  }

  return {
    settings,
    sidebarCollapsed,
    theme,
    initialize,
    applyTheme,
    toggleSidebar,
    setTheme,
    setLanguage,
    setPrimaryColor,
  }
})
