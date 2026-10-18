const fs = require('fs');
const path = require('path');

// API-93 判据：白名单条目由 /** 收紧为 /* 之后，"裸前缀"（零尾段）在无 token 时必须回 401，
// 而带真实尾段的分享链接仍要免登可达。两组都要测，否则分不清是"收紧生效"还是"整块坏了"。
const cases = [
  ['/api/screen/share', 'bare, expect 401'],
  ['/api/admin/config/public', 'bare, expect 401'],
  ['/api/file/view', 'bare, expect 401'],
  ['/api/auth/sso', 'bare, expect 401'],
  ['/api/screen/ws/share', 'bare, expect 401'],
];
// 尾段用假值即可：这里要证的是"免登可达（不再 401）"，不是"数据存在"。假 token 会回业务 4xx，
// 那就够了 —— 把它读成 200 是我自己造的第二类假缺陷。
const withTail = [
  ['/api/admin/config/public/mourning_mode', 'tail, expect NOT 401 (免登)'],
  ['/api/screen/share/nope-not-a-token', 'tail, expect NOT 401 (免登)'],
];

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

async function get(url) {
  const t0 = Date.now();
  try {
    const res = await fetch(url);
    const text = await res.text();
    return { url, status: res.status, ms: Date.now() - t0, body: text.slice(0, 200) };
  } catch (e) {
    return { url, status: 'ERR', ms: Date.now() - t0, body: String(e && e.message) };
  }
}

(async () => {
  const out = [];
  for (const [p, note] of cases.concat(withTail)) {
    const r = await get('http://localhost:8080' + p);
    r.note = note;
    out.push(r);
    await sleep(1200);
  }
  fs.writeFileSync(path.join(__dirname, 'ay17-whitelist-noauth.json'), JSON.stringify(out, null, 2));
  for (const r of out) console.log(r.status, '|', r.url, '|', r.note, '|', (r.body || '').replace(/\s+/g, ' ').slice(0, 110));
})();
