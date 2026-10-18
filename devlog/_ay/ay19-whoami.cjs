const fs = require('fs')
const http = require('http')
const AY = 'E:/wowowo/devlog/_ay'

function call(method, p, body, token) {
  return new Promise(res => {
    const data = body ? JSON.stringify(body) : null
    const headers = { 'Content-Type': 'application/json' }
    if (data) headers['Content-Length'] = Buffer.byteLength(data)
    if (token) headers.Authorization = 'Bearer ' + token
    const req = http.request({ host: '127.0.0.1', port: 8080, path: p, method, headers, timeout: 15000 }, r => {
      let b = ''
      r.on('data', c => (b += c))
      r.on('end', () => { let j = null; try { j = JSON.parse(b) } catch {} res({ status: r.statusCode, json: j, raw: b }) })
    })
    req.on('timeout', () => { req.destroy(); res({ status: 'ERR', raw: 'timeout' }) })
    req.on('error', e => res({ status: 'ERR', raw: String(e.message) }))
    if (data) req.write(data)
    req.end()
  })
}

;(async () => {
  const phone = process.argv[2] || '13800000001'
  const terminal = 'pc-web'
  const send = await call('POST', '/api/auth/sms/send', { phone, terminal })
  const login = await call('POST', '/api/auth/sms/login', { phone, code: '123123', terminal })
  const tk = login.json && login.json.data && (login.json.data.token || login.json.data.accessToken)
  if (!tk) { console.log('LOGIN FAIL', login.status, login.raw.slice(0, 200)); process.exit(1) }
  fs.writeFileSync(AY + '/ay19-token-pc-web.txt', tk)
  console.log('login ok, token len=' + tk.length)
  const me = await call('GET', '/api/user/current', null, tk)
  console.log('current ->', me.status, JSON.stringify(me.json && me.json.code), String(me.raw).replace(/\s+/g, ' ').slice(0, 700))
})()
