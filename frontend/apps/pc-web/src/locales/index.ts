import { createI18n } from 'vue-i18n'
import zhCN from './zh-CN.json'
import enUS from './en-US.json'

export type AppLocale = 'zh-CN' | 'en-US'

const STORAGE_KEY = 'dataviz_locale'

export function getStoredLocale(): AppLocale {
  return localStorage.getItem(STORAGE_KEY) === 'en-US' ? 'en-US' : 'zh-CN'
}

export function setStoredLocale(locale: AppLocale) {
  localStorage.setItem(STORAGE_KEY, locale)
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

/** 供 setup 外（表单校验规则、消息提示）使用的全局翻译函数 */
export const t = (key: string, named?: Record<string, unknown>) =>
  i18n.global.t(key, named ? { ...named } : {})

export function switchLocale(locale: AppLocale) {
  i18n.global.locale.value = locale
  setStoredLocale(locale)
}
