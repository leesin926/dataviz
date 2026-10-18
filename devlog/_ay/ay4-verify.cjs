const http = require('http')
function call(method, path) {
  return new Promise(res => {
    const req = http.request({ host: '127.0.0.1', port: 8080, path, method, timeout: 8000 }, r => {
      let b = ''; r.on('data', c => (b += c)); r.on('end', () => res({ path, status: r.statusCode, body: b.slice(0, 160) }))
    })
    req.on('timeout', () => { req.destroy(); res({ path, status: 'ERR', body: 'timeout' }) })
    req.on('error', e => res({ path, status: 'ERR', body: String(e.message) }))
    req.end()
  })
}
;(async () => {
  const cases = [
    ['GET', '/api/admin/config/public'],
    ['GET', '/api/screen/share'],
    ['GET', '/api/file/view'],
    ['GET', '/api/auth/sso'],
    ['GET', '/api/admin/config/public/deadline_switch'],
    ['GET', '/api/screen/share/tok123'],
    ['GET', '/api/file/view/2080000000000000001.png'],
    ['GET', '/api/auth/sso/login'],
    ['GET', '/api/auth/sso/callback'],
    ['GET', '/api/screen/ws/share'],
    ['GET', '/api/auth/captcha'],
    ['GET', '/api/dashboard/list'],
    ['GET', '/api/admin/config/123'],
  ]
  const out = []
  for (const [m, p] of cases) {
    const r = await call(m, p)
    out.push({ method: m, ...r })
    console.log(`${m} ${p} -> ${r.status} | ${r.body.replace(/\s+/g, ' ').slice(0, 110)}`)
  }
  require('fs').writeFileSync('devlog/_ay/ay4-verify.json', JSON.stringify(out, null, 2))
})()
