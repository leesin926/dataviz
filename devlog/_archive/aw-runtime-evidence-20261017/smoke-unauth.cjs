// AW3 未鉴权面冒烟（只读 GET，零凭证）
// 分层判据：网关 401 体 = {code,message,data:null}（无 timestamp/success）；服务 AuthInterceptor 401 体带 timestamp/success
// 探针路径不是猜的：先从各服务 /v3/api-docs 里取真实 GET 端点，再经网关与直连各打一次
const fs = require('fs');
const path = require('path');
const http = require('http');

const OUT = __dirname;
const SERVICES = [
  ['auth', 8081, '/api/auth'], ['user', 8082, '/api/user'], ['datasource', 8083, '/api/datasource'],
  ['etl', 8084, '/api/etl'], ['model', 8085, '/api/model'], ['analysis', 8086, '/api/analysis'],
  ['dashboard', 8087, '/api/dashboard'], ['screen', 8088, '/api/screen'], ['collab', 8089, '/api/collab'],
  ['alert', 8090, '/api/alert'], ['ai', 8091, '/api/ai'], ['openapi', 8092, '/api/openapi'],
  ['admin', 8093, '/api/admin'], ['file', 8094, '/api/file'], ['schedule', 8095, '/api/schedule'],
  ['monitor', 8096, '/api/monitor'],
];

function request(port, urlPath, timeout = 12000, wantBody = false) {
  return new Promise((resolve) => {
    const t0 = Date.now();
    const req = http.get({ host: '127.0.0.1', port, path: urlPath, timeout }, (res) => {
      let body = '';
      res.setEncoding('utf8');
      res.on('data', (c) => (body += c));
      res.on('end', () => {
        let j = null;
        try { j = JSON.parse(body); } catch (e) { /* html/空体 */ }
        resolve({
          status: res.statusCode, ms: Date.now() - t0,
          code: j ? j.code : undefined,
          message: j ? String(j.message).slice(0, 90) : body.slice(0, 60),
          layer: j && 'timestamp' in j && 'success' in j ? 'service' : 'gateway-or-plain',
          ...(wantBody ? { body } : {}),
        });
      });
    });
    req.on('error', (e) => resolve({ status: 'ERR:' + e.code, ms: Date.now() - t0 }));
    req.on('timeout', () => { req.destroy(); resolve({ status: 'TIMEOUT' }); });
  });
}
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

(async () => {
  const rep = { startedAt: new Date().toISOString(), docs: [], gateway: [], direct: [], public: [], internal: [] };

  // A) api-docs：端点计数 + 选一个无路径参数的 GET 端点作探针
  const probes = {};
  for (const [name, port, prefix] of SERVICES) {
    const r = await request(port, '/v3/api-docs', 20000, true);
    let totalGet = 0, pick = null, params = 0;
    if (r.status === 200) {
      const j = JSON.parse(r.body);
      const entries = Object.entries(j.paths || {});
      for (const [p, ops] of entries) {
        if (!ops.get) continue;
        totalGet++;
        if (/\{.*\}/.test(p)) { params++; continue; }
        if (!pick) pick = p;
      }
      fs.writeFileSync(path.join(OUT, `paths-${name}.txt`), entries.map(([p, o]) => Object.keys(o).map((m) => m.toUpperCase() + ' ' + p).join(' ')).join('\n'));
    }
    probes[name] = { port, prefix, pick };
    rep.docs.push({ name, port, apiDocs: r.status, getEndpoints: totalGet, withPathParams: params, probe: pick });
    await sleep(40);
  }

  // B) 经网关打探针：不带 token
  for (const [name] of SERVICES) {
    const { prefix, pick } = probes[name];
    if (!pick) { rep.gateway.push({ name, path: null, status: 'NO-GET-ENDPOINT' }); continue; }
    const p = prefix + pick;
    const r = await request(8080, p);
    rep.gateway.push({ name, path: p, ...r });
    await sleep(200); // 网关 60req/min/IP 固定窗口，须节奏化
  }

  // C) 直连服务端口打同一路径（去掉 /api/<svc> 前缀）：证明服务侧拦截器也在拒
  for (const [name] of SERVICES) {
    const { port, prefix, pick } = probes[name];
    if (!pick) continue;
    const r = await request(port, prefix.replace(/^\/api\/[^/]+/, '') + pick);
    rep.direct.push({ name, port, path: prefix.replace(/^\/api\/[^/]+/, '') + pick, ...r });
    await sleep(40);
  }

  // D) 免登面（设计上应 200）
  for (const p of ['/api/auth/captcha', '/api/admin/config/public', '/api/screen/share/smoke-nope']) {
    const r = await request(8080, p);
    rep.public.push({ path: p, ...r });
    await sleep(250);
  }

  // E) 内部端点经网关必须被拒（Z4/AA1）
  for (const p of ['/api/user/internal/by-username?username=admin', '/api/user/internal/page-role?userId=1']) {
    const r = await request(8080, p);
    rep.internal.push({ path: p, ...r });
    await sleep(250);
  }

  rep.finishedAt = new Date().toISOString();
  fs.writeFileSync(path.join(OUT, 'unauth-report.json'), JSON.stringify(rep, null, 2));

  const brief = (a) => a.map((x) => `${String(x.name || '').padEnd(11)} ${String(x.status).padEnd(5)} ${x.layer || ''} ${String(x.code)} ${String(x.message || '').slice(0, 46)}`).join('\n');
  console.log('=== api-docs ===\n' + rep.docs.map((d) => `${d.name.padEnd(11)} :${d.port} docs=${d.apiDocs} GET=${d.getEndpoints} paramed=${d.withPathParams} probe=${d.probe}`).join('\n'));
  console.log('\n=== via gateway 8080 (no token) ===\n' + brief(rep.gateway));
  console.log('\n=== direct service port (no token) ===\n' + brief(rep.direct));
  console.log('\n=== public surface ===\n' + brief(rep.public));
  console.log('\n=== internal via gateway ===\n' + brief(rep.internal));
})();
