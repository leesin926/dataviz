// PI5 · 跨实例读判据（真进程、真两个实例、真扇出归并）
//
// 判据不是"接口 200"，而是这三件事同时成立：
//   A. 只查 202609 时能出数据 —— 而 ds0 上的 openapi_log_202609 是空的（实测 0 行），
//      行只在 ds1（3307）上。这一条只有"真的打到第二个实例"才可能成立。
//   B. 跨月区间的 total 与"逐月表 + 客户端可见集"分开数出来的基线一致（扇出没漏片、没重片）。
//   C. 不给时间范围时 windowDefaulted=true 且分片数按默认窗口来（服务端收窄了范围这件事必须说出来）。
//
// 登录走产品自带的短信登录路径（固定码 123123），不碰验证码、不清 sms:day 计数；端口是 PI1 之后的 17081/17092。
const http = require('http')
const { execFileSync } = require('child_process')
const { bucketOf, DS0_CONTAINER, DS1_CONTAINER } = require('./shard-config.cjs')

const AUTH_PORT = 17081
const OPENAPI_PORT = 17092
const PHONE = '13912345678'

/** 只读/只写的分片库对账：走容器里的 MYSQL_ROOT_PASSWORD，凭据不落这条命令行。 */
function sql (container, text) {
  const out = execFileSync('docker', ['exec', '-i', container, 'sh', '-c',
    'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot -h 127.0.0.1 --default-character-set=utf8mb4 -B --raw'],
  { input: 'SET NAMES utf8mb4;\n' + text, encoding: 'utf8' })
  const line = out.split('\n').filter(l => /^\d+$/.test(l.trim()))
  return Number(line[line.length - 1])
}

const countMonth = (container, month) =>
  sql(container, 'SELECT COUNT(*) FROM `db_openapi`.`openapi_log_' + month + '`;')
const countLogical = (container) =>
  sql(container, 'SELECT COUNT(*) FROM `db_openapi`.`openapi_log`;')

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
      res.on('data', chunk => { text += chunk })
      res.on('end', () => {
        let json = null
        try { json = JSON.parse(text) } catch (e) { /* 原样带出来，别把 500 的 HTML 页丢掉 */ }
        resolve({ status: res.statusCode, json, text: json ? '' : text.slice(0, 200) })
      })
    })
    req.on('error', reject)
    req.on('timeout', () => req.destroy(new Error('超时')))
    if (payload) req.write(payload)
    req.end()
  })
}

// Result 的成败位是 success / code=0（不是 HTTP 状态码），这里按信封读，别拿 200 当成功。
function ok (res) { return !!res.json && (res.json.success === true || res.json.code === 0) }

async function login () {
  // 先直接登：上一次发的码在 Redis 里还有 300s 生命周期。
  // 这样重跑探针不会再吃一次「每日发送上限」，也不会撞 60s 重发闸门。
  let res = await request(AUTH_PORT, 'POST', '/auth/sms/login', { phone: PHONE, code: '123123', terminal: 'pc-web' })
  if (!ok(res)) {
    const sent = await request(AUTH_PORT, 'POST', '/auth/sms/send', { phone: PHONE, terminal: 'pc-web' })
    if (!ok(sent)) throw new Error('发送验证码失败: ' + JSON.stringify(sent.json || sent.text))
    res = await request(AUTH_PORT, 'POST', '/auth/sms/login', { phone: PHONE, code: '123123', terminal: 'pc-web' })
  }
  if (!ok(res)) throw new Error('登录失败: ' + JSON.stringify(res.json || res.text))
  const d = res.json.data || {}
  const token = d.token || d.accessToken || d.tokenValue
  if (!token) throw new Error('登录成功但没拿到 token: ' + JSON.stringify(Object.keys(d)))
  return token
}

async function page (token, qs) {
  const res = await request(OPENAPI_PORT, 'GET', '/api/openapi/log/page?' + qs, undefined, token)
  if (!res.json) return { status: res.status, ok: false, raw: res.text }
  const d = res.json.data || {}
  const rows = d.records || d.list || []
  return {
    status: res.status,
    ok: ok(res),
    code: res.json.code,
    message: res.json.message,
    total: d.total,
    records: rows.length,
    truncated: d.truncated,
    shards: d.shards,
    windowDefaulted: d.windowDefaulted,
    months: Array.from(new Set(rows.map(r => String(r.createTime || '').slice(0, 7)))).sort()
  }
}

(async () => {
  const token = await login()
  console.log('login ok（走短信登录路径，验证码与 sms:day 都没绕）')

  const a = await page(token, 'startTime=2026-09-01T00:00:00&endTime=2026-09-30T23:59:59&pageSize=50')
  console.log('A 只查 202609（ds0 上这张月表是空的）:', JSON.stringify(a))

  const b = await page(token, 'startTime=2026-11-01T00:00:00&endTime=2026-11-30T23:59:59&pageSize=50')
  console.log('B 只查 202611（行在 ds0）:', JSON.stringify(b))

  const c = await page(token, 'startTime=2026-09-01T00:00:00&endTime=2026-11-30T23:59:59&pageSize=50')
  console.log('C 跨三个月一次读（要同时打两个实例）:', JSON.stringify(c))

  const d = await page(token, 'pageSize=50')
  console.log('D 不给时间范围（服务端补默认窗口）:', JSON.stringify(d))

  // E 写侧：一条未验签的回调要落进"这个月该去的那个实例"的月表，且逻辑表一行都不该多。
  // 桶位由 shard-config 现算（与 Java/SQL 同一条式子），不写死 202611 —— 探针明天还要能跑。
  const now = new Date()
  const month = String(now.getFullYear()) + String(now.getMonth() + 1).padStart(2, '0')
  const bucket = bucketOf(month)
  const home = bucket === 0 ? DS0_CONTAINER : DS1_CONTAINER
  const away = bucket === 0 ? DS1_CONTAINER : DS0_CONTAINER
  const beforeMonth = countMonth(home, month)
  const beforeLogical = countLogical(DS0_CONTAINER)
  const beforeAway = countMonth(away, month)
  const refusal = await request(OPENAPI_PORT, 'GET', '/api/openapi/callback/pi5-probe')
  const afterMonth = countMonth(home, month)
  const afterLogical = countLogical(DS0_CONTAINER)
  const awayMonth = countMonth(away, month)
  console.log('E 写侧落月表（' + month + ' 桶位实测 ' + bucket + ' → ' + home + '）：拒绝 HTTP ' + refusal.status
    + '，该月表 ' + beforeMonth + '→' + afterMonth + '，另一实例同月表 ' + beforeAway + '→' + awayMonth
    + '，逻辑表 ' + beforeLogical + '→' + afterLogical)

  const checks = [
    ['A 必须从 ds1 读到 9 月行', a.ok && a.records > 0 && a.shards === 1],
    ['B 11 月行从 ds0 读到', b.ok && b.records > 0 && b.shards === 1],
    ['C 跨月 total = A + B（扇出不重不漏）', c.ok && c.total === a.total + b.total],
    ['C 分片数 = 3 且未截断', c.shards === 3 && c.truncated === false],
    ['D 明确告知窗口是服务端补的', d.ok && d.windowDefaulted === true],
    ['D 默认 3 个月回看跨 4 张月表', d.shards === 4],
    ['E 拒绝行进月表、不进逻辑表，且落在月份桶位那个实例', refusal.status === 401
      && afterMonth === beforeMonth + 1 && afterLogical === beforeLogical && awayMonth === beforeAway]
  ]
  let failed = 0
  for (const [name, ok] of checks) {
    console.log((ok ? 'PASS ' : 'FAIL ') + name)
    if (!ok) failed++
  }
  console.log('\n判据读数：A=' + a.total + ' B=' + b.total + ' C=' + c.total + ' D=' + d.total
    + '，分片 A/B/C/D = ' + a.shards + '/' + b.shards + '/' + c.shards + '/' + d.shards)
  if (failed) { console.log('未通过 ' + failed + ' 条'); process.exitCode = 1 }
})().catch(e => { console.error('探针失败: ' + e.message); process.exitCode = 1 })
