// PI6 · alert_event 分片读侧判据（真进程、真两个实例、真扇出归并）
//
// 与 PI5 的差别要先说清楚：openapi_log 是 NONE 路由（按月份轮转实例），所以一个租户 1 的会话
// 天然会打到两个实例；alert_event 是 TENANT 路由（按租户哈希选实例），租户 1 → 桶 0 → ds0。
// 于是"跨实例读"这一条在 alert 上不可能由租户 1 的会话证明 —— 本探针不假装能证，
// 把它单列为 GAP，并把能证的部分（ds1 上确实只有租户 903 的行、桶位算出来确实是 1）用 SQL 钉住。
//
// 能证的判据：
//   A. 扇出覆盖窗口内全部月表，total 与"逐张月表按同一窗口分开数"的基线一致（不重不漏）。
//   B. 归并是全局最新优先，不是把分片结果首尾相接 —— 首页必须整页来自最新的非空月表。
//   C. stats 的 partial=true，且四个数与同窗口 SQL 基线一致（状态三档之和 = 列表 total）。
//   D. 过滤条件与扇出正交：status / ruleId 过滤后的 total 仍与同条件 SQL 基线一致。
//   E. 按 id 改状态真的落到月表：locate() 找得到、update() 改的是月表那一行。
//      这一条同时会暴露"逻辑基表不再跟着改"的分叉，探针把它当读数报出来，不当失败。
//      写的是我自己铺的演示事件，跑完按原值还原，净变化为零。
//
// 登录走产品自带的短信登录路径（固定码 123123），不碰验证码、不清 sms:day 计数。
const http = require('http')
const { execFileSync } = require('child_process')
const { bucketOf, DS0_CONTAINER, DS1_CONTAINER } = require('./shard-config.cjs')

const AUTH_PORT = 17081
const ALERT_PORT = 17090
const PHONE = '13912345678'
const TENANT = 1
const QUERY_MONTHS = 3

/** 凭据走容器里的 MYSQL_ROOT_PASSWORD，不落这条命令行。 */
function sql (container, text) {
  return execFileSync('docker', ['exec', '-i', container, 'sh', '-c',
    'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot -h 127.0.0.1 --default-character-set=utf8mb4 -B --raw'],
  { input: 'SET NAMES utf8mb4;\n' + text, encoding: 'utf8' })
}

/** 取结果集最后一行的数字列（-B --raw 下就是制表符分隔的裸值）。 */
function nums (container, text) {
  return row(container, text).map(v => (v === 'NULL' ? null : Number(v)))
}

/** 取结果集最后一行的原始字符串列 —— 状态、时间这类非数字必须走这条，Number('PENDING') 是 NaN。 */
function row (container, text) {
  const lines = sql(container, text).split('\n').map(l => l.replace(/\r$/, '')).filter(l => l.trim() !== '')
  if (lines.length < 2) throw new Error('SQL 没有结果行: ' + text)
  return lines[lines.length - 1].split('\t')
}

/**
 * 逐月表数出来再求和。
 * 注意 UNION ALL 出来的是"每月一行"，不是总和 —— 直接取最后一行等于只数了最后那张月表，
 * 而窗口里最后一张（202611）恰好是空的，于是基线恒为 0、所有判据假失败。必须外面再套一层 SUM。
 */
function sumOverMonths (container, months, selectBody) {
  const inner = months.map(m => selectBody(m)).join(' UNION ALL ')
  return nums(container, `SELECT SUM(n) FROM (${inner}) t;`)[0]
}

function request (port, method, path, body, token) {
  return new Promise((resolve, reject) => {
    const payload = body === undefined ? null : JSON.stringify(body)
    const headers = { Accept: 'application/json' }
    if (payload) {
      headers['Content-Type'] = 'application/json'
      headers['Content-Length'] = Buffer.byteLength(payload)
    }
    if (token) headers.Authorization = 'Bearer ' + token
    const req = http.request({ host: '127.0.0.1', port, method, path, headers, timeout: 30000 }, res => {
      let text = ''
      res.setEncoding('utf8')
      res.on('data', chunk => { text += chunk })
      res.on('end', () => {
        let json = null
        try { json = JSON.parse(text) } catch (e) { /* 原样带出来，别把 500 的 HTML 页丢掉 */ }
        resolve({ status: res.statusCode, json, text: json ? '' : text.slice(0, 300) })
      })
    })
    req.on('error', reject)
    req.on('timeout', () => req.destroy(new Error('超时')))
    if (payload) req.write(payload)
    req.end()
  })
}

// Result 的成败位是 success / code=0，不是 HTTP 状态码。
function ok (res) { return !!res.json && (res.json.success === true || res.json.code === 0) }

async function login () {
  // 先直接登：上一次发的码在 Redis 里还有 300s 生命周期，重跑探针不该再吃一次每日上限。
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
  const res = await request(ALERT_PORT, 'GET', '/api/alert/event/page?' + qs, undefined, token)
  if (!res.json) return { status: res.status, ok: false, raw: res.text }
  const d = res.json.data || {}
  const rows = d.records || d.list || []
  return {
    status: res.status, ok: ok(res), code: res.json.code, message: res.json.message,
    total: d.total, records: rows.length, rows,
    truncated: d.truncated, shards: d.shards, windowDefaulted: d.windowDefaulted,
    months: Array.from(new Set(rows.map(r => String(r.createTime || '').slice(0, 7)))).sort()
  }
}

/** 服务端窗口：now - QUERY_MONTHS .. now。探针与实现同口径，但月份集合独立算一遍。 */
function windowMonths () {
  const to = new Date()
  const from = new Date(to.getTime())
  from.setMonth(from.getMonth() - QUERY_MONTHS)
  const set = []
  const cursor = new Date(from.getFullYear(), from.getMonth(), 1)
  while (cursor <= to) {
    set.push(String(cursor.getFullYear()) + String(cursor.getMonth() + 1).padStart(2, '0'))
    cursor.setMonth(cursor.getMonth() + 1)
  }
  return { from, to, months: set }
}

const sqlTime = d => d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0') + '-'
  + String(d.getDate()).padStart(2, '0') + ' ' + String(d.getHours()).padStart(2, '0') + ':'
  + String(d.getMinutes()).padStart(2, '0') + ':' + String(d.getSeconds()).padStart(2, '0')

/** 窗口内逐张月表数出来的基线。extra 是裸 SQL 条件（列名都是本文件写死的白名单）。 */
function baseline (container, months, from, to, extra) {
  const bounds = `create_time >= '${sqlTime(from)}' AND create_time <= '${sqlTime(to)}'`
  return sumOverMonths(container, months, m =>
    `SELECT COUNT(*) n FROM \`db_alert\`.\`alert_event_${m}\` WHERE tenant_id = ${TENANT} AND ${bounds}`
    + (extra ? ` AND ${extra}` : ''))
}

/** 同一条条件但不带窗口边界 —— 用来判断"窗口边界进 SQL"这件事在本数据集上有没有区分力。 */
function baselineUnbounded (container, months, extra) {
  return sumOverMonths(container, months, m =>
    `SELECT COUNT(*) n FROM \`db_alert\`.\`alert_event_${m}\` WHERE tenant_id = ${TENANT}`
    + (extra ? ` AND ${extra}` : ''))
}

function sumCounts (container, months, from, to, column) {
  return baseline(container, months, from, to, column)
}

(async () => {
  const token = await login()
  console.log('login ok（短信登录路径，验证码与 sms:day 都没绕）')

  const w = windowMonths()
  const home = bucketOf(TENANT) === 0 ? DS0_CONTAINER : DS1_CONTAINER
  const away = bucketOf(TENANT) === 0 ? DS1_CONTAINER : DS0_CONTAINER
  console.log('窗口 ' + sqlTime(w.from) + ' .. ' + sqlTime(w.to) + ' → 月表 ' + w.months.join(',')
    + '；租户 ' + TENANT + ' 桶位 ' + bucketOf(TENANT) + ' → ' + home)

  const base = baseline(home, w.months, w.from, w.to)
  const unbounded = baselineUnbounded(home, w.months)
  console.log('基线：窗口内 ' + base + ' 行，同月表不带边界 ' + unbounded + ' 行'
    + (base === unbounded ? '（相等 ⇒ 本数据集上"边界进 SQL"没有区分力，这一条只能由单测证）' : '（不等 ⇒ 有区分力）'))

  const a = await page(token, 'pageNum=1&pageSize=1')
  console.log('A 扇出 total:', JSON.stringify({ ...a, rows: undefined }))

  const b = await page(token, 'pageNum=1&pageSize=20')
  const newest = row(home, 'SELECT MAX(create_time) FROM (' + w.months.map(m =>
    `SELECT create_time FROM \`db_alert\`.\`alert_event_${m}\` WHERE tenant_id = ${TENANT}`).join(' UNION ALL ')
    + ') t;')[0]
  const newestMonth = String(newest).slice(0, 7)
  const times = b.rows.map(r => String(r.createTime || ''))
  const nonIncreasing = times.every((t, i) => i === 0 || times[i - 1] >= t)
  console.log('B 归并顺序：首页月份 ' + JSON.stringify(b.months) + '，最新月表 ' + newestMonth
    + '，时间非递增 ' + nonIncreasing + '，首行 ' + times[0])

  const statsRes = await request(ALERT_PORT, 'GET', '/api/alert/event/stats', undefined, token)
  const s = (statsRes.json && statsRes.json.data) || {}
  const pending = sumCounts(home, w.months, w.from, w.to, "status = 'PENDING'")
  const acknowledged = sumCounts(home, w.months, w.from, w.to, "status = 'ACKNOWLEDGED'")
  const resolved = sumCounts(home, w.months, w.from, w.to, "status = 'RESOLVED'")
  const critical = sumCounts(home, w.months, w.from, w.to, "severity = 'CRITICAL'")
  // alert_rule 不分片：启用数就是一条普通计数。旧代码这里恒写 0L（"Would need a separate query"），
  // 而库里实测有启用中的规则 ⇒ 基线必须 > 0，否则"恒 0"也能假绿。
  const enabledRules = nums(home, `SELECT COUNT(*) FROM \`db_alert\`.\`alert_rule\` WHERE tenant_id = ${TENANT} AND enabled = 1 AND deleted = 0;`)[0]
  console.log('C stats:', JSON.stringify(s))
  console.log('C SQL 基线: pending=' + pending + ' acknowledged=' + acknowledged + ' resolved=' + resolved
    + ' critical=' + critical + ' enabledRules=' + enabledRules)

  const d1 = await page(token, 'pageNum=1&pageSize=1&status=PENDING')
  // 最新的"非空"月表：窗口里最后那张（202611）对本租户是空的，按下标取会取到空表。
  const nonEmpty = w.months.filter(m =>
    nums(home, `SELECT COUNT(*) FROM \`db_alert\`.\`alert_event_${m}\` WHERE tenant_id = ${TENANT};`)[0] > 0)
  const latest = nonEmpty[nonEmpty.length - 1]
  const ruleId = nums(home, `SELECT rule_id FROM \`db_alert\`.\`alert_event_${latest}\` WHERE tenant_id = ${TENANT} LIMIT 1;`)[0]
  const d2 = await page(token, 'pageNum=1&pageSize=1&ruleId=' + ruleId)
  const ruleBase = baseline(home, w.months, w.from, w.to, 'rule_id = ' + ruleId)
  console.log('D 过滤正交：status=PENDING total=' + d1.total + '（基线 ' + pending + '）；ruleId=' + ruleId
    + ' total=' + d2.total + '（基线 ' + ruleBase + '）')

  // E 写侧：挑一条 PENDING 事件确认掉，看月表那一行变了没有、逻辑基表那一行变了没有。
  const STATUSES = ['PENDING', 'ACKNOWLEDGED', 'RESOLVED']
  const pick = row(home, `SELECT id, status, DATE_FORMAT(create_time,'%Y%m') FROM \`db_alert\`.\`alert_event_${latest}\` WHERE tenant_id = ${TENANT} AND status = 'PENDING' ORDER BY create_time DESC LIMIT 1;`)
  let e = { skipped: true }
  if (pick && pick[0] && STATUSES.includes(pick[1])) {
    const id = Number(pick[0])
    const before = pick[1]
    const month = pick[2]
    const logicalBefore = row(home, `SELECT status FROM \`db_alert\`.\`alert_event\` WHERE id = ${id};`)[0]
    const ack = await request(ALERT_PORT, 'POST', '/api/alert/event/acknowledge', { id }, token)
    const monthAfter = row(home, `SELECT status FROM \`db_alert\`.\`alert_event_${month}\` WHERE id = ${id};`)[0]
    const logicalAfter = row(home, `SELECT status FROM \`db_alert\`.\`alert_event\` WHERE id = ${id};`)[0]
    // 还原：净变化为零。before 必须先过状态白名单 —— 上一版就是拿 Number('PENDING')=NaN 拼进
    // UPDATE，把演示行写成了字面量 'NaN'。读回来的值也一样要过白名单才认。
    sql(home, `UPDATE \`db_alert\`.\`alert_event_${month}\` SET status = '${before}' WHERE id = ${id};`)
    const restored = row(home, `SELECT status FROM \`db_alert\`.\`alert_event_${month}\` WHERE id = ${id};`)[0]
    e = { skipped: false, id, month, ok: ok(ack), httpStatus: ack.status, before, monthAfter, logicalBefore, logicalAfter, restored }
    console.log('E 按 id 改状态:', JSON.stringify(e))
    if (restored !== before) throw new Error('还原失败，需人工修：id=' + id + ' 月表=' + month + ' 现值=' + restored + ' 应为=' + before)
  } else {
    console.log('E 跳过：' + latest + ' 没有可挑的 PENDING 事件（或读回的状态不在白名单里）')
  }

  // GAP ds1 半区：SQL 能证行在哪、桶位算出来是哪，但没有任何租户 903 的账号，会话级跨实例读证不了。
  const awayTenantRows = nums(away, `SELECT COUNT(*) FROM \`db_alert\`.\`alert_event\` WHERE tenant_id = 903;`)[0]
  const awayMonthRows = w.months.reduce((acc, m) =>
    acc + nums(away, `SELECT COUNT(*) FROM \`db_alert\`.\`alert_event_${m}\` WHERE tenant_id = 903;`)[0], 0)
  const homeMonth903 = w.months.reduce((acc, m) =>
    acc + nums(home, `SELECT COUNT(*) FROM \`db_alert\`.\`alert_event_${m}\` WHERE tenant_id = 903;`)[0], 0)
  const users903 = nums(DS0_CONTAINER, 'SELECT COUNT(*) FROM `db_user`.`sys_user` WHERE tenant_id = 903 AND deleted = 0;')[0]
  console.log('GAP 租户 903：桶位 ' + bucketOf(903) + ' → ' + (bucketOf(903) === 0 ? DS0_CONTAINER : DS1_CONTAINER)
    + '；' + away + ' 月表里 ' + awayMonthRows + ' 行、' + home + ' 月表里 ' + homeMonth903 + ' 行；'
    + away + ' 逻辑基表 ' + awayTenantRows + ' 行；租户 903 的可用账号 ' + users903 + ' 个')

  const checks = [
    ['A total = 逐月表同窗口基线（不重不漏）', a.ok && a.total === base],
    ['A 分片数 = 窗口内月表数 ' + w.months.length, a.shards === w.months.length],
    ['A 未截断且明说窗口是服务端补的', a.truncated === false && a.windowDefaulted === true],
    ['B 首页整页来自最新非空月表（不是首尾相接）', b.ok && b.months.length === 1 && b.months[0] === newestMonth],
    ['B 首页时间非递增', b.ok && nonIncreasing],
    ['C partial=true（这四个数不是全历史）', ok(statsRes) && s.partial === true],
    ['C 四档计数与 SQL 基线一致', s.pendingEvents === pending && s.acknowledgedEvents === acknowledged
      && s.resolvedEvents === resolved && s.criticalEvents === critical],
    ['C 状态三档之和 = 列表 total', s.pendingEvents + s.acknowledgedEvents + s.resolvedEvents === a.total],
    ['C enabledRules 是真计数不是占位 0（API-175）', enabledRules > 0 && s.enabledRules === enabledRules],
    ['D status 过滤后仍与基线一致', d1.ok && d1.total === pending],
    ['D ruleId 过滤后仍与基线一致', d2.ok && d2.total === ruleBase],
    ['E locate+update 打到月表（月表行变 ACKNOWLEDGED）', !e.skipped && e.ok && e.monthAfter === 'ACKNOWLEDGED'],
    ['E 还原成功（净变化为零）', !e.skipped && e.restored === e.before],
    ['GAP ds1 上确有租户 903 的行、ds0 月表上一行都没有', awayMonthRows > 0 && homeMonth903 === 0 && bucketOf(903) === 1]
  ]
  let failed = 0
  for (const [name, pass] of checks) {
    console.log((pass ? 'PASS ' : 'FAIL ') + name)
    if (!pass) failed++
  }

  console.log('\n读数：total=' + a.total + ' 基线=' + base + ' 分片=' + a.shards
    + '；stats partial=' + s.partial + '；E 月表 ' + e.monthAfter + ' / 基表 ' + e.logicalAfter)
  if (!e.skipped && e.logicalBefore === e.logicalAfter && e.monthAfter !== e.before) {
    console.log('分叉（登记，不当失败）：分片打开后状态回写只改月表，逻辑基表 alert_event 那一行仍是 '
      + e.logicalBefore + '。把 shard.enabled 关回去会读到旧状态 —— 这是 PI4 保留基表镜像的代价，须另行收口。')
  }
  console.log('未证（GAP，不当通过）：租户 903 的会话级跨实例读。可用账号 ' + users903
    + ' 个 ⇒ 无法登录成租户 903；ds1 选路只由单测 + 上面的 SQL 行分布证明。')
  if (failed) { console.log('未通过 ' + failed + ' 条'); process.exitCode = 1 }
})().catch(e => { console.error('探针失败: ' + e.message); process.exitCode = 1 })