// API-170 的 monitor 半边判据：同一个入参形状，改前 400（绑定器压根不认 ISO），改后 200。
// 为什么还要配一条"坏值必须仍然 400"：只证好值通过，说明不了绑定器真的在按 ISO 解析 ——
// 也可能只是它这次什么都能收。两条一起才把"接受 ISO"与"仍然拒绝非法值"钉住。
const http = require('http')

const GW = 17080
const AUTH_PORT = 17081
const PHONE = '13912345678'

function request (port, method, path, body, token) {
  return new Promise((resolve, reject) => {
    const payload = body === undefined ? null : JSON.stringify(body)
    const headers = { Accept: 'application/json' }
    if (payload) {
      headers['Content-Type'] = 'application/json'
      headers['Content-Length'] = Buffer.byteLength(payload)
    }
    if (token) headers.Authorization = 'Bearer ' + token
    const req = http.request({ host: '127.0.0.1', port, method, path, headers, timeout: 20000 }, res => {
      let text = ''
      res.setEncoding('utf8')
      res.on('data', c => { text += c })
      res.on('end', () => {
        let json = null
        try { json = JSON.parse(text) } catch (e) { /* 500 的 HTML 页要原样带出来 */ }
        resolve({ status: res.statusCode, json, text: json ? '' : text.slice(0, 200) })
      })
    })
    req.on('error', reject)
    req.on('timeout', () => req.destroy(new Error('超时')))
    if (payload) req.write(payload)
    req.end()
  })
}

const ok = res => !!res.json && (res.json.success === true || res.json.code === 0)

async function login () {
  let res = await request(AUTH_PORT, 'POST', '/auth/sms/login', { phone: PHONE, code: '123123', terminal: 'pc-web' })
  if (!ok(res)) {
    await request(AUTH_PORT, 'POST', '/auth/sms/send', { phone: PHONE, terminal: 'pc-web' })
    res = await request(AUTH_PORT, 'POST', '/auth/sms/login', { phone: PHONE, code: '123123', terminal: 'pc-web' })
  }
  if (!ok(res)) throw new Error('登录失败: ' + JSON.stringify(res.json || res.text))
  const d = res.json.data || {}
  return d.token || d.accessToken || d.tokenValue
}

;(async () => {
  const token = await login()
  // 走网关（17080），这样同时证 /api/monitor 那条路由与新端口段是通的
  const base = '/api/monitor/metrics/history?serviceName=auth-service&metricName=cpu'

  const iso = await request(GW, 'GET',
    base + '&startTime=2026-11-01T00:00:00&endTime=2026-11-07T00:00:00', undefined, token)
  const garbage = await request(GW, 'GET',
    base + '&startTime=2026-13-45T00:00:00&endTime=2026-11-07T00:00:00', undefined, token)

  console.log('1) ISO 形状（管理端 value-format 发的就是这个）:', iso.status,
    iso.json ? JSON.stringify(iso.json).slice(0, 160) : iso.text)
  console.log('2) 非法月份（13 月 45 日）:', garbage.status,
    garbage.json ? JSON.stringify(garbage.json).slice(0, 160) : garbage.text)

  const checks = [
    ['ISO 入参不再被绑定器拒掉（改前恒 400 Failed to convert）',
      !(iso.status === 400 && /Failed to convert/.test(JSON.stringify(iso.json || iso.text)))],
    ['绑定器真的在按 ISO 解析：非法值仍然 400 并点名入参',
      garbage.status === 400 && /startTime|Failed to convert|DateTimeFormat/.test(JSON.stringify(garbage.json || garbage.text))]
  ]
  let failed = 0
  for (const [name, pass] of checks) {
    console.log((pass ? 'PASS ' : 'FAIL ') + name)
    if (!pass) failed++
  }
  if (failed) { console.log('未通过 ' + failed + ' 条'); process.exitCode = 1 }
})().catch(e => { console.error('探针失败: ' + e.message); process.exitCode = 1 })
