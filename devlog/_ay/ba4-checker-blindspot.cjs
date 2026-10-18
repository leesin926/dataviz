/**
 * BA4：量化 ay2-path-diff.cjs 的核对盲区 —— 嵌套泛型实参（.post<R<X>>(）被旧正则整条跳过。
 * 用法: node ba4-checker-blindspot.cjs
 */
const fs = require('fs');
const path = require('path');

const OLD = [
  /\.(get|post|put|delete|patch|download|upload)\s*(?:<[^>]*>)?\s*\(\s*`([^`]*)`/g,
  /\.(get|post|put|delete|patch|download|upload)\s*(?:<[^>]*>)?\s*\(\s*['"]([^'"]*)['"]/g,
];
// 新：泛型实参里允许再嵌一层 <>（只要不含右括号），否则 .post<R<Foo>>('x') 整条不被识别
const NEW = [
  /\.(get|post|put|delete|patch|download|upload)\s*(?:<[^)]*>)?\s*\(\s*`([^`]*)`/g,
  /\.(get|post|put|delete|patch|download|upload)\s*(?:<[^)]*>)?\s*\(\s*['"]([^'"]*)['"]/g,
];

const roots = [
  'E:/wowowo/frontend/packages/api-client/src',
  'E:/wowowo/frontend/apps/admin/src',
  'E:/wowowo/frontend/apps/pc-web/src',
  'E:/wowowo/frontend/apps/pc-desktop/src',
];

function collect(res) {
  const found = new Set();
  const walk = (dir) => {
    for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
      const fp = path.join(dir, e.name);
      if (e.isDirectory()) {
        if (e.name !== 'node_modules') walk(fp);
        continue;
      }
      if (!/\.(ts|vue|js)$/.test(e.name)) continue;
      const src = fs.readFileSync(fp, 'utf8');
      for (const re of res) {
        let m;
        while ((m = re.exec(src))) {
          const p = ('/api/' + m[2].replace(/^\/+/, '')).replace(/\$\{[^}]*\}/g, ':x').replace(/\?.*$/, '').replace(/\/$/, '');
          if (!/^\/api\/[a-z]/.test(p)) continue;
          found.add(m[1].toUpperCase().replace('DOWNLOAD', 'GET').replace('UPLOAD', 'POST') + ' ' + p);
        }
      }
    }
  };
  roots.forEach(walk);
  return found;
}

const o = collect(OLD);
const n = collect(NEW);
const missed = [...n].filter((k) => !o.has(k));
console.log(`old=${o.size} new=${n.size} 仅新正则识别到=${missed.length}`);
missed.sort().forEach((k) => console.log('  ' + k));
