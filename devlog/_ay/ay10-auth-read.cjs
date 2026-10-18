const fs = require('fs')
const path = require('path')
const http = require('http')
const AY = 'E:/wowowo/devlog/_ay'
const TOKEN = fs.readFileSync(path.join(AY, 'ay5-token-pc-web.txt'), 'utf8').trim()
const SLEEP = Number(process.argv[2] || 1150)
// 网关 IP 限流是 60 次/60 秒（RateLimitFilter.IP_RATE_LIMIT），1.15s 间隔 ≈ 52 次/分钟，留一点余量给手动复测
const sleep = ms => new Promise(r => setTimeout(r, ms))

function call(p) {
  return new Promise(res => {
    const req = http.request({ host: '127.0.0.1', port: 8080, path: p, method: 'GET', headers: { Authorization: 'Bearer ' + TOKEN }, timeout: 20000 }, r => {
      let b = ''
      r.on('data', c => (b += c))
      r.on('end', () => {
        let j = null
        try { j = JSON.parse(b) } catch {}
        // 响应体允许不是对象（/api/xxx/exists 直接回裸 boolean），所以 'code' in j 必须先验型
        const isObj = j !== null && typeof j === 'object' && !Array.isArray(j)
        res({ status: r.statusCode, code: isObj && 'code' in j ? j.code : undefined, message: isObj ? j.message : undefined, ctype: String(r.headers['content-type'] || ''), bytes: Buffer.byteLength(b), raw: b.slice(0, 220), json: j })
      })
    })
    req.on('timeout', () => { req.destroy(); res({ status: 'ERR', message: 'timeout', bytes: 0, raw: '' }) })
    req.on('error', e => res({ status: 'ERR', message: String(e.message), bytes: 0, raw: '' }))
    req.end()
  })
}

const ops = []
for (const f of fs.readdirSync(path.join(AY, 'docs'))) {
  const port = f.slice(0, 4)
  const doc = JSON.parse(fs.readFileSync(path.join(AY, 'docs', f), 'utf8'))
  const needApi = port === '8081' || port === '8082'
  for (const [p0, m] of Object.entries(doc.paths || {})) {
    if (!m.get) continue
    const full = needApi ? '/api' + p0 : p0
    const vars = (full.match(/\{[^}]*\}/g) || []).map(v => v.slice(1, -1))
    const q = (m.get.parameters || []).filter(x => x.in === 'query')
    ops.push({ port, svc: f.replace(/^\d+-/, '').replace(/\.json$/, ''), upstream: p0, raw: full, vars, query: q })
  }
}

const idCache = new Map()
function valFor(name, schema) {
  const k = String(name).toLowerCase()
  if (idCache.has(k)) return idCache.get(k)
  if (/(^|_)id$/.test(k) || k === 'id') { if (idCache.has('id')) return idCache.get('id') }
  const en = schema && schema.enum
  if (en && en.length) return String(en[0])
  if (/page(_num|ber)?/.test(k)) return '1'
  if (/size|limit/.test(k)) return '5'
  if (/(start|end)_?(time|date)/.test(k)) return '2026-01-01 00:00:00'
  if (/type|status|platform|terminal|level|format|targettype|resourcetype/.test(k)) return 'dashboard'
  if (schema && schema.type === 'boolean') return 'true'
  if (schema && (schema.type === 'integer' || schema.type === 'number')) return '1'
  return 'dashboard'
}
function fillPath(o) {
  let p = o.raw
  for (const v of o.vars) { p = p.replace('{' + v + '}', encodeURIComponent(valFor(v, null))) }
  const qs = o.query.map(x => x.name + '=' + encodeURIComponent(valFor(x.name, x.schema))).join('&')
  return p + (qs ? (p.includes('?') ? '&' : '?') + qs : '')
}
function harvest(json) {
  const d = json && json.data
  const rows = Array.isArray(d) ? d : d && Array.isArray(d.records) ? d.records : d && Array.isArray(d.list) ? d.list : null
  const pick = o => {
    if (!o || typeof o !== 'object') return
    for (const [k, v] of Object.entries(o)) {
      if (typeof v !== 'number' && typeof v !== 'string') continue
      if (v === '' || String(v).length > 40) continue
      if (k === 'id' || /Id$/.test(k) || /Code$/.test(k) || k === 'key' || k.endsWith('Key')) {
        const kk = k.toLowerCase()
        if (!idCache.has(kk)) idCache.set(kk, String(v))
        if (k === 'id' || /Id$/.test(k)) { if (!idCache.has('id')) idCache.set('id', String(v)) }
      }
    }
  }
  if (rows && rows.length) { pick(rows[0]); if (rows[0].data) pick(rows[0].data) } else pick(d)
}

;(async () => {
  const order = ops.slice().sort((a, b) => a.vars.length - b.vars.length)
  const out = []
  let n = 0
  for (const o of order) {
    const p = fillPath(o)
    if (/(export|download|stream|proxy)/i.test(o.upstream)) { out.push({ svc: o.svc, path: p, upstream: o.upstream, skipped: 'export/proxy 类，留给专项' }); continue }
    const r = await call(p)
    n++
    harvest(r.json)
    out.push({ svc: o.svc, port: o.port, upstream: o.upstream, path: p, status: r.status, code: r.code, message: r.message, bytes: r.bytes, ctype: r.ctype, raw: r.status === 200 && r.code === undefined ? r.raw : undefined })
    const flag = r.status === 'ERR' || Number(r.status) >= 500 ? '!!' : (r.status === 429 ? 'RL' : (Number(r.code) >= 400 || r.code === undefined) && r.status === 200 ? '??' : '  ')
    console.log(flag + ' ' + String(n).padStart(3) + ' ' + r.status + '/' + (r.code === undefined ? '-' : r.code) + ' ' + String(r.bytes).padStart(6) + 'B ' + p + ' | ' + String(r.message === undefined ? r.raw : r.message).replace(/\s+/g, ' ').slice(0, 58))
    if (r.status === 429) { console.log('   限流命中，退避 30s'); await sleep(30000) }
    await sleep(SLEEP)
  }
  const done = out.filter(x => !x.skipped)
  const dist = {}
  done.forEach(x => { const k = x.status + '/' + (x.code === undefined ? '-' : x.code); dist[k] = (dist[k] || 0) + 1 })
  console.log('\n=== 调用数 =', done.length, ' 跳过 =', out.length - done.length, ' 429 =', done.filter(x => x.status === 429).length)
  console.log('=== status/bodyCode 分布 ===')
  Object.entries(dist).sort((a, b) => b[1] - a[1]).forEach(([k, v]) => console.log('   ' + k + '  ' + v))
  console.log('=== 5xx / ERR / 429 ===')
  done.filter(x => x.status === 'ERR' || Number(x.status) >= 500 || x.status === 429).forEach(x => console.log('   ' + x.status + ' ' + x.path + ' | ' + String(x.message).slice(0, 70)))
  console.log('=== 200 但响应体没有 R 信封 ===')
  done.filter(x => x.status === 200 && x.code === undefined).forEach(x => console.log('   ' + x.path + ' | ' + String(x.raw).replace(/\s+/g, ' ').slice(0, 90)))
  console.log('=== HTTP 200 但 body code != 0（伪成功面）===')
  done.filter(x => x.status === 200 && x.code !== undefined && x.code !== 0).forEach(x => console.log('   code=' + x.code + ' ' + x.path + ' | ' + String(x.message).slice(0, 60)))
  console.log('=== 404（网关可达性/映射错配）===')
  done.filter(x => x.status === 404 || x.code === 404).forEach(x => console.log('   ' + x.status + '/' + x.code + ' ' + x.path + ' | ' + String(x.message).slice(0, 60)))
  fs.writeFileSync(path.join(AY, 'ay10-auth-read.json'), JSON.stringify({ dist, idCache: Object.fromEntries(idCache), rows: out }, null, 2))
})()
