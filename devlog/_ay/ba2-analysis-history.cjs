const http = require('http')
const fs = require('fs')
const AY = 'E:/wowowo/devlog/_ay'

const sleeps = ms => new Promise(r => setTimeout(r, ms))

function call(method, p, body, token) {
  return new Promise(res => {
    const data = body === undefined || body === null ? null : JSON.stringify(body)
    const headers = { 'Content-Type': 'application/json' }
    if (data) headers['Content-Length'] = Buffer.byteLength(data)
    if (token) headers.Authorization = 'Bearer ' + token
    const req = http.request({ host: '127.0.0.1', port: 8080, path: p, method, headers, timeout: 15000 }, r => {
      let b = ''
      r.on('data', c => (b += c))
      r.on('end', () => {
        let j = null
        try { j = JSON.parse(b) } catch {}
        const code = j && typeof j === 'object' && !Array.isArray(j) && 'code' in j ? j.code : '-'
        res({ status: r.statusCode, code, json: j, raw: b })
      })
    })
    req.on('timeout', () => { req.destroy(); res({ status: 'ERR', code: '-', raw: 'timeout' }) })
    req.on('error', e => res({ status: 'ERR', code: '-', raw: String(e.message) }))
    if (data) req.write(data)
    req.end()
  })
}

const rows = []
let token = null

async function step(name, method, p, body) {
  await sleeps(1300)
  const r = await call(method, p, body, token)
  rows.push({
    name,
    req: method + ' ' + p + (body === undefined ? '' : ' ' + JSON.stringify(body)),
    read: r.status + '/' + r.code,
    msg: r.json && r.json.message ? r.json.message : '',
    body: r.raw.length > 400 ? r.raw.slice(0, 400) + '…' : r.raw
  })
  console.log((r.status + '/' + r.code).padEnd(9), name, '| msg=' + (rows[rows.length - 1].msg || '-'))
  return r
}

;(async () => {
  await call('POST', '/api/auth/sms/send', { phone: '13912345678', terminal: 'pc-web' })
  const login = await call('POST', '/api/auth/sms/login', { phone: '13912345678', code: '123123', terminal: 'pc-web' })
  token = login.json && login.json.data && (login.json.data.token || login.json.data.accessToken)
  if (!token) { console.log('LOGIN FAIL', login.status, login.raw.slice(0, 160)); process.exit(1) }
  console.log('login ok (token 不落盘)')

  // A1/A2：补丁前的两条 500（Unknown column 'deleted' in 'where clause'）
  const a1 = await step('A1 GET /api/analysis/history', 'GET', '/api/analysis/history?page=1&size=5')
  const a2 = await step('A2 GET /api/analysis/query/history', 'GET', '/api/analysis/query/history?page=1&size=5')

  // A3：INSERT 半区。save 走 queryHistoryMapper.insert，正是补丁注释说的"只补 deleted 的话写入仍进不来"
  const a3 = await step('A3 POST /api/analysis/query/save', 'POST', '/api/analysis/query/save', {
    datasetId: 1, dimensions: [], metrics: [], limit: 10
  })
  const savedId = a3.json && typeof a3.json.data === 'number' ? a3.json.data : null

  // A4：把刚落库那行读回来（证明 deleted=0 的注入条件不会把它滤掉）
  const a4 = await step('A4 GET /api/analysis/query/history 复读', 'GET', '/api/analysis/query/history?page=1&size=5')
  const echoed = savedId !== null && String(a4.raw).includes('"id":' + savedId)

  const verdict = {
    'A1 history 不再 500': a1.status !== 500 && a1.code !== 500,
    'A2 query/history 不再 500': a2.status !== 500 && a2.code !== 500,
    'A3 save 落库拿到 id': savedId !== null,
    'A4 新行能读回（deleted 过滤没误杀）': echoed
  }
  console.log('\n=== 判据 ===')
  Object.keys(verdict).forEach(k => console.log((verdict[k] ? 'PASS ' : 'FAIL ') + k))
  console.log('待清理：db_analysis.query_history id=' + savedId + '（我自己写的这一行）')

  fs.writeFileSync(AY + '/ba2-analysis-history.json', JSON.stringify({ rows, verdict, savedId }, null, 2))
})()
