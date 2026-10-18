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

const cjk = /[\u4e00-\u9fa5]/;
const apps = process.argv.slice(2);
for (const app of apps) {
  const files = walk(`apps/${app}/src`).filter((p) => !p.includes('locales'));
  const hits = [];
  for (const f of files) {
    const lines = fs.readFileSync(f, 'utf8').split(/\r?\n/);
    lines.forEach((raw, i) => {
      if (!cjk.test(raw)) return;
      const t = raw.trim();
      if (t.startsWith('//') || t.startsWith('*') || t.startsWith('/*') || t.startsWith('<!--')) return;
      if (/label=|placeholder=|title=|content=|description=/.test(t) === false
        && /[`'"][^`'"]*[\u4e00-\u9fa5]/.test(t) === false) return;
      hits.push(`${f}:${i + 1}: ${t.slice(0, 120)}`);
    });
  }
  console.log(`\n===== ${app}: ${hits.length} lines with CJK`);
  hits.forEach((h) => console.log(h));
}
