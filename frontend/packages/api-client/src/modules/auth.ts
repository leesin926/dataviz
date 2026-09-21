import type { LoginResult, LoginForm, R } from '@dataviz/shared-types'
import { request } from '../request'

export interface CaptchaResult {
  captchaKey: string
  captchaImage: string // base64 data URL
  expireSeconds: number
}

/** 登录 */
export async function login(form: LoginForm): Promise<LoginResult> {
  const res = await request.post<R<LoginResult>>('/auth/login', form)
  return res.data.data
}

/** 登出 */
export async function logout(): Promise<void> {
  await request.post('/auth/logout')
}

/** 刷新 token */
export async function refreshToken(refreshToken: string): Promise<{ accessToken: string; refreshToken: string; expiresIn: number }> {
  const res = await request.post<R<{ accessToken: string; refreshToken: string; expiresIn: number }>>(
    '/auth/refreshToken',
    { refreshToken },
  )
  return res.data.data
}

/** 获取验证码 */
export async function getCaptcha(): Promise<CaptchaResult> {
  const res = await request.get<R<CaptchaResult>>('/auth/captcha')
  return res.data.data
}

/** 获取当前用户信息 */
export async function getCurrentUser(): Promise<unknown> {
  const res = await request.get<R<unknown>>('/auth/userinfo')
  return res.data.data
}
