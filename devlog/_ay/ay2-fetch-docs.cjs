const fs = require('fs');
const path = require('path');
const OUT = 'E:/wowowo/devlog/_ay';
const PORTS = {
  8080: 'gateway-service', 8081: 'auth-service', 8082: 'user-service', 8083: 'datasource-service',
  8084: 'etl-service', 8085: 'model-service', 8086: 'analysis-service', 8087: 'dashboard-service',
  8088: 'screen-service', 8089: 'collab-service', 8090: 'alert-service', 8091: 'ai-service',
  8092: 'openapi-service', 8093: 'admin-service', 8094: 'file-service', 8095: 'schedule-service',
  8096: 'monitor-service',
};
fs.mkdirSync(path.join(OUT, 'docs'), { recursive: true });
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
      fs.writeFileSync(path.join(OUT, 'docs', `${port}-${name}.json`), JSON.stringify(doc));
      for (const [p, ops] of Object.entries(doc.paths || {})) n += Object.keys(ops).length;
    }
    rows.push({ port, name, http, endpoints: n, servers: doc ? (doc.servers || []).map(s => s.url) : [] });
  }
  fs.writeFileSync(path.join(OUT, 'ay2-docs-live.json'), JSON.stringify(rows, null, 1));
  console.log(rows.map(r => `${r.port} ${r.name} ${r.http} ${r.endpoints}`).join('\n'));
  console.log('TOTAL_ENDPOINTS=' + rows.reduce((a, b) => a + b.endpoints, 0));
})();
