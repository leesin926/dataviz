import { getLocal, setLocal, removeLocal } from '@dataviz/shared-utils'

const TOKEN_KEY = 'access_token'
const REFRESH_TOKEN_KEY = 'refresh_token'

export function getToken(): string | null {
  return getLocal<string>(TOKEN_KEY)
}

export function setToken(token: string, refreshToken?: string): void {
  setLocal(TOKEN_KEY, token)
  if (refreshToken) setLocal(REFRESH_TOKEN_KEY, refreshToken)
}

export function removeToken(): void {
  removeLocal(TOKEN_KEY)
  removeLocal(REFRESH_TOKEN_KEY)
}

export function isTokenExpired(): boolean {
  // 简单检查: 若存在 token 则视为有效 (实际可扩展为 JWT 过期检查)
  return !getToken()
}
