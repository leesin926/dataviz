/**
 * uni 端登录工具：账号登录走后端网关，短信登录为前端假实现（永远成功）。
 * 存储 key 与 Web 端保持一致（dataviz_ 前缀）。
 */

import { request, getToken } from './request'

const REFRESH_TOKEN_KEY = 'dataviz_refresh_token'
const USER_KEY = 'dataviz_user'
const PERMISSIONS_KEY = 'dataviz_permissions'
const SMS_MOCK_TOKEN_PREFIX = 'sms-mock-'

export interface LoginResult {
  accessToken: string
  refreshToken: string
  userId: number
  username: string
  roles: string[]
  permissions: string[]
}

export { getToken }

export function isLogged(): boolean {
  return !!getToken()
}

function saveSession(result: LoginResult): void {
  uni.setStorageSync('dataviz_access_token', result.accessToken)
  uni.setStorageSync(REFRESH_TOKEN_KEY, result.refreshToken)
  uni.setStorageSync(USER_KEY, {
    id: result.userId,
    username: result.username,
    nickname: result.username,
    roles: result.roles,
    permissions: result.permissions,
  })
  uni.setStorageSync(PERMISSIONS_KEY, result.permissions)
}

export function clearSession(): void {
  uni.removeStorageSync('dataviz_access_token')
  uni.removeStorageSync(REFRESH_TOKEN_KEY)
  uni.removeStorageSync(USER_KEY)
  uni.removeStorageSync(PERMISSIONS_KEY)
}

export interface CaptchaResult {
  captchaKey: string
  captchaImage: string
  expireSeconds: number
}

export function getCaptcha(): Promise<CaptchaResult> {
  return request<CaptchaResult>('GET', '/auth/captcha')
}

export function passwordLogin(
  username: string,
  password: string,
  captchaCode: string,
  captchaKey: string,
): Promise<LoginResult> {
  return request<LoginResult>('POST', '/auth/login', {
    username,
    password,
    captchaCode,
    captchaKey,
  }).then((result) => {
    saveSession(result)
    return result
  })
}

function isValidPhone(phone: string): boolean {
  return /^1[3-9]\d{9}$/.test(phone)
}

function delay(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

/** 发送短信验证码（假接口，永远成功） */
export async function sendSmsCode(phone: string): Promise<void> {
  if (!isValidPhone(phone)) throw new Error('请输入正确的手机号')
  await delay(500)
  console.info(`[sms-mock] 验证码已发送至 ${phone}（假接口，任意验证码均可登录）`)
}

/** 短信登录（假接口，永远成功） */
export async function smsLogin(phone: string, code: string): Promise<LoginResult> {
  if (!isValidPhone(phone)) throw new Error('请输入正确的手机号')
  if (!/^\d{4,8}$/.test(code)) throw new Error('请输入验证码')
  await delay(600)
  const rand = Math.random().toString(36).slice(2, 10) + Date.now().toString(36)
  const result: LoginResult = {
    accessToken: `${SMS_MOCK_TOKEN_PREFIX}${rand}`,
    refreshToken: `${SMS_MOCK_TOKEN_PREFIX}refresh-${rand}`,
    userId: 0,
    username: phone,
    roles: ['super_admin'],
    permissions: ['*'],
  }
  saveSession(result)
  return result
}
