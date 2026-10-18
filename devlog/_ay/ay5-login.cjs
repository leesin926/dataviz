const http = require('http')
function post(path, body, token) {
  return new Promise(res => {
    const data = body ? JSON.stringify(body) : null
    const h = { 'Content-Type': 'application/json', 'Content-Length': Buffer.byteLength(data || '') }
    if (token) h.Authorization = 'Bearer ' + token
    const req = http.request({ host: '127.0.0.1', port: 8080, path, method: 'POST', headers: h, timeout: 15000 }, r => {
      let b = ''; r.on('data', c => (b += c)); r.on('end', () => { let j = null; try { j = JSON.parse(b) } catch {} res({ status: r.statusCode, json: j, raw: b.slice(0, 200) }) })
    })
    req.on('timeout', () => { req.destroy(); res({ status: 'ERR', raw: 'timeout' }) })
    req.on('error', e => res({ status: 'ERR', raw: String(e.message) }))
    if (data) req.write(data); req.end()
  })
}
;(async () => {
  const phone = process.argv[2], terminal = process.argv[3] || 'pc-web'
  const send = await post('/api/auth/sms/send', { phone, terminal })
  console.log('send ->', send.status, JSON.stringify(send.json && send.json.code), send.raw.slice(0, 140))
  const login = await post('/api/auth/sms/login', { phone, code: '123123', terminal })
  console.log('login ->', login.status, JSON.stringify(login.json && login.json.code), login.raw.slice(0, 160))
  const tk = login.json && login.json.data && (login.json.data.token || login.json.data.accessToken)
  if (!tk) { console.log('NO TOKEN'); process.exit(1) }
  require('fs').writeFileSync('devlog/_ay/ay5-token-' + terminal + '.txt', tk)
  console.log('TOKEN len=' + tk.length + ' head=' + tk.slice(0, 12) + '...')
})()
