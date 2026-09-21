import { createI18n } from 'vue-i18n'
import zhCN from './zh-CN.json'
import enUS from './en-US.json'

export type AppLocale = 'zh-CN' | 'en-US'

export function getStoredLocale(): AppLocale {
  return localStorage.getItem('dataviz_locale') === 'en-US' ? 'en-US' : 'zh-CN'
}

export function switchLocale(locale: AppLocale) {
  i18n.global.locale.value = locale
  localStorage.setItem('dataviz_locale', locale)
}

export const i18n = createI18n({
  legacy: false,
  globalInjection: true,
  locale: getStoredLocale(),
  fallbackLocale: 'zh-CN',
  messages: {
    'zh-CN': zhCN,
    'en-US': enUS,
  },
})

export const t = (key: string, named?: Record<string, unknown>) =>
  i18n.global.t(key, named ? { ...named } : {})
