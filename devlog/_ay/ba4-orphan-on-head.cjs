/**
 * BA4：把门禁盲区欠的账一次算清 —— 用"修好的正则"扫 HEAD（修复前）的全部前端调用点，
 * 对活栈 /v3/api-docs 快照做差集，列出 AY2 那轮因为漏扫而没登记的孤儿/方法错配。
 * 用法: node ba4-orphan-on-head.cjs
 */
const { execSync } = require('child_process');
const fs = require('fs');
const path = require('path');

const DOCS = 'E:/wowowo/devlog/_ay/docs-ba4';
const roots = ['frontend/packages/api-client/src', 'frontend/apps/admin/src', 'frontend/apps/pc-web/src', 'frontend/apps/pc-desktop/src'];

const livePaths = new Map();
for (const f of fs.readdirSync(DOCS)) {
  const port = f.slice(0, 4);
  const doc = JSON.parse(fs.readFileSync(path.join(DOCS, f), 'utf8'));
  const needApi = port === '8081' || port === '8082';
  for (const [p0, ops] of Object.entries(doc.paths || {})) {
    const p = (needApi ? '/api' + p0 : p0).replace(/\{[^}]*\}/g, ':x').replace(/\/$/, '');
    if (!livePaths.has(p)) livePaths.set(p, new Set());
    for (const m of Object.keys(ops)) livePaths.get(p).add(m.toUpperCase());
  }
}

// 两种字面量分开扫：引号串不含反引号，反引号串整条取。混在一个字符类里会在模板串的 'enable' 处截断（AY2 脚本原本就是分开的）
const CALL = /\.(get|post|put|delete|patch|download|upload)\s*(?:<[^)]*>)?\s*\(\s*['"]([^'"]*)['"]/g;
const TB = /\.(get|post|put|delete|patch|download|upload)\s*(?:<[^)]*>)?\s*\(\s*`([^`]*)`/g;

const files = execSync(`git ls-tree -r HEAD --name-only -- ${roots.join(' ')}`, { encoding: 'utf8' })
  .split('\n').filter((f) => /\.(ts|vue|js)$/.test(f));

const orphan = [];
const mismatch = [];
let n = 0;
for (const f of files) {
  let src;
  try {
    src = execSync(`git show HEAD:${f}`, { encoding: 'utf8' });
  } catch {
    continue;
  }
  for (const re of [CALL, TB]) {
    let m;
    const r = new RegExp(re.source, 'g');
    while ((m = r.exec(src))) {
      let verb = m[1].toUpperCase();
      if (verb === 'DOWNLOAD' || verb === 'UPLOAD') verb = m[1] === 'upload' ? 'POST' : 'GET';
      const p = ('/api/' + m[2].replace(/^\/+/, '')).replace(/\$\{[^}]*\}/g, ':x').replace(/\?.*$/, '').replace(/\/$/, '');
      if (!/^\/api\/[a-z]/.test(p)) continue;
      n++;
      if (livePaths.has(p)) {
        if (!livePaths.get(p).has(verb)) mismatch.push(`${verb} ${p}  live=${[...livePaths.get(p)].join('/')}  <- ${f}`);
      } else if (p.includes(':x')) {
        // 带参数段的调用：按段数+同位通配找候选，候选里没有该方法的才算孤儿
        const a = p.split('/');
        const cand = [...livePaths.keys()].filter((q) => {
          const b = q.split('/');
          return b.length === a.length && a.every((s, i) => s === b[i] || s === ':x' || b[i] === ':x');
        });
        if (!cand.some((q) => livePaths.get(q).has(verb))) {
          orphan.push(`${verb} ${p}  (候选路径=${cand.join(',') || '无'})  <- ${f}`);
        }
      } else {
        orphan.push(`${verb} ${p}  <- ${f}`);
      }
    }
  }
}
console.log(`HEAD 前端调用点(新正则可见)=${n} 文件数=${files.length}`);
console.log(`--- 孤儿(后端无此路径) ${orphan.length} 条：`);
[...new Set(orphan)].sort().forEach((o) => console.log('  ' + o));
console.log(`--- 方法错配 ${mismatch.length} 条：`);
[...new Set(mismatch)].sort().forEach((o) => console.log('  ' + o));
