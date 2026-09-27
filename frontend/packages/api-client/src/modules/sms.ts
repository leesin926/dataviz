import type { LoginResult, R } from '@dataviz/shared-types'
import { request } from '../request'

/**
 * 短信登录 —— 真接口。
 *
 * 这里以前是一份"永远成功"的假实现，伪造出 `sms-mock-` 前缀的 token 并顺手写上
 * `roles: ['super_admin']` + `permissions: ['*']`，等于任何手机号都是超管。
 * 现在码值由后端下发（演示环境固定 123123，见 SmsCodeService），登录走的是与账密同一条发 token 的路。
 */

/** 与后端 SmsSendDTO/SmsLoginDTO 的 @Pattern 同一条规则 —— 改一处必须同步另一处 */
export function isValidPhone(phone: string): boolean {
  return /^1[3-9]\d{9}$/.test(phone)
}

/**
 * 登录发起端。后端按"手机号 + 终端"隔离验证码与重发闸门，所以同一个人换端登录不再互相踩；
 * 值必须落在后端 {@code SmsTerminalConstant.PATTERN} 的白名单里，两边漏一边就是发码直接失败。
 */
export type SmsTerminal =
  | 'admin'
  | 'pc-web'
  | 'pc-desktop'
  | 'mobile-app'
  | 'tablet-app'
  | 'mini-program'

export interface SmsSendResult {
  /** 验证码有效期（秒） */
  expireSeconds: number
  /** 重发间隔（秒），倒计时以它为准，不再前端写死 60 */
  resendAfterSeconds: number
}

/** 下发验证码；手机号未绑定、被停用、发太频繁都由后端以业务失败抛出 */
export async function sendSmsCode(phone: string, terminal: SmsTerminal): Promise<SmsSendResult> {
  if (!isValidPhone(phone)) {
    throw new Error('请输入正确的手机号')
  }
  const res = await request.post<R<SmsSendResult>>('/auth/sms/send', { phone, terminal })
  return res.data.data
}

/** 短信登录，返回体与账密登录同构；terminal 必须是发码时那一个，否则后端读到的是另一份码 */
export async function smsLogin(phone: string, code: string, terminal: SmsTerminal): Promise<LoginResult> {
  if (!isValidPhone(phone)) {
    throw new Error('请输入正确的手机号')
  }
  if (!/^\d{4,8}$/.test(code)) {
    throw new Error('请输入验证码')
  }
  const res = await request.post<R<LoginResult>>('/auth/sms/login', { phone, code, terminal })
  return res.data.data
}
