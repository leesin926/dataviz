const fs = require('fs');
const path = require('path');

function walk(dir, acc = []) {
  for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
    if (e.name === 'node_modules') continue;
    const p = path.join(dir, e.name);
    if (e.isDirectory()) walk(p, acc);
    else if (/\.(vue|ts)$/.test(e.name)) acc.push(p);
  }
  return acc;
}

function flat(o, p = '', acc = {}) {
  for (const k in o) {
    const v = o[k];
    const n = p ? p + '.' + k : k;
    if (v && typeof v === 'object' && !Array.isArray(v)) flat(v, n, acc);
    else acc[n] = v;
  }
  return acc;
}

const app = process.argv[2];
const zh = flat(JSON.parse(fs.readFileSync(`apps/${app}/src/locales/zh-CN.json`, 'utf8')));
const zhKeys = Object.keys(zh);
const files = walk(`apps/${app}/src`).filter((p) => !p.includes('locales'));

const used = new Map();
const dynamic = [];
for (const f of files) {
  const src = fs.readFileSync(f, 'utf8');
  const re = /(?:^|[^A-Za-z0-9_$.])t\(\s*(['"`])([^'"`]*)\1/gm;
  let m;
  while ((m = re.exec(src))) {
    const key = m[2];
    if (!used.has(key)) used.set(key, f);
  }
  const dre = /(?:^|[^A-Za-z0-9_$.])t\(\s*[`'"][^'"`]*\$\{/gm;
  while ((m = dre.exec(src))) dynamic.push(`${f}:${src.slice(0, m.index).split('\n').length}`);
}

const missing = [...used.keys()].filter((k) => !zhKeys.includes(k));
console.log(`===== ${app}: used=${used.size} zhKeys=${zhKeys.length} 未定义=${missing.length} 动态拼键=${dynamic.length}`);
missing.forEach((k) => console.log(`  MISSING ${k}  (${used.get(k)})`));
dynamic.forEach((d) => console.log(`  DYNAMIC ${d}`));
