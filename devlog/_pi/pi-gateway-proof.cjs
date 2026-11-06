// PI1 · 换端口之后的端到端判据：一切从网关 17080 进，证 16 条静态路由、跨服务默认值、
// 以及 BH 留下的"网关侧回调免登三条"到底通不通。
// 用法：printf 'SELECT app_key,app_secret ...' | docker exec -i dataviz-mysql ... | node pi-gateway-proof.cjs
// app_secret 从 stdin 进来，只用于签名，不打印、不落盘。
const http = require('http')
const fs = require('fs')
const crypto = require('crypto')

function call(port, method, path, body, headers) {
  return new Promise(res => {
    // Buffer 走原样（multipart），字符串直接发，其余才 JSON 化。
    // 之前这里无条件 JSON.stringify，把 multipart 的 Buffer 变成了 {"type":"Buffer",...}，
    // 于是 file-service 报 "Required request part 'file' is not present" ⇒ 那个 500 是探针的错，不是平台的。
    const data = body == null ? null : (Buffer.isBuffer(body) || typeof body === 'string' ? body : JSON.stringify(body))
    const h = Object.assign({}, headers)
    if (data) {
      if (!h['Content-Type']) h['Content-Type'] = 'application/json'
      h['Content-Length'] = Buffer.byteLength(data)
    }
    const req = http.request({ host: '127.0.0.1', port, path, method, headers: h, timeout: 25000 }, r => {
      const chunks = []
      r.on('data', c => chunks.push(c))
      r.on('end', () => res({ status: r.statusCode, buf: Buffer.concat(chunks) }))
    })
    req.on('timeout', () => { req.destroy(); res({ status: 'ERR', buf: Buffer.from('timeout') }) })
    req.on('error', e => res({ status: 'ERR', buf: Buffer.from(String(e.message)) }))
    if (data) req.write(data)
    req.end()
  })
}
const GW = 17080
const j = (r) => { try { return JSON.parse(r.buf.toString('utf8')) } catch { return null } }
const say = (n, o) => console.log(n.padEnd(34), JSON.stringify(o))

;(async () => {
  const secretLine = fs.readFileSync(0, 'utf8').split('\n').map(s => s.trim()).filter(Boolean).pop() || '\t'
  const [appKey, appSecret] = secretLine.split('\t')

  // 1) 网关 → auth-service（免登）：证 /api/auth/* 路由与 17081
  const send = await call(GW, 'POST', '/api/auth/sms/send', { phone: '13912345678', terminal: 'pc-web' })
  say('gw→auth sms/send', { status: send.status, code: j(send) && j(send).code })
  const login = await call(GW, 'POST', '/api/auth/sms/login', { phone: '13912345678', code: '123123', terminal: 'pc-web' })
  const token = j(login) && j(login).data && (j(login).data.token || j(login).data.accessToken)
  say('gw→auth sms/login', { status: login.status, hasToken: !!token })
  const auth = token ? { Authorization: 'Bearer ' + token } : {}

  // 2) 网关 → datasource-service（17083）
  const ds = await call(GW, 'GET', '/api/datasource/list?page=1&size=5', null, auth)
  const dsj = j(ds)
  say('gw→datasource list', { status: ds.status, code: dsj && dsj.code, total: dsj && dsj.data && dsj.data.total })

  // 3) 网关 → file-service（17094，minio 模式）：上传 → 下载 → 删除
  const PNG = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==', 'base64')
  const b = '----pigate' + Date.now()
  const mp = Buffer.concat([
    Buffer.from(`--${b}\r\nContent-Disposition: form-data; name="file"; filename="pi-gw.png"\r\nContent-Type: image/png\r\n\r\n`),
    PNG, Buffer.from(`\r\n--${b}--\r\n`)
  ])
  const up = await call(GW, 'POST', '/api/file/upload', mp, { 'Content-Type': 'multipart/form-data; boundary=' + b, ...auth })
  const upj = j(up)
  const fileId = upj && upj.data && upj.data.id
  say('gw→file upload(minio)', { status: up.status, code: upj && upj.code, id: fileId, storageType: upj && upj.data && upj.data.storageType })
  if (fileId) {
    const dl = await call(GW, 'GET', '/api/file/download/' + fileId, null, auth)
    say('gw→file download', { status: dl.status, bytes: dl.buf.length, identical: dl.buf.equals(PNG) })
    const del = await call(GW, 'DELETE', '/api/file/' + fileId, null, auth)
    say('gw→file delete', { status: del.status, code: j(del) && j(del).code })
  }

  // 4) 网关 → openapi-service（17092）三条回调免登：这是 BH 留给用户的那一格
  if (appKey && appSecret) {
    const uri = '/api/openapi/callback/webhook/test'
    const body = JSON.stringify({ event: 'pi-gw', ts: Date.now() })
    const ts = String(Math.floor(Date.now() / 1000))
    const nonce = crypto.randomUUID()
    const canonical = ['POST', uri, ts, nonce, crypto.createHash('sha256').update(body).digest('hex')].join('\n')
    const sig = crypto.createHmac('sha256', appSecret).update(canonical).digest('hex')
    const cb = await call(GW, 'POST', uri, body, {
      'Content-Type': 'application/json', 'X-App-Key': appKey, 'X-Timestamp': ts, 'X-Nonce': nonce, 'X-Signature': sig
    })
    say('gw→openapi callback(签名)', { status: cb.status, body: cb.buf.toString('utf8').slice(0, 120) })
    const cbDeny = await call(GW, 'POST', uri, body, { 'Content-Type': 'application/json' })
    say('gw→openapi callback(无头)', { status: cbDeny.status, body: cbDeny.buf.toString('utf8').slice(0, 120) })
  } else {
    say('gw→openapi callback', { skipped: 'stdin 没带 app_key/app_secret' })
  }

  // 5) 反向判据：网关仍在旧端口上不该有监听（由外部 netstat 证），这里证未带 token 打受保护面回 401
  const noAuth = await call(GW, 'GET', '/api/file/list', null, {})
  say('gw 未带token 打受保护面', { status: noAuth.status, body: noAuth.buf.toString('utf8').slice(0, 90) })
})()
