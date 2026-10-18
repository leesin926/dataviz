import { createI18n } from 'vue-i18n'
import zhCN from './zh-CN.json'
import enUS from './en-US.json'

export type AppLocale = 'zh-CN' | 'en-US'

export function getStoredLocale(): AppLocale {
  return localStorage.getItem('dataviz_locale') === 'en-US' ? 'en-US' : 'zh-CN'
}

/**
 * `<html lang>` 跟着语言走。读屏软件按它选发音、浏览器按它决定要不要弹"翻译此页"，
 * 而这两个都只看这个属性，不看 localStorage —— 属性停在 zh-CN 时，英文界面的无障碍朗读
 * 会用中文音库去念英文，等于切了语言但没说给机器听。
 */
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

// index.html 里写死的是 zh-CN；上次存了 en-US 的用户如果不经过切换按钮直接进来，
// 界面是英文而 lang 仍是 zh-CN，所以首次加载就要补一次。
applyHtmlLang(i18n.global.locale.value)

export const t = (key: string, named?: Record<string, unknown>) =>
  i18n.global.t(key, named ? { ...named } : {})
