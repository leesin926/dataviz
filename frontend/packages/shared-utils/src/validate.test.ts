import { describe, expect, it } from 'vitest'
import { isEmail, isIdCard, isNotEmpty, isPhone, isUrl, isValidPassword, isValidUsername } from './validate'

describe('shared-utils/validate —— 联系方式与证件', () => {
  it('isEmail 要求 "@域名.顶级域" 三段齐全，域名里的空格直接判非法', () => {
    expect(isEmail('ops@example.com')).toBe(true)
    expect(isEmail('a@b.co')).toBe(true)
    expect(isEmail('a@b')).toBe(false)
    expect(isEmail('a b@c.co')).toBe(false)
    expect(isEmail('@b.co')).toBe(false)
  })

  it('isPhone 只认中国大陆 1[3-9] 开头 11 位，号段 12 与少一位都拒', () => {
    expect(isPhone('13800138000')).toBe(true)
    expect(isPhone('19912345678')).toBe(true)
    expect(isPhone('12800138000')).toBe(false)
    expect(isPhone('1380013800')).toBe(false)
    expect(isPhone('138001380000')).toBe(false)
  })

  it('isIdCard 校验 18 位结构与月日范围，X 校验位大小写都收', () => {
    expect(isIdCard('110105199003074512')).toBe(true)
    expect(isIdCard('11010519900307451X')).toBe(true)
    expect(isIdCard('11010519900307451x')).toBe(true)
    // 少一位 / 月份 13 / 日期 32
    expect(isIdCard('11010519900307451')).toBe(false)
    expect(isIdCard('110105199013074512')).toBe(false)
    expect(isIdCard('110105199003324512')).toBe(false)
  })
})

describe('shared-utils/validate —— URL、空值与账号口令', () => {
  it('isUrl 只放行 http/https 协议，且协议后必须跟可打印主机', () => {
    expect(isUrl('https://a.com/x')).toBe(true)
    expect(isUrl('http://127.0.0.1:17080/api')).toBe(true)
    expect(isUrl('ftp://a.com')).toBe(false)
    expect(isUrl('https://.com')).toBe(false)
    expect(isUrl('a.com')).toBe(false)
  })

  it('isNotEmpty 对字符串按 trim 判空，对数组/对象按长度判空，数字与布尔恒为非空', () => {
    expect(isNotEmpty(null)).toBe(false)
    expect(isNotEmpty(undefined)).toBe(false)
    expect(isNotEmpty('   ')).toBe(false)
    expect(isNotEmpty('a')).toBe(true)
    expect(isNotEmpty([])).toBe(false)
    expect(isNotEmpty([0])).toBe(true)
    expect(isNotEmpty({})).toBe(false)
    expect(isNotEmpty({ a: 1 })).toBe(true)
    // 0 / false 是合法业务值，不能被当成空
    expect(isNotEmpty(0)).toBe(true)
    expect(isNotEmpty(false)).toBe(true)
  })

  it('isValidUsername 限 4-32 位字母数字下划线，中文与连字符拒', () => {
    expect(isValidUsername('ops_01')).toBe(true)
    expect(isValidUsername('abc')).toBe(false)
    expect(isValidUsername('ops-01')).toBe(false)
    expect(isValidUsername('a'.repeat(33))).toBe(false)
    expect(isValidUsername('a'.repeat(32))).toBe(true)
  })

  it('isValidPassword 要求 8-32 位且大小写字母与数字至少各一个', () => {
    expect(isValidPassword('Abcdef12')).toBe(true)
    expect(isValidPassword('abcdefgh')).toBe(false)
    expect(isValidPassword('ABCDEFGH')).toBe(false)
    expect(isValidPassword('12345678')).toBe(false)
    expect(isValidPassword('Ab1')).toBe(false)
    expect(isValidPassword('Abcdef123'.repeat(4))).toBe(false)
  })
})
