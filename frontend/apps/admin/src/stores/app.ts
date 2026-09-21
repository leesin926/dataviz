import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { getLocal, setLocal } from '@dataviz/shared-utils'

export interface AdminSettings {
  theme: 'light' | 'dark'
  sidebarCollapsed: boolean
}

export const useAppStore = defineStore('admin-app', () => {
  const settings = ref<AdminSettings>({ theme: 'light', sidebarCollapsed: false })

  const sidebarCollapsed = computed(() => settings.value.sidebarCollapsed)
  const theme = computed(() => settings.value.theme)

  /** 暗色：html[data-dv-theme='dark'] 命中 shared-styles 令牌，同时开 Element Plus 的 .dark */
  function applyTheme(value: 'light' | 'dark') {
    const el = document.documentElement
    if (value === 'dark') {
      el.setAttribute('data-dv-theme', 'dark')
      el.classList.add('dark')
    } else {
      el.removeAttribute('data-dv-theme')
      el.classList.remove('dark')
    }
  }

  function initialize() {
    const saved = getLocal<Partial<AdminSettings>>('admin_settings')
    if (saved) settings.value = { ...settings.value, ...saved }
    applyTheme(settings.value.theme)
  }

  function toggleSidebar() {
    settings.value.sidebarCollapsed = !settings.value.sidebarCollapsed
    persist()
  }

  function setTheme(value: 'light' | 'dark') {
    settings.value.theme = value
    applyTheme(value)
    persist()
  }

  function persist() {
    setLocal('admin_settings', settings.value)
  }

  return { settings, sidebarCollapsed, theme, initialize, applyTheme, toggleSidebar, setTheme }
})
