// BH1 · MinIO 运行时闭环：真登录 → 真上传 → 真下载 → 真删除，全程只走 HTTP 与容器文件系统。
// 为什么不用单测代替：单测证明的是"代码按契约调 SDK"，这里要证明的是"服务起来后这条路真的能用"，
// 两者的证据层级不同。token 只在内存里用，不落盘、不打印。
const http = require('http')
const fs = require('fs')

const H = '127.0.0.1'
const PNG = Buffer.from(
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==',
  'base64')

function raw(method, port, path, headers, body) {
  return new Promise(res => {
    const req = http.request({ host: H, port, path, method, headers, timeout: 20000 }, r => {
      const chunks = []
      r.on('data', c => chunks.push(c))
      r.on('end', () => res({ status: r.statusCode, buf: Buffer.concat(chunks) }))
    })
    req.on('timeout', () => { req.destroy(); res({ status: 'ERR', buf: Buffer.from('timeout') }) })
    req.on('error', e => res({ status: 'ERR', buf: Buffer.from(String(e.message)) }))
    if (body) req.write(body)
    req.end()
  })
}

const json = (r) => { try { return JSON.parse(r.buf.toString('utf8')) } catch { return null } }

;(async () => {
  const out = { steps: [] }
  const say = (name, detail) => { out.steps.push({ name, ...detail }); console.log(name, '->', JSON.stringify(detail)) }

  // 1) 取 token：走产品自带的短信登录路径（演示号 admin=13912345678，开发态固定码 123123）。
  //    刻意不动密码登录的验证码闸门——那道闸门要人来解，脚本解它等于绕过它，是明确不许做的事。
  const smsBody = JSON.stringify({ phone: '13912345678', terminal: 'pc-web' })
  const send = await raw('POST', 8081, '/auth/sms/send',
    { 'Content-Type': 'application/json', 'Content-Length': Buffer.byteLength(smsBody) }, smsBody)
  const sj = json(send)
  say('sms-send', { status: send.status, code: sj && sj.code, msg: sj && sj.message })

  const loginBody = JSON.stringify({ phone: '13912345678', code: '123123', terminal: 'pc-web' })
  const login = await raw('POST', 8081, '/auth/sms/login',
    { 'Content-Type': 'application/json', 'Content-Length': Buffer.byteLength(loginBody) }, loginBody)
  const lj = json(login)
  const token = lj && lj.data && (lj.data.token || lj.data.accessToken)
  say('sms-login', { status: login.status, code: lj && lj.code, msg: lj && lj.message, hasToken: !!token })
  if (!token) { fs.writeFileSync('E:/wowowo/devlog/_bh/bh-minio-roundtrip.json', JSON.stringify(out, null, 2)); process.exit(1) }

  const auth = { Authorization: 'Bearer ' + token }

  // 2) 真上传（multipart/form-data 手工拼，字段与 FileController.upload 对齐）
  const boundary = '----bhminioroundtrip' + Date.now()
  const part = (name, value) => Buffer.from(
    `--${boundary}\r\nContent-Disposition: form-data; name="${name}"\r\n\r\n${value}\r\n`)
  const head = Buffer.concat([
    part('bizType', 'bh-live'),
    Buffer.from(`--${boundary}\r\nContent-Disposition: form-data; name="file"; filename="bh-live.png"\r\nContent-Type: image/png\r\n\r\n`),
    PNG,
    Buffer.from(`\r\n--${boundary}--\r\n`)
  ])
  const up = await raw('POST', 8094, '/api/file/upload',
    { ...auth, 'Content-Type': 'multipart/form-data; boundary=' + boundary, 'Content-Length': head.length }, head)
  const uj = json(up)
  const data = uj && uj.data
  say('upload', { status: up.status, code: uj && uj.code, msg: uj && uj.message, id: data && data.id, storageType: data && data.storageType })
  if (!data || !data.id) { fs.writeFileSync('E:/wowowo/devlog/_bh/bh-minio-roundtrip.json', JSON.stringify(out, null, 2)); process.exit(1) }

  // 3) 记录里的存储类型必须是 minio（而不是被当前配置误导成别的）
  const info = await raw('GET', 8094, '/api/file/info/' + data.id, auth)
  const ij = json(info)
  say('info', { status: info.status, storageType: ij && ij.data && ij.data.storageType, bucket: ij && ij.data && ij.data.bucket })

  // 4) 真下载：字节必须与上传的 PNG 完全一致
  const dl = await raw('GET', 8094, '/api/file/download/' + data.id, auth)
  say('download', { status: dl.status, bytes: dl.buf.length, identicalToUploaded: dl.buf.equals(PNG) })

  // 5) 免登直读（图片放行）：不带 Authorization 也应取到同一份字节
  const view = await raw('GET', 8094, '/api/file/view/' + data.id, {})
  say('view-unauthenticated', { status: view.status, bytes: view.buf.length, identicalToUploaded: view.buf.equals(PNG) })

  // 6) 容器侧对账：对象真的落在 MinIO 的数据目录里（应用说存下了 ≠ 盘上真有，两边都要看）
  const stored = data.storedName || ''
  const shell = "for d in $(ls /data/dataviz 2>/dev/null); do ls -l /data/dataviz/$d 2>/dev/null; done | grep -c . ; ls /data/dataviz/" +
    stored.replace(/\/[^/]+$/, '') + ' 2>&1 | head -5'
  let container = { error: 'skipped' }
  try {
    container = { listing: require('child_process')
      .execSync('docker exec dataviz-minio sh -c ' + JSON.stringify(shell), { encoding: 'utf8' }).trim() }
  } catch (e) {
    container = { error: String(e.message).slice(0, 160) }
  }
  say('container-side', { storedName: stored, objectDirVisible: String(container.listing || container.error).includes(stored.split('/').pop()) })
  if (container.listing) console.log(container.listing.split('\n').slice(0, 4).join('\n'))

  // 7) 真删除
  const del = await raw('DELETE', 8094, '/api/file/' + data.id, auth)
  const dj = json(del)
  say('delete', { status: del.status, code: dj && dj.code, msg: dj && dj.message })

  // 8) 删除后容器与库里都不该留下它
  let after = { error: 'skipped' }
  try {
    after = { listing: require('child_process')
      .execSync('docker exec dataviz-minio sh -c ' + JSON.stringify('ls /data/dataviz/' + stored.replace(/\/[^/]+$/, '') + ' 2>&1 | head -5'), { encoding: 'utf8' }).trim() }
  } catch (e) {
    after = { error: String(e.message).slice(0, 160) }
  }
  say('container-after-delete', { stillVisible: String(after.listing || '').includes(stored.split('/').pop()), listing: after.listing || after.error })

  fs.writeFileSync('E:/wowowo/devlog/_bh/bh-minio-roundtrip.json', JSON.stringify(out, null, 2))
  console.log('storedName-for-container-check:', data.storedName || (ij && ij.data && ij.data.filePath))
})()
