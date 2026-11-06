// BH3 · 回调链路实跑：HMAC 验签过了之后，请求到底能不能落到 CallbackController，以及结果有没有进 openapi_log。
// app_key/app_secret 由外层用库里的凭据安全路径查出来后从 stdin 喂进来：
// 凭据不进 argv、不进环境变量、不进日志，只在这段进程内存里用于签名。
const http = require('http')
const crypto = require('crypto')
const fs = require('fs')

function post(path, headers, body) {
  return new Promise(res => {
    const req = http.request({
      host: '127.0.0.1', port: 8092, path, method: 'POST',
      headers: Object.assign({ 'Content-Type': 'application/json', 'Content-Length': Buffer.byteLength(body) }, headers),
      timeout: 20000
    }, r => {
      let b = ''
      r.on('data', c => (b += c))
      r.on('end', () => res({ status: r.statusCode, body: b }))
    })
    req.on('timeout', () => { req.destroy(); res({ status: 'ERR', body: 'timeout' }) })
    req.on('error', e => res({ status: 'ERR', body: String(e.message) }))
    req.write(body)
    req.end()
  })
}

const hexSha = (buf) => crypto.createHash('sha256').update(buf).digest('hex')
const sign = (secret, canonical) => crypto.createHmac('sha256', secret).update(canonical).digest('hex')

;(async () => {
  const line = fs.readFileSync(0, 'utf8').split('\n').map(l => l.trim()).filter(Boolean).pop()
  const parts = line.split('\t')
  const appKey = parts[0]
  const appSecret = parts[1]
  console.log('app 已取到: appKey=' + appKey + ' secretChars=' + (appSecret || '').length)

  const uri = '/api/openapi/callback/webhook/test'
  const marker = 'bh-probe-' + Date.now()
  const body = JSON.stringify({ event: 'bh-probe', marker })
  const ts = String(Math.floor(Date.now() / 1000))
  const nonce = crypto.randomUUID()
  const canonical = ['POST', uri, ts, nonce, hexSha(Buffer.from(body))].join('\n')

  const ok = await post(uri, {
    'X-App-Key': appKey, 'X-Timestamp': ts, 'X-Nonce': nonce, 'X-Signature': sign(appSecret, canonical)
  }, body)
  console.log('正向验签 ->', ok.status, ok.body.slice(0, 240))

  // 反向对照一：签名故意错。期望 401 Invalid Signature，且审计行 client_id 为空（未验签不认领归属）
  const bad = await post(uri, {
    'X-App-Key': appKey, 'X-Timestamp': ts, 'X-Nonce': crypto.randomUUID(), 'X-Signature': 'deadbeef'
  }, body)
  console.log('错签名 ->', bad.status, bad.body.slice(0, 240))

  // 反向对照二：头都不带
  const missing = await post(uri, {}, body)
  console.log('缺签名头 ->', missing.status, missing.body.slice(0, 240))

  // 反向对照三：时间戳超出偏差窗口（301 秒前）—— 期望 401 Request expired
  const staleTs = String(Math.floor(Date.now() / 1000) - 4000)
  const staleCanonical = ['POST', uri, staleTs, 'n1', hexSha(Buffer.from(body))].join('\n')
  const stale = await post(uri, {
    'X-App-Key': appKey, 'X-Timestamp': staleTs, 'X-Nonce': 'n1', 'X-Signature': sign(appSecret, staleCanonical)
  }, body)
  console.log('时间戳过期 ->', stale.status, stale.body.slice(0, 240))

  console.log('marker=' + marker)
})()
