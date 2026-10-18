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

/**
 * `<html lang>` 跟着语言走。读屏软件按它选发音、浏览器按它决定要不要弹"翻译此页"，
 * 两者都只看这个属性而不看 localStorage —— 属性停在 zh-CN 时，英文界面的朗读会用中文音库。
 */
function applyHtmlLang(locale: AppLocale) {
  document.documentElement.lang = locale
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

// index.html 写死 zh-CN：带着已存过的 en-US 直接进来时界面是英文但 lang 没跟上，所以首屏补一次
applyHtmlLang(i18n.global.locale.value)

/** 供 setup 外（表单校验规则、消息提示）使用的全局翻译函数 */
export const t = (key: string, named?: Record<string, unknown>) =>
  i18n.global.t(key, named ? { ...named } : {})

export function switchLocale(locale: AppLocale) {
  i18n.global.locale.value = locale
  setStoredLocale(locale)
  applyHtmlLang(locale)
}
