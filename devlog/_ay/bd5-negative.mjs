/**
 * 自检的反证（negative test）
 *
 * 为什么要这一步：补丁的 13 条自检全靠"没有 @@BAD 行"来判通过，而"没有行"有两种成因 ——
 * 数据真的干净，或者那条查询根本没在检查任何东西（条件恒假、JSON 路径写错、类型转成 NULL）。
 * 本轮就撞上过两种：layers 正向 JOIN 恒不产出 BAD 行；`.length()` 直接报 3143。
 * 所以拿 bd5-dump.out 里留档的"改造前 901"（已知带 chartPreset-only、props 无数值两处缺陷）
 * 逐条喂给同一批 SQL —— 补丁里的原文，不做第二次誊写 —— 能报出 BAD 才算这条自检是活的。
 *
 * 跑法：node devlog/_ay/bd5-negative.mjs && 再用 --force 跑 devlog/_ay/bd5-negative.sql
 */
import fs from 'node:fs'
import path from 'node:path'

const ROOT = process.cwd()
const PATCH = path.join(ROOT, 'deploy/sql/patch/2026-10-18-bd-demo-screen.sql')
const DUMP = path.join(ROOT, 'devlog/bd5-dump.out')
const OUT = path.join(ROOT, 'devlog/_ay/bd5-negative.sql')

function archived(id, tag) {
  for (const line of fs.readFileSync(DUMP, 'utf8').split(/\r?\n/)) {
    if (line.startsWith(`@@${tag} ${id} :: `)) return line.slice(`@@${tag} ${id} :: `.length)
  }
  throw new Error(`留档里找不到 @@${tag} ${id}`)
}

const cmp = archived(901, 'CMP')
const cfg = archived(901, 'CFG')
const q = (v) => `'${v.replace(/'/g, "''")}'`

// 取补丁自检段里的原样 SELECT 语句
const patchText = fs.readFileSync(PATCH, 'utf8')
const region = patchText.slice(patchText.indexOf('-- ============ 自检'))
const raw = [...region.matchAll(/SELECT CONCAT\([\s\S]*?;/g)].map((m) => m[0])
if (raw.length === 0) throw new Error('补丁里没抓到自检语句')

// 把 FROM screen 换成留档 JSON 派生表；其余谓词一字不动
const derived =
  'FROM (SELECT CAST(' + q(cmp) + ' AS JSON) AS components_json, CAST(' + q(cfg) + ' AS JSON) AS config_json, ' +
  '901 AS id, 0 AS deleted, 1 AS status, \'negtest\' AS share_token) s'

const rewritten = raw.map((sql) => {
  const one = sql.replace(/\s+/g, ' ').trim()
  if (one.includes('FROM screen WHERE deleted = 0 AND share_token')) return null // 令牌唯一：全局扫描，跟单行无关
  if (one.includes('variants_json')) return null // 变体检查：留档 901 无变体
  return one
    .replace(/FROM db_screen\.screen s/, derived)
    .replace(/FROM screen s/, derived)
    .replace(/WHERE s\.id IN \(901, 911\) AND s\.deleted = 0/, 'WHERE s.deleted = 0')
    .replace(/WHERE s\.id = 901 AND s\.deleted = 0/, 'WHERE s.deleted = 0')
    .replace(/WHERE s\.deleted = 0 AND/, 'WHERE s.deleted = 0 AND')
})

const keep = rewritten.filter(Boolean)
const header = [
  '-- 由 devlog/_ay/bd5-negative.mjs 生成：把补丁自检段原文对"改造前 901 的留档 JSON"重放',
  '-- 判据：no-chartConfig / preset-only 必须各自报出行（这两条正是当年 901 的缺陷）；',
  '--        报了 = 自检有效，没报 = 自检是死的，本次通过不可信。',
  'SET NAMES utf8mb4;',
  'USE db_screen;',
  '',
]
fs.writeFileSync(OUT, header.concat(keep, ['SELECT \'@@NEG-END\';', '']).join('\n'), 'utf8')
console.log(`已生成 ${path.relative(ROOT, OUT)}（重放 ${keep.length} / ${raw.length} 条自检）`)
