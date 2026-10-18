const fs = require('fs');
const AY = 'E:/wowowo/devlog/_ay';
const GW = 'http://localhost:8080';
const SEG = { auth: 8081, user: 8082, datasource: 8083, etl: 8084, model: 8085, analysis: 8086, dashboard: 8087, screen: 8088, collab: 8089, alert: 8090, ai: 8091, openapi: 8092, admin: 8093, file: 8094, schedule: 8095, monitor: 8096 };

async function call(method, p, body) {
  const t0 = Date.now();
  let status = 'ERR', text = '', json = null;
  try {
    const r = await fetch(GW + p, {
      method,
      headers: body ? { 'Content-Type': 'application/json' } : {},
      body: body ? JSON.stringify(body) : undefined,
      signal: AbortSignal.timeout(12000),
    });
    status = r.status;
    text = (await r.text()).slice(0, 400);
    try { json = JSON.parse(text); } catch { }
  } catch (e) { text = 'ERR:' + e.name + ' ' + e.message; }
  return { method, path: p, status, ms: Date.now() - t0, body: text, envelope: json && typeof json.code !== 'undefined' && typeof json.message !== 'undefined' };
}

function layerOf(r) {
  if (r.status !== 401) return '-';
  return r.envelope ? 'service-or-gateway(json)' : 'plain';
}

(async () => {
  const out = [];
  const push = async (...a) => { const r = await call(...a); out.push(r); return r; };

  // A. 免登白名单逐条真打
  await push('GET', '/api/auth/captcha');
  await push('POST', '/api/auth/login', { username: 'ay_probe_no_such_user', password: 'x', captchaKey: 'ay', captchaCode: '0000' });
  await push('POST', '/api/auth/sms/send', { phone: '100000000000000', terminal: 'pc_web' });
  await push('POST', '/api/auth/sms/login', { phone: '100000000000000', code: '000000', terminal: 'pc_web' });
  await push('GET', '/api/screen/share/ay_nonexistent_token');
  await push('GET', '/api/screen/share/..%2f..%2f..%2fetc%2fpasswd');
  await push('GET', '/api/screen/share/' + 'a'.repeat(4000));
  await push('GET', '/api/screen/share/ay%27%20OR%201%3D1%20--');
  await push('GET', '/api/file/view/ay_nonexistent');
  await push('GET', '/api/admin/config/public');
  await push('GET', '/api/admin/config/public/mourning');
  await push('GET', '/api/admin/ws/public');

  // B. 17 个前缀的路由覆盖：未鉴权应命中网关拒绝（说明 predicate 匹配到了路由），绝不能 404/502/超时
  for (const seg of Object.keys(SEG)) await push('GET', `/api/${seg}/ay_no_such_path`);

  // C. 直连服务层（不经网关）的免登形状
  const direct = [];
  for (const [seg, port] of Object.entries(SEG)) {
    const t0 = Date.now();
    let status = 'ERR', text = '';
    try {
      const r = await fetch(`http://localhost:${port}/api/${seg}/ay_no_such_path`, { signal: AbortSignal.timeout(8000) });
      status = r.status; text = (await r.text()).slice(0, 200);
    } catch (e) { text = 'ERR:' + e.name; }
    direct.push({ seg, port, status, ms: Date.now() - t0, body: text });
  }

  // D. 三条疑点的确认用例（把读数钉死，不是新猜测）
  await push('GET', '/api/admin/config/123');                 // 受保护：应 401
  await push('GET', '/api/admin/config/public');              // 白名单前缀命中 {id}？
  await push('GET', '/api/admin/config/public/');             // 尾斜杠变体
  await push('GET', '/api/admin/config/public/mourning');     // 业务侧 key 白名单
  await push('GET', '/api/screen/share/%2e%2e%2fadmin');      // 编码点斜杠变体
  await push('GET', '/api/screen/share/x%0aevil');            // 换行注入
  await push('GET', '/api/auth/login');                       // GET 而非 POST：白名单只列了路径不区分方法
  await push('DELETE', '/api/auth/captcha');                  // 白名单路径上的非预期方法

  fs.writeFileSync(AY + '/ay2b-http-probe.json', JSON.stringify({ viaGateway: out, direct }, null, 1));
  const bad = out.filter(r => r.status === 'ERR' || (typeof r.status === 'number' && r.status >= 500));
  console.log('calls=' + (out.length + direct.length) + ' 5xx-or-ERR=' + bad.length);
  out.forEach(r => console.log(`  ${r.method} ${r.path.slice(0, 46)} -> ${r.status} ${r.ms}ms envelope=${r.envelope ? 'Y' : 'N'} ${String(r.body).replace(/\s+/g, ' ').slice(0, 60)}`));
  console.log('--- direct-to-service (no gateway):');
  direct.forEach(d => console.log(`  ${d.seg}:${d.port} -> ${d.status} ${d.body.replace(/\s+/g, ' ').slice(0, 70)}`));
  if (bad.length) console.log('!! 5xx/ERR 明细:', JSON.stringify(bad, null, 1));
})();
