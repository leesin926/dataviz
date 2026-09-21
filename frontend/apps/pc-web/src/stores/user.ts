import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { User, UserInfo } from '@dataviz/shared-types'
import { login as apiLogin, logout as apiLogout, getCurrentUser, smsLogin as apiSmsLogin } from '@dataviz/api-client'
import { setToken, clearToken, getToken } from '@dataviz/api-client'
import { setLocal, getLocal, removeLocal } from '@dataviz/shared-utils'
import { router } from '@/router'

export const useUserStore = defineStore('user', () => {
  const user = ref<UserInfo | null>(null)
  const token = ref<string | null>(getToken())

  const isLoggedIn = computed(() => !!token.value)
  const permissions = computed<string[]>(() => user.value?.permissions || [])
  const roles = computed(() => user.value?.roles || [])
  const isAdmin = computed(() => user.value?.isAdmin ?? false)

  async function login(username: string, password: string, captcha?: string, captchaKey?: string) {
    const result = await apiLogin({ username, password, captchaCode: captcha, captchaKey })
    token.value = result.accessToken
    setToken(result.accessToken, result.refreshToken)
    await fetchUserInfo()
    return result
  }

  async function smsLogin(phone: string, code: string) {
    const result = await apiSmsLogin(phone, code)
    token.value = result.accessToken
    setToken(result.accessToken, result.refreshToken)
    const userInfo = {
      id: result.userId,
      username: result.username,
      nickname: result.username,
      avatar: '',
      email: '',
      phone,
      dept: null,
      roles: result.roles,
      permissions: result.permissions,
      isAdmin: result.roles.includes('super_admin'),
    } as unknown as UserInfo
    user.value = userInfo
    setLocal('user', userInfo)
    setLocal('permissions', userInfo.permissions)
    return result
  }

  async function fetchUserInfo() {
    const info = await getCurrentUser()
    const raw = info as Record<string, any>
    const roles: string[] = raw.roles ?? []
    const userInfo = {
      ...raw,
      id: raw.id ?? raw.userId,
      roles,
      permissions: raw.permissions ?? [],
      isAdmin: roles.includes('super_admin'),
    } as unknown as UserInfo
    user.value = userInfo
    setLocal('user', userInfo)
    setLocal('permissions', userInfo.permissions)
    return info
  }

  async function logout() {
    try {
      await apiLogout()
    } catch {
      // ignore
    }
    user.value = null
    token.value = null
    clearToken()
    removeLocal('user')
    removeLocal('permissions')
    router.push('/login')
  }

  function hasPermission(code: string): boolean {
    if (isAdmin.value) return true
    return permissions.value.includes(code)
  }

  return {
    user,
    token,
    isLoggedIn,
    permissions,
    roles,
    isAdmin,
    login,
    smsLogin,
    logout,
    fetchUserInfo,
    hasPermission,
  }
})
