const fs = require('fs');
const path = require('path');
const AY = 'E:/wowowo/devlog/_ay';

// 1) 后端真实端点：来自 /v3/api-docs 的活读数
const live = new Map(); // "GET /ai/status" -> port
const livePaths = new Map(); // "/ai/status" -> Set<method>
for (const f of fs.readdirSync(path.join(AY, 'docs'))) {
  const port = f.slice(0, 4);
  const doc = JSON.parse(fs.readFileSync(path.join(AY, 'docs', f), 'utf8'));
  // 网关只有 auth / user 两条路由带 StripPrefix=1，这两个服务不带 /api 上下文；其余服务自带 /api
  const needApi = port === '8081' || port === '8082';
  for (const [p0, ops] of Object.entries(doc.paths || {})) {
    const p = needApi ? '/api' + p0 : p0;
    const np = p.replace(/\{[^}]*\}/g, ':x').replace(/\/$/, '');
    if (!livePaths.has(np)) livePaths.set(np, new Set());
    for (const m of Object.keys(ops)) {
      livePaths.get(np).add(m.toUpperCase());
      live.set(`${m.toUpperCase()} ${np}`, port);
    }
  }
}

// 2) 前端调用点：api-client 模块 + 三端页面里直接发的 request/http 调用
const roots = [
  'E:/wowowo/frontend/packages/api-client/src',
  'E:/wowowo/frontend/apps/admin/src',
  'E:/wowowo/frontend/apps/pc-web/src',
  'E:/wowowo/frontend/apps/pc-desktop/src',
];
const callReBt = /\.(get|post|put|delete|patch|download|upload)\s*(?:<[^>]*>)?\s*\(\s*`([^`]*)`/g;
const callReQ = /\.(get|post|put|delete|patch|download|upload)\s*(?:<[^>]*>)?\s*\(\s*['"]([^'"]*)['"]/g;
const hits = new Map(); // "GET /ai/status" -> [files]
function walk(dir) {
  for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
    const fp = path.join(dir, e.name);
    if (e.isDirectory()) { if (e.name !== 'node_modules') walk(fp); continue; }
    if (!/\.(ts|vue|js)$/.test(e.name)) continue;
    const src = fs.readFileSync(fp, 'utf8');
    for (const re of [callReBt, callReQ]) {
      let m;
      while ((m = re.exec(src))) {
        let verb = m[1].toUpperCase();
        if (verb === 'DOWNLOAD' || verb === 'UPLOAD') verb = m[1] === 'upload' ? 'POST' : 'GET';
        const raw = m[2];
        const p = ('/api/' + raw.replace(/^\/+/, '')).replace(/\$\{[^}]*\}/g, ':x').replace(/\?.*$/, '').replace(/\/$/, '');
        if (!/^\/api\/[a-z]/.test(p)) continue;
        const k = `${verb} ${p}`;
        if (!hits.has(k)) hits.set(k, []);
        hits.get(k).push(fp.replace(/\\/g, '/').replace('E:/wowowo/frontend/', '') + ' {' + raw + '}');
      }
    }
  }
}
roots.forEach(walk);

// 3) 集合差
const liveList = [...livePaths.keys()];
function fuzzy(p) {
  const a = p.split('/');
  return liveList.filter(q => {
    const b = q.split('/');
    return b.length === a.length && a.every((s, i) => s === b[i] || s === ':x' || b[i] === ':x');
  });
}
const orphanClient = []; // 前端在调，后端文档里没有
const paramGuess = [];   // 靠参数段猜中的（取值域要人工确认）
const methodMismatch = [];
for (const [k, files] of hits) {
  const [verb, p] = k.split(' ');
  if (livePaths.has(p)) {
    if (!livePaths.get(p).has(verb)) methodMismatch.push({ call: k, live: [...livePaths.get(p)].join('/'), files });
  } else if (p.includes(':x')) {
    const cand = fuzzy(p).filter(q => livePaths.get(q).has(verb));
    if (cand.length) paramGuess.push({ call: k, candidates: cand, files });
    else orphanClient.push({ call: k, files });
  } else orphanClient.push({ call: k, files });
}
const guessedLive = new Set();
for (const g of paramGuess) for (const q of g.candidates) for (const v of livePaths.get(q)) guessedLive.add(`${v} ${q}`);
const noClient = [];
for (const [p, verbs] of livePaths) {
  for (const v of verbs) if (!hits.has(`${v} ${p}`) && !guessedLive.has(`${v} ${p}`)) noClient.push(`${v} ${p}`);
}

orphanClient.sort((a, b) => a.call.localeCompare(b.call));
const out = {
  liveEndpointCount: live.size,
  clientCallCount: hits.size,
  orphanClient,
  paramGuess,
  methodMismatch,
  noClientCount: noClient.length,
  noClientByService: noClient.reduce((acc, s) => {
    const seg = s.split('/')[2] || '?';
    acc[seg] = (acc[seg] || 0) + 1;
    return acc;
  }, {}),
};
fs.writeFileSync(path.join(AY, 'ay2-path-diff.json'), JSON.stringify(out, null, 1));
console.log(`live=${live.size} client=${hits.size} orphanClient=${orphanClient.length} methodMismatch=${methodMismatch.length} noClient=${noClient.length}`);
console.log('--- 前端在调但后端文档没有：');
orphanClient.forEach(o => console.log('  ' + o.call + '  <- ' + o.files[0] + (o.files.length > 1 ? ` (+${o.files.length - 1})` : '')));
console.log('--- 路径在但方法不匹配：');
console.log('--- 靠参数段猜中（需人工确认取值域）：');
paramGuess.forEach(o => console.log('  ' + o.call + ' -> ' + o.candidates.join(', ') + '  <- ' + o.files[0]));
methodMismatch.forEach(o => console.log(`  ${o.call}  live=${o.live}  <- ${o.files[0]}`));
console.log('--- 后端有实现、前端零调用（按服务域计数）：');
console.log('  ' + JSON.stringify(out.noClientByService));
