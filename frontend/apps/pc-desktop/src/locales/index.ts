import { createI18n } from 'vue-i18n'
import zhCN from './zh-CN.json'
import enUS from './en-US.json'

export type AppLocale = 'zh-CN' | 'en-US'

export function getStoredLocale(): AppLocale {
  return localStorage.getItem('dataviz_locale') === 'en-US' ? 'en-US' : 'zh-CN'
}

/** `<html lang>` 必须跟着 locale：读屏软件与浏览器翻译提示只认这个属性，不认 localStorage */
function applyHtmlLang(locale: AppLocale) {
  document.documentElement.lang = locale
}

export function switchLocale(locale: AppLocale) {
  i18n.global.locale.value = locale
  localStorage.setItem('dataviz_locale', locale)
  applyHtmlLang(locale)
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

// index.html 写死 zh-CN：带着已存过的 en-US 直接进来时，界面是英文但 lang 没跟上
applyHtmlLang(i18n.global.locale.value)

export const t = (key: string, named?: Record<string, unknown>) =>
  i18n.global.t(key, named ? { ...named } : {})
