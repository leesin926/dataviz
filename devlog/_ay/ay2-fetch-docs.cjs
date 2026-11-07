const fs = require('fs');
const path = require('path');
const AY = 'E:/wowowo/devlog/_ay';
// 快照目录走 argv，默认仍是 AY2 那一轮的 docs：复跑不该覆盖上一轮已入账的证据。
// PI1 之后端口整体迁到 17080-17096，所以这一轮的读数以 `docs-pi17` 另存一份。
const OUT_DIR = path.join(AY, process.argv[2] || 'docs');
const PORTS = {
  17080: 'gateway-service', 17081: 'auth-service', 17082: 'user-service', 17083: 'datasource-service',
  17084: 'etl-service', 17085: 'model-service', 17086: 'analysis-service', 17087: 'dashboard-service',
  17088: 'screen-service', 17089: 'collab-service', 17090: 'alert-service', 17091: 'ai-service',
  17092: 'openapi-service', 17093: 'admin-service', 17094: 'file-service', 17095: 'schedule-service',
  17096: 'monitor-service',
};
fs.mkdirSync(OUT_DIR, { recursive: true });
(async () => {
  const rows = [];
  for (const [port, name] of Object.entries(PORTS)) {
    let doc = null, http = '000';
    try {
      const r = await fetch(`http://localhost:${port}/v3/api-docs`, { signal: AbortSignal.timeout(8000) });
      http = r.status;
      if (r.ok) doc = await r.json();
    } catch (e) { http = 'ERR:' + e.name; }
    let n = 0;
    if (doc) {
      fs.writeFileSync(path.join(OUT_DIR, `${port}-${name}.json`), JSON.stringify(doc));
      for (const [p, ops] of Object.entries(doc.paths || {})) n += Object.keys(ops).length;
    }
    rows.push({ port, name, http, endpoints: n, servers: doc ? (doc.servers || []).map(s => s.url) : [] });
  }
  fs.writeFileSync(path.join(OUT_DIR, 'docs-live.json'), JSON.stringify(rows, null, 1));
  console.log(rows.map(r => `${r.port} ${r.name} ${r.http} ${r.endpoints}`).join('\n'));
  console.log('TOTAL_ENDPOINTS=' + rows.reduce((a, b) => a + b.endpoints, 0));
})();
