const fs = require('fs');
const path = require('path');
const token = fs.readFileSync(path.join(__dirname, 'ay5-token-pc-web.txt'), 'utf8').trim();

const targets = [
  ['API-96 etl/operator/list via gateway', 'GET', '/api/etl/operator/list?page=1&size=5'],
  ['API-96 control: direct 8084', 'GET', 'DIRECT8084/api/etl/operator/list?page=1&size=5'],
  ['API-93 /api/auth/sso (zero-segment whitelist)', 'GET', '/api/auth/sso'],
  ['API-93 /api/file/view (zero-segment whitelist)', 'GET', '/api/file/view'],
  ['API-93 /api/screen/share (zero-segment whitelist)', 'GET', '/api/screen/share'],
  ['API-93 /api/admin/config/public (zero-segment)', 'GET', '/api/admin/config/public'],
  ['internal blocked at gateway', 'GET', '/api/user/internal/by-phone'],
  ['no route matched (control)', 'GET', '/api/nope/nope'],
];

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

async function one(label, method, raw) {
  const url = raw.startsWith('DIRECT8084')
    ? 'http://localhost:8084' + raw.slice('DIRECT8084'.length)
    : 'http://localhost:8080' + raw;
  const headers = { Authorization: 'Bearer ' + token };
  const t0 = Date.now();
  try {
    const res = await fetch(url, { method, headers });
    const text = await res.text();
    return {
      label, method, url, status: res.status, ms: Date.now() - t0,
      body: text.length > 260 ? text.slice(0, 260) + '…' : text,
    };
  } catch (e) {
    return { label, method, url, status: 'ERR', ms: Date.now() - t0, body: String(e && e.message) };
  }
}

(async () => {
  const out = [];
  for (const [label, method, raw] of targets) {
    out.push(await one(label, method, raw));
    await sleep(1200);
  }
  fs.writeFileSync(path.join(__dirname, 'ay15-gateway-recheck.json'), JSON.stringify(out, null, 2));
  for (const r of out) console.log(r.status, '|', r.label, '|', (r.body || '').slice(0, 120).replace(/\s+/g, ' '));
})();
