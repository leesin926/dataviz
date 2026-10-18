/**
 * BA4：反证 API-106 为什么在 AY2 那轮门禁里是绿的。
 * 同一行调用，四种组合：{旧/新代码} × {旧/新正则}
 * 用法: node ba4-blindspot-proof.cjs
 */
const { execSync } = require('child_process');
const fs = require('fs');

const RE = {
  old: /\.(get|post|put|delete|patch|download|upload)\s*(?:<[^>]*>)?\s*\(\s*['"`]([^'"`]*)['"`]/g,
  new: /\.(get|post|put|delete|patch|download|upload)\s*(?:<[^)]*>)?\s*\(\s*['"`]([^'"`]*)['"`]/g,
};

function calls(src, re) {
  const out = [];
  const r = new RegExp(re.source, 'g');
  let m;
  while ((m = r.exec(src))) out.push(`${m[1].toUpperCase()} ${m[2]}`);
  return out;
}

const head = execSync('git show HEAD:frontend/packages/api-client/src/modules/datasource.ts', { encoding: 'utf8' });
const now = fs.readFileSync('E:/wowowo/frontend/packages/api-client/src/modules/datasource.ts', 'utf8');

for (const [label, src] of [['HEAD(修复前)', head], ['工作区(修复后)', now]]) {
  for (const [rl, re] of [['旧正则', RE.old], ['新正则', RE.new]]) {
    console.log(`${label} × ${rl} -> ${JSON.stringify(calls(src, re))}`);
  }
}
