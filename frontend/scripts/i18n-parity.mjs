#!/usr/bin/env node
// i18n 门禁：三份词典的键对齐 + en-US 无残留中文 + 代码里静态用到的键都有定义。
// 动态键（模板字符串/变量）只报告不算失败——静态扫描猜不出它的取值。
import { readFileSync, readdirSync, statSync, existsSync } from 'node:fs'
import { join, relative } from 'node:path'
import { fileURLToPath } from 'node:url'

const ROOT = fileURLToPath(new URL('..', import.meta.url))
const APPS = ['admin', 'pc-web', 'pc-desktop']
const CJK = /[\u3400-\u4dbf\u4e00-\u9fff\uf900-\ufaff\u3000-\u303f\uff00-\uffef]/

function flatten(obj, prefix = '', out = new Map()) {
  for (const [key, value] of Object.entries(obj)) {
    const path = prefix ? `${prefix}.${key}` : key
    if (value && typeof value === 'object' && !Array.isArray(value)) flatten(value, path, out)
    else out.set(path, value)
  }
  return out
}

function walk(dir, files = []) {
  for (const entry of readdirSync(dir)) {
    if (entry === 'node_modules' || entry.startsWith('.')) continue
    const full = join(dir, entry)
    if (statSync(full).isDirectory()) walk(full, files)
    else if (/\.(vue|ts|tsx|js)$/.test(entry)) files.push(full)
  }
  return files
}

const STATIC_USE = /\b(?:\$t|t|te)\s*\(\s*['"]([^'"]+)['"]/g
const DYNAMIC_USE = /\b(?:\$t|t)\s*\(\s*[`]/g

const failures = []
const notes = []

for (const app of APPS) {
  const localeDir = join(ROOT, 'apps', app, 'src', 'locales')
  if (!existsSync(localeDir)) {
    failures.push(`${app}: locales 目录不存在`)
    continue
  }
  const zh = flatten(JSON.parse(readFileSync(join(localeDir, 'zh-CN.json'), 'utf8')))
  const en = flatten(JSON.parse(readFileSync(join(localeDir, 'en-US.json'), 'utf8')))

  const missingEn = [...zh.keys()].filter((k) => !en.has(k))
  const missingZh = [...en.keys()].filter((k) => !zh.has(k))
  if (missingEn.length) failures.push(`${app}: en-US 缺 ${missingEn.length} 键 -> ${missingEn.slice(0, 8).join(', ')}`)
  if (missingZh.length) failures.push(`${app}: zh-CN 缺 ${missingZh.length} 键 -> ${missingZh.slice(0, 8).join(', ')}`)

  const cjkValues = [...en].filter(([, v]) => typeof v === 'string' && CJK.test(v))
  if (cjkValues.length) failures.push(`${app}: en-US 仍有中文取值 -> ${cjkValues.map(([k]) => k).slice(0, 8).join(', ')}`)

  const srcDir = join(ROOT, 'apps', app, 'src')
  const undefinedKeys = new Map()
  let dynamicUses = 0
  for (const file of walk(srcDir)) {
    const text = readFileSync(file, 'utf8')
    for (const match of text.matchAll(STATIC_USE)) {
      const key = match[1]
      if (zh.has(key)) continue
      if (!undefinedKeys.has(key)) undefinedKeys.set(key, relative(ROOT, file))
    }
    dynamicUses += [...text.matchAll(DYNAMIC_USE)].length
  }
  if (undefinedKeys.size) {
    const lines = [...undefinedKeys].map(([k, f]) => `    ${k} (${f})`).join('\n')
    failures.push(`${app}: 代码用到但词典没有 ${undefinedKeys.size} 键\n${lines}`)
  }
  notes.push(`${app}: zh ${zh.size} / en ${en.size} 键，静态用键 ${undefinedKeys.size === 0 ? '全部有定义' : '有缺失'}，动态用键 ${dynamicUses} 处未校验`)
}

for (const note of notes) console.log(note)
if (failures.length) {
  console.error('\ni18n parity FAILED:')
  for (const failure of failures) console.error(`  - ${failure}`)
  process.exit(1)
}
console.log('\ni18n parity OK')
