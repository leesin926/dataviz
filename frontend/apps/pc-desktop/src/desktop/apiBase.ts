/**
 * 服务地址解析。
 *
 * 浏览器端用相对路径 /api 就够了（vite 代理 / nginx 反代都会接住），但打包后的桌面端
 * 窗口的 origin 是 file://，相对路径会解析成 file:///api —— 请求根本出不了本机。
 * 所以桌面端必须有一个绝对的、可在运行时改的服务地址，优先级：
 *   构建期 VITE_API_BASE_URL > 用户在本机存过的 dataviz_api_base > 相对 /api（仅在 http(s) 页面下才有意义）。
 */
import { request } from '@dataviz/api-client'
import { t } from '@/locales'

export const API_BASE_KEY = 'dataviz_api_base'

const FALLBACK = '/api'

export function isFileProtocol(): boolean {
  return window.location.protocol === 'file:'
}

export function storedBase(): string | null {
  const raw = localStorage.getItem(API_BASE_KEY)
  return raw && raw.trim() ? raw.trim() : null
}

export function currentBase(): string {
  const baked = (import.meta.env.VITE_API_BASE_URL as string | undefined) || ''
  const stored = storedBase()
  if (baked) return baked
  if (stored) return stored
  return FALLBACK
}

/** 只接受 http(s) 绝对地址或相对路径：把 "javascript:alert(1)" 之类的当 baseURL 写进 axios 等于开了个执行入口 */
export function validateBase(input: string): string {
  const value = input.trim()
  if (!value) throw new Error(t('desktop.errBaseEmpty'))
  if (value.startsWith('/')) return value
  if (!/^https?:\/\/[^\s]+$/.test(value)) throw new Error(t('desktop.errBaseFormat'))
  return value.replace(/\/+$/, '')
}

export function applyBase(base?: string): string {
  if (!base) {
    const current = currentBase()
    request.defaults.baseURL = current
    return current
  }
  const next = validateBase(base)
  localStorage.setItem(API_BASE_KEY, next)
  request.defaults.baseURL = next
  return next
}

/** file:// 下且没配绝对地址 = 任何接口都必然失败，界面必须先说清楚而不是转圈 */
export function baseLooksUnusable(): boolean {
  return isFileProtocol() && !/^https?:\/\//.test(currentBase())
}
