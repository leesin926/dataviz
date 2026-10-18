const fs = require('fs'), path = require('path'), http = require('http')
const AY = 'E:/wowowo/devlog/_ay'
const TOKEN = fs.readFileSync(path.join(AY, 'ay5-token-pc-web.txt'), 'utf8').trim()

function call(method, p, body) {
  return new Promise(res => {
    const data = body ? JSON.stringify(body) : null
    const h = { Authorization: 'Bearer ' + TOKEN }
    if (data) { h['Content-Type'] = 'application/json'; h['Content-Length'] = Buffer.byteLength(data) }
    const req = http.request({ host: '127.0.0.1', port: 8080, path: encodeURI(p), method, headers: h, timeout: 20000 }, r => {
      let b = ''; r.on('data', c => (b += c)); r.on('end', () => {
        let j = null; try { j = JSON.parse(b) } catch {}
        res({ status: r.statusCode, code: j ? j.code : null, msg: j ? String(j.message).slice(0, 90) : b.replace(/\s+/g, ' ').slice(0, 90), ctype: r.headers['content-type'], bytes: b.length, json: j })
      })
    })
    req.on('timeout', () => { req.destroy(); res({ status: 'ERR', msg: 'timeout', bytes: 0 }) })
    req.on('error', e => res({ status: 'ERR', msg: String(e.message), bytes: 0 }))
    if (data) req.write(data); req.end()
  })
}

// 收集 GET 端点（含路径变量名）
const ops = []
for (const f of fs.readdirSync(path.join(AY, 'docs'))) {
  const port = f.slice(0, 4)
  const doc = JSON.parse(fs.readFileSync(path.join(AY, 'docs', f), 'utf8'))
  const needApi = port === '8081' || port === '8082'
  for (const [p0, m] of Object.entries(doc.paths || {})) {
    if (!m.get) continue
    const full = needApi ? '/api' + p0 : p0
    const vars = (full.match(/\{[^}]*\}/g) || []).map(v => v.slice(1, -1))
    ops.push({ port, raw: full, vars, svc: f.replace(/^\d+-/, '').replace(/\.json$/, '') })
  }
}
ops.sort((a, b) => a.vars.length - b.vars.length)

const idCache = new Map()
function valueFor(varName) {
  const k = varName.toLowerCase()
  if (idCache.has(k)) return idCache.get(k)
  if (idCache.has('id')) return idCache.get('id')
  if (/token|sharecode|share_code/.test(k)) return 'demo'
  return '1'
}
function harvest(json) {
  const d = json && json.data
  const rows = Array.isArray(d) ? d : (d && Array.isArray(d.records) ? d.records : (d && Array.isArray(d.list) ? d.list : null))
  const pick = o => { if (!o || typeof o !== 'object') return; for (const [k, v] of Object.entries(o)) { if ((k === 'id' || /Id$/.test(k) || /code$|Code$/.test(k)) && (typeof v === 'number' || typeof v === 'string') && v !== '' && !idCache.has(k.toLowerCase())) idCache.set(k.toLowerCase(), String(v)) } }
  if (rows && rows.length) { pick(rows[0]); pick(rows[0] && rows[0].data) }
  else pick(d)
}

;(async () => {
  const out = []
  for (const o of ops) {
    let p = o.raw
    for (const v of o.vars) p = p.replace('{' + v + '}', valueFor(v))
    if (/(export|download|preview\/export|stream)/i.test(p)) { out.push({ ...o, path: p, skipped: 'export/stream' }); continue }
    const r = await call('GET', p)
    harvest(r.json)
    out.push({ ...o, path: p, ...r })
    const line = `${r.status}/${r.code === null ? '-' : r.code} ${r.bytes}B ${p}`
    const bad = r.status === 'ERR' || Number(r.status) >= 500
    console.log((bad ? '!! ' : '   ') + line + ' | ' + String(r.msg).slice(0, 60))
  }
  fs.writeFileSync(path.join(AY, 'ay6-read-matrix.json'), JSON.stringify({ n: out.filter(x => !x.skipped).length, skipped: out.filter(x => x.skipped).length, idCache: Object.fromEntries(idCache), rows: out }, null, 2))
  const done = out.filter(x => !x.skipped)
  const g = {}
  done.forEach(x => { const k = `${x.status}/${x.code}`; g[k] = (g[k] || 0) + 1 })
  console.log('\n=== 分布 status/bodyCode ==='); Object.entries(g).sort((a, b) => b[1] - a[1]).forEach(([k, v]) => console.log(k, v))
  console.log('=== 5xx/ERR ===', done.filter(x => x.status === 'ERR' || Number(x.status) >= 500).length)
  console.log('=== HTTP200 但 body code!=0 ===', done.filter(x => x.status === 200 && x.code !== 0 && x.code !== null).length)
})()
