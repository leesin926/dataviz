/**
 * Storage 封装 (支持过期时间、JSON 序列化)
 */

const STORAGE_PREFIX = 'dataviz_'

interface StorageItem<T> {
  value: T
  expireAt?: number
}

function makeKey(key: string): string {
  return STORAGE_PREFIX + key
}

/**
 * 设置 localStorage
 */
export function setLocal<T>(key: string, value: T, ttl?: number): void {
  const item: StorageItem<T> = { value }
  if (ttl !== undefined) {
    item.expireAt = Date.now() + ttl
  }
  try {
    window.localStorage.setItem(makeKey(key), JSON.stringify(item))
  } catch (e) {
    console.error(`[storage] localStorage set failed: key=${key}`, e)
  }
}

/**
 * 获取 localStorage
 */
export function getLocal<T>(key: string): T | null {
  try {
    const raw = window.localStorage.getItem(makeKey(key))
    if (!raw) return null
    const item = JSON.parse(raw) as unknown
    // 契约：setLocal 永远落 { value, expireAt? } 信封。所谓"corrupt" = 顶层不是带 value 字段的信封对象
    // （裸值历史数据如 "abc"/0/数组、或解析出的标量），一律按缺失处理返回 null，而不是把 undefined 漏给调用方（API-149）。
    if (item === null || typeof item !== 'object' || !('value' in item)) return null
    const env = item as StorageItem<T>
    if (env.expireAt && Date.now() > env.expireAt) {
      window.localStorage.removeItem(makeKey(key))
      return null
    }
    return env.value
  } catch (e) {
    return null
  }
}

/**
 * 删除 localStorage
 */
export function removeLocal(key: string): void {
  window.localStorage.removeItem(makeKey(key))
}

/**
 * 清空 dataviz 前缀的所有 key
 */
export function clearLocal(): void {
  const keys: string[] = []
  for (let i = 0; i < window.localStorage.length; i++) {
    const k = window.localStorage.key(i)
    if (k && k.startsWith(STORAGE_PREFIX)) keys.push(k)
  }
  keys.forEach((k) => window.localStorage.removeItem(k))
}

/**
 * 设置 sessionStorage
 */
export function setSession<T>(key: string, value: T): void {
  try {
    window.sessionStorage.setItem(makeKey(key), JSON.stringify(value))
  } catch (e) {
    console.error(`[storage] sessionStorage set failed: key=${key}`, e)
  }
}

/**
 * 获取 sessionStorage
 */
export function getSession<T>(key: string): T | null {
  try {
    const raw = window.sessionStorage.getItem(makeKey(key))
    if (!raw) return null
    return JSON.parse(raw) as T
  } catch {
    return null
  }
}

/**
 * 删除 sessionStorage
 */
export function removeSession(key: string): void {
  window.sessionStorage.removeItem(makeKey(key))
}
