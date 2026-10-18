#!/usr/bin/env node
/**
 * bd5-contract.cjs 的负向自测：每种缺陷注入一次，checker 必须点着"那一条"检查。
 *
 * 理由同 bd5-negative.mjs（那边管 SQL 自检，这边管 JS 契约核对）：
 * 一个恒真的检查比漏检更危险 —— 它会给出"全部通过"的假凭证。
 * 判据不是"退出码非 0"就算过：还要求失败的 label 命中预期关键字，
 * 否则一个语法错误也能让所有变异集体"通过"负向测试。
 *
 * 跑法：node devlog/_ay/bd5-contract-negative.cjs
 */
const path = require('path')
const { spawnSync } = require('child_process')

const SELF = path.join(__dirname, 'bd5-contract.cjs')

/** kind → 期望在失败清单里出现的关键字（取自 checker 的 label，不是随便一个词） */
const CASES = [
  ['db-drift', '库里组件与声明逐字段一致'],
  ['unknown-type', '有渲染器'],
  ['no-chartConfig', '必须带 chartConfig'],
  ['bad-chartType', '在 ChartType 枚举里'],
  ['outside-canvas', '落在'],
  ['drop-from-layer', '没有游离组件'],
  ['dangling-layer', '图层不引用不存在的组件'],
  ['static-field-typo', '在 staticData.columns 里'],
  ['live-screen-static', '走数据集真链路'],
  ['bad-aggregation', '在 SqlBuilder 白名单里'],
  ['bad-sort-order', '排序方向'],
  ['hidden-column', '被 911 组件点名引用'],
  ['white-ink', '不与白底撞色'],
  ['statCard-string', 'statCard 的 props.value 必须是数字'],
  ['progress-negative', 'progress 的 percent'],
  ['table-ragged', '每行格数与表头一致'],
  ['clock-format', '在 DATE_FORMATS 里'],
  ['mobile-pc-only', '允许投放到 mobile'],
  ['gauge-drift', '同口径'],
  ['rows-zero', '等价查询有行'],
  ['probe-bare-order', '落在外层投影或 GROUP BY 里'],
]

let bad = 0
for (const [kind, expect] of CASES) {
  const r = spawnSync(process.execPath, [SELF], {
    env: { ...process.env, BD5_MUTATE: kind }, encoding: 'utf8', cwd: process.cwd(),
  })
  const out = (r.stdout || '') + (r.stderr || '')
  const fails = /^  ✗ (.*)$/gm
  const hit = [...out.matchAll(fails)].map((m) => m[1])
  const matched = hit.filter((l) => l.includes(expect))
  const ok = r.status === 1 && matched.length > 0
  if (!ok) bad++
  console.log(`${ok ? '✓' : '✗'} ${kind.padEnd(20)} 退出码 ${r.status}，失败 ${String(hit.length).padStart(2)} 条，`
    + `命中「${expect}」${matched.length} 条${ok ? '' : `\n    实际失败：\n      ${hit.join('\n      ') || out.slice(0, 300)}`}`)
}

// 反向对照：不带变异时必须 0 失败（否则上面的"命中"没有意义）
{
  const r = spawnSync(process.execPath, [SELF], { encoding: 'utf8', cwd: process.cwd() })
  const clean = r.status === 0 && !/^  ✗ /m.test(r.stdout || '')
  if (!clean) bad++
  console.log(`${clean ? '✓' : '✗'} ${'（无变异对照）'.padEnd(20)} 退出码 ${r.status}`)
}

console.log(bad === 0
  ? `负向自测通过：${CASES.length} 种缺陷各有其检查，且干净数据下不报`
  : `负向自测失败 ${bad} 项`)
process.exit(bad === 0 ? 0 : 1)
