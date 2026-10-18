const fs = require('fs');
const cjk = /[\u4e00-\u9fa5]/;

function flat(o, p = '', acc = {}) {
  for (const k in o) {
    const v = o[k];
    const n = p ? p + '.' + k : k;
    if (v && typeof v === 'object' && !Array.isArray(v)) flat(v, n, acc);
    else acc[n] = v;
  }
  return acc;
}

for (const app of ['pc-web', 'admin', 'pc-desktop']) {
  const en = flat(JSON.parse(fs.readFileSync(`apps/${app}/src/locales/en-US.json`, 'utf8')));
  const bad = Object.entries(en).filter(([, v]) => typeof v === 'string' && cjk.test(v));
  console.log(`===== ${app}: en-US 值里仍含中文 = ${bad.length}`);
  bad.forEach(([k, v]) => console.log(`  ${k} = ${v}`));
}
