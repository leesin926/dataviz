import type { LoginResult } from '@dataviz/shared-types'

/**
 * 短信登录 —— 前端假实现（后端暂未提供短信接口）
 * 发送验证码与登录均永远成功，token 带 sms-mock- 前缀便于识别。
 */

export const SMS_MOCK_TOKEN_PREFIX = 'sms-mock-'

function delay(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

export function isValidPhone(phone: string): boolean {
  return /^1[3-9]\d{9}$/.test(phone)
}

export function isSmsMockToken(token: string | null | undefined): boolean {
  return !!token && token.startsWith(SMS_MOCK_TOKEN_PREFIX)
}

/** 发送短信验证码（假接口，永远成功） */
export async function sendSmsCode(phone: string): Promise<{ expireSeconds: number }> {
  if (!isValidPhone(phone)) {
    throw new Error('请输入正确的手机号')
  }
  await delay(500)
  console.info(`[sms-mock] 验证码已发送至 ${phone}（假接口，任意验证码均可登录）`)
  return { expireSeconds: 60 }
}

/** 短信登录（假接口，永远成功） */
export async function smsLogin(phone: string, code: string): Promise<LoginResult> {
  if (!isValidPhone(phone)) {
    throw new Error('请输入正确的手机号')
  }
  if (!/^\d{4,8}$/.test(code)) {
    throw new Error('请输入验证码')
  }
  await delay(600)
  const rand = Math.random().toString(36).slice(2, 10) + Date.now().toString(36)
  return {
    accessToken: `${SMS_MOCK_TOKEN_PREFIX}${rand}`,
    refreshToken: `${SMS_MOCK_TOKEN_PREFIX}refresh-${rand}`,
    userId: 0,
    username: phone,
    roles: ['super_admin'],
    permissions: ['*'],
  }
}
