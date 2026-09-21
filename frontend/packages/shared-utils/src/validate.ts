/**
 * 验证工具
 */

export const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
export const phoneRegex = /^1[3-9]\d{9}$/
export const idCardRegex = /^[1-9]\d{5}(19|20)\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\d|3[01])\d{3}[\dXx]$/
export const urlRegex = /^https?:\/\/[^\s$.?#].[^\s]*$/i

export function isEmail(value: string): boolean {
  return emailRegex.test(value)
}

export function isPhone(value: string): boolean {
  return phoneRegex.test(value)
}

export function isIdCard(value: string): boolean {
  return idCardRegex.test(value)
}

export function isUrl(value: string): boolean {
  return urlRegex.test(value)
}

export function isNotEmpty(value: unknown): boolean {
  if (value === null || value === undefined) return false
  if (typeof value === 'string') return value.trim().length > 0
  if (Array.isArray(value)) return value.length > 0
  if (typeof value === 'object') return Object.keys(value as object).length > 0
  return true
}

export function isValidUsername(value: string): boolean {
  return /^[a-zA-Z0-9_]{4,32}$/.test(value)
}

export function isValidPassword(value: string): boolean {
  // 8-32位，至少包含大小写字母和数字
  return /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).{8,32}$/.test(value)
}
