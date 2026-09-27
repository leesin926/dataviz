/**
 * uni 端登录工具：账密与短信都走后端网关，两条路返回同一个 LoginResult（同一个发 token + 写会话快照的实现）。
 * 存储 key 与 Web 端保持一致（dataviz_ 前缀）。
 */

import { request, getToken } from './request'
import { SMS_TERMINAL } from './device'

const REFRESH_TOKEN_KEY = 'dataviz_refresh_token'
const USER_KEY = 'dataviz_user'
const PERMISSIONS_KEY = 'dataviz_permissions'

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
  // 与后端 SmsSendDTO/SmsLoginDTO 的 @Pattern 同一条规则
  return /^1[3-9]\d{9}$/.test(phone)
}

export interface SmsSendResult {
  expireSeconds: number
  resendAfterSeconds: number
}

/**
 * 发送短信验证码。演示环境未接入短信渠道，码值固定（见后端 auth.sms.mock-code），但发送/校验/限流状态机是真的。
 * 终端标识取本端 device.ts，后端按"手机号+终端"隔离闸门，所以同一账号在多个端登录不会互相踩。
 */
export function sendSmsCode(phone: string): Promise<SmsSendResult> {
  if (!isValidPhone(phone)) return Promise.reject(new Error('请输入正确的手机号'))
  return request<SmsSendResult>('POST', '/auth/sms/send', { phone, terminal: SMS_TERMINAL })
}

/** 短信登录：验证码替代口令，之后与账密登录返回同一种 LoginResult */
export function smsLogin(phone: string, code: string): Promise<LoginResult> {
  if (!isValidPhone(phone)) return Promise.reject(new Error('请输入正确的手机号'))
  if (!/^\d{4,8}$/.test(code)) return Promise.reject(new Error('请输入验证码'))
  return request<LoginResult>('POST', '/auth/sms/login', { phone, code, terminal: SMS_TERMINAL }).then((result) => {
    saveSession(result)
    return result
  })
}
