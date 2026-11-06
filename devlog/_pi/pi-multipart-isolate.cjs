// PI1 · 定位"经网关上传丢 multipart 分片"：同一份请求体，直连 vs 网关、定长 vs chunked 四种组合。
const http = require('http')
const PNG = Buffer.from(
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==', 'base64')

function raw(port, path, body, headers) {
  return new Promise(res => {
    const req = http.request({ host: '127.0.0.1', port, path, method: 'POST', headers, timeout: 20000 }, r => {
      let s = ''
      r.on('data', c => (s += c))
      r.on('end', () => res(r.statusCode + ' ' + s.slice(0, 110)))
    })
    req.on('error', e => res('ERR ' + e.message))
    req.on('timeout', () => { req.destroy(); res('ERR timeout') })
    req.write(body)
    req.end()
  })
}

function multipart(chunked) {
  const b = '----pib' + Date.now().toString(36) + Math.random().toString(36).slice(2)
  const body = Buffer.concat([
    Buffer.from('--' + b + '\r\nContent-Disposition: form-data; name="file"; filename="x.png"\r\nContent-Type: image/png\r\n\r\n'),
    PNG,
    Buffer.from('\r\n--' + b + '--\r\n')
  ])
  const h = { 'Content-Type': 'multipart/form-data; boundary=' + b }
  if (chunked) h['Transfer-Encoding'] = 'chunked'; else h['Content-Length'] = body.length
  return { body, h }
}

;(async () => {
  // 登录用的码必须由同一次运行先发出来：码是一次性的，上次探针已经把它消费掉了
  await new Promise(res => {
    const d = JSON.stringify({ phone: '13912345678', terminal: 'pc-web' })
    const q = http.request({ host: '127.0.0.1', port: 17081, path: '/auth/sms/send', method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Content-Length': Buffer.byteLength(d) } }, x => {
      x.on('data', () => {}); x.on('end', res)
    })
    q.write(d); q.end()
  })
  const tok = await new Promise(res => {
    const d = JSON.stringify({ phone: '13912345678', code: '123123', terminal: 'pc-web' })
    const q = http.request({ host: '127.0.0.1', port: 17081, path: '/auth/sms/login', method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Content-Length': Buffer.byteLength(d) } }, x => {
      let s = ''
      x.on('data', c => (s += c))
      x.on('end', () => { const j = JSON.parse(s); res(j.data && (j.data.token || j.data.accessToken)) })
    })
    q.write(d); q.end()
  })

  const a = multipart(false); a.h.Authorization = 'Bearer ' + tok
  console.log('直连17094 定长   ->', await raw(17094, '/api/file/upload', a.body, a.h))
  const b = multipart(false); b.h.Authorization = 'Bearer ' + tok
  console.log('网关17080 定长   ->', await raw(17080, '/api/file/upload', b.body, b.h))
  const c = multipart(true); c.h.Authorization = 'Bearer ' + tok
  console.log('网关17080 chunked->', await raw(17080, '/api/file/upload', c.body, c.h))
  // 顺带证一条：网关对 /api/file/** 的小体量 JSON 请求正常（排除"整条路由坏了"这种解释）
  console.log('网关17080 file/list ->', await new Promise(res => {
    const q = http.request({ host: '127.0.0.1', port: 17080, path: '/api/file/list?page=1&size=2',
      headers: { Authorization: 'Bearer ' + tok } }, x => {
      let s = ''
      x.on('data', c => (s += c))
      x.on('end', () => res(x.statusCode + ' ' + s.slice(0, 90)))
    })
    q.end()
  }))
})()
