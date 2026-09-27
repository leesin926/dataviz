import axios, { type AxiosError, type AxiosRequestConfig, type AxiosResponse, type InternalAxiosRequestConfig } from 'axios'
import type { R } from '@dataviz/shared-types'
import { getLocal, removeLocal, setLocal } from '@dataviz/shared-utils'

const BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api'
const TIMEOUT = 30000

const TOKEN_KEY = 'access_token'
const REFRESH_TOKEN_KEY = 'refresh_token'

/** 会话失效提示文案由应用层注入翻译器（api-client 不依赖 vue-i18n） */
export type AuthMessageResolver = () => string

let authMessage: AuthMessageResolver | null = null

export function setAuthMessageResolver(resolver: AuthMessageResolver): void {
  authMessage = resolver
}

const SESSION_EXPIRED_FALLBACK = 'Session expired, please sign in again'

function sessionExpiredMessage(): string {
  return authMessage ? authMessage() : SESSION_EXPIRED_FALLBACK
}

/**
 * 创建 axios 实例
 */
export const request = axios.create({
  baseURL: BASE_URL,
  timeout: TIMEOUT,
  headers: {
    'Content-Type': 'application/json',
  },
})

/**
 * 请求拦截: 注入 token
 */
request.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const token = getLocal<string>(TOKEN_KEY)
    if (token && config.headers) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => Promise.reject(error),
)

/**
 * 响应拦截: 统一错误处理
 */
request.interceptors.response.use(
  (response) => {
    // 服务端会在 JWT 临期时通过 New-Token 头下发轮换 token，需落盘否则下次仍用旧串
    const rotated = response.headers?.['new-token']
    if (typeof rotated === 'string' && rotated) setLocal(TOKEN_KEY, rotated)
    const { data } = response as AxiosResponse<R>
    if (data.code !== undefined && data.code !== 0 && data.code !== 200) {
      const err = new Error(data.message || '请求失败') as Error & { code?: number }
      err.code = data.code
      return Promise.reject(err)
    }
    return response
  },
  async (error: AxiosError<R>) => {
    if (error.response) {
      const { status, data } = error.response
      switch (status) {
        case 401: {
          const refreshed = await tryRefreshToken()
          if (!refreshed) {
            handleLogout()
            return Promise.reject(authError(error))
          }
          // 刷新成功：用新 token 重放原请求，用户不会感知到这一次失败
          const cfg = error.config as (InternalAxiosRequestConfig & { _retried?: boolean }) | undefined
          if (cfg && !cfg._retried) {
            cfg._retried = true
            if (cfg.headers) cfg.headers.Authorization = `Bearer ${getLocal<string>(TOKEN_KEY)}`
            return request(cfg)
          }
          break
        }
        case 403:
          console.error('[request] 无权限:', data?.message)
          markForbidden()
          break
        case 404:
          console.error('[request] 资源不存在')
          break
        case 500:
          console.error('[request] 服务器错误:', data?.message)
          break
        default:
          console.error(`[request] 错误 ${status}:`, data?.message)
      }
    } else if (String(error.message ?? '').includes('timeout')) {
      console.error('[request] 请求超时')
    } else {
      console.error('[request] 网络错误')
    }
    return Promise.reject(error)
  },
)

/** 一次页面加载可能并发多个请求同时被拒，2s 内只广播一次，避免弹刷屏 */
let lastForbiddenAt = 0

function markForbidden(): void {
  if (typeof window === 'undefined') return
  const now = Date.now()
  if (now - lastForbiddenAt < 2000) return
  lastForbiddenAt = now
  window.dispatchEvent(new CustomEvent('dv:forbidden'))
}

/** 尝试刷新 token
 *  多个并发 401 共用同一次刷新请求，避免重复刷新互相覆盖 refresh_token */
let refreshing: Promise<boolean> | null = null

async function tryRefreshToken(): Promise<boolean> {
  if (refreshing) return refreshing
  const refreshToken = getLocal<string>(REFRESH_TOKEN_KEY)
  if (!refreshToken) return false
  refreshing = (async () => {
    try {
      const { data } = await axios.post<R<{ accessToken: string; refreshToken: string }>>(
        `${BASE_URL}/auth/refreshToken`,
        { refreshToken },
      )
      if (data.code === 0 || data.code === 200) {
        setLocal(TOKEN_KEY, data.data.accessToken)
        setLocal(REFRESH_TOKEN_KEY, data.data.refreshToken)
        return true
      }
      return false
    } catch {
      return false
    } finally {
      refreshing = null
    }
  })()
  return refreshing
}

/** 会话失效：带上本地化文案与原始 cause，应用层据此决定提示还是静默 */
function authError(cause: unknown): Error {
  const err = new Error(sessionExpiredMessage()) as Error & { cause?: unknown }
  err.cause = cause
  return err
}

/**
 * 登出处理
 */
function handleLogout(): void {
  removeLocal(TOKEN_KEY)
  removeLocal(REFRESH_TOKEN_KEY)
  removeLocal('user')
  removeLocal('permissions')
  if (typeof window === 'undefined') return
  // 应用层可 preventDefault 自行跳转（便于展示本地化提示）；无人处理时兜底整页跳登录
  const handledByApp = !window.dispatchEvent(new CustomEvent('dv:session-expired', { cancelable: true }))
  if (handledByApp) return
  if (!window.location.pathname.includes('/login')) {
    window.location.href = '/login'
  }
}

/**
 * 通用请求封装
 */
export async function apiGet<T>(url: string, params?: unknown, config?: AxiosRequestConfig): Promise<T> {
  const res = await request.get<R<T>>(url, { params, ...config })
  return res.data.data
}

export async function apiPost<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
  const res = await request.post<R<T>>(url, data, config)
  return res.data.data
}

export async function apiPut<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
  const res = await request.put<R<T>>(url, data, config)
  return res.data.data
}

export async function apiDelete<T>(url: string, config?: AxiosRequestConfig): Promise<T> {
  const res = await request.delete<R<T>>(url, config)
  return res.data.data
}

export function getToken(): string | null {
  return getLocal<string>(TOKEN_KEY)
}

export function setToken(token: string, refreshToken?: string): void {
  setLocal(TOKEN_KEY, token)
  if (refreshToken) setLocal(REFRESH_TOKEN_KEY, refreshToken)
}

export function clearToken(): void {
  removeLocal(TOKEN_KEY)
  removeLocal(REFRESH_TOKEN_KEY)
}

export { BASE_URL, TIMEOUT }
