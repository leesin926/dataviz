const http = require('http')
const fs = require('fs')
const AY = 'E:/wowowo/devlog/_ay'

const sleeps = ms => new Promise(r => setTimeout(r, ms))

function call(method, p, body, token) {
  return new Promise(res => {
    const data = body === undefined || body === null ? null : JSON.stringify(body)
    const headers = { 'Content-Type': 'application/json' }
    if (data) headers['Content-Length'] = Buffer.byteLength(data)
    if (token) headers.Authorization = 'Bearer ' + token
    const req = http.request({ host: '127.0.0.1', port: 8080, path: p, method, headers, timeout: 15000 }, r => {
      let b = ''
      r.on('data', c => (b += c))
      r.on('end', () => {
        let j = null
        try { j = JSON.parse(b) } catch {}
        // 两轴：HTTP 状态 + 信封 code。dashboard-service 成功面回裸值（无 code）⇒ 记 '-'
        const code = j && typeof j === 'object' && !Array.isArray(j) && 'code' in j ? j.code : '-'
        res({ status: r.statusCode, code, json: j, raw: b })
      })
    })
    req.on('timeout', () => { req.destroy(); res({ status: 'ERR', code: '-', raw: 'timeout' }) })
    req.on('error', e => res({ status: 'ERR', code: '-', raw: String(e.message) }))
    if (data) req.write(data)
    req.end()
  })
}

const rows = []
const H = { phone: '13912345678', terminal: 'pc-web' }

async function step(name, method, p, body, expect) {
  await sleeps(1300)
  const r = await call(method, p, body, token)
  rows.push({
    name,
    req: method + ' ' + p + (body === undefined ? '' : ' ' + JSON.stringify(body)),
    read: r.status + '/' + r.code,
    msg: r.json && r.json.message ? r.json.message : '',
    expect: expect || '-',
    body: r.raw.length > 300 ? r.raw.slice(0, 300) + '…' : r.raw
  })
  console.log((r.status + '/' + r.code).padEnd(9), name, '| msg=' + (rows[rows.length - 1].msg || '-'))
  return r
}

let token = null

;(async () => {
  // 用演示账号的口令登录，不走短信：同一个手机号每天只有 10 发验证码（SmsCodeService.DAILY_SEND_LIMIT），
  // BA 轮已经把 admin 那台的配额跑满，再用短信路只会读到「今日验证码发送次数已达上限」——
  // 那是配额，不是服务缺陷。口令是我自己按 API-17 处置补进库的演示口令，属允许的演示账号。
  const login = await call('POST', '/api/auth/login', { username: 'admin', password: 'admin123', terminal: H.terminal })
  token = login.json && login.json.data && (login.json.data.token || login.json.data.accessToken)
  if (!token) { console.log('LOGIN FAIL', login.status, login.raw.slice(0, 200)); process.exit(1) }
  console.log('login ok (我自己用演示账号口令登录，token 不落盘)')

  // ---- S1~S5 API-100：组件更新按自己的主键取行，父级不动 ----
  const dashA = await step('S1 建仪表盘 A', 'POST', '/api/dashboard', { name: 'zz_ba3_dashA', description: 'Z1 判据用' })
  const A = dashA.json
  const wid = (await step('S2 在 A 下建组件', 'POST', '/api/dashboard/widget', { dashboardId: A, widgetType: 'bar', title: 'zz_ba3_widget', configJson: '{}', dataConfigJson: '{}', positionJson: '{"x":0,"y":0,"w":4,"h":4}', sort: 1 })).json
  await step('S3 只改 title', 'PUT', '/api/dashboard/widget', { id: wid, title: 'zz_ba3_widget-renamed' })
  if (typeof A !== 'number' || typeof wid !== 'number') { console.log('ABORT: 前置步没拿到数字 id —— A=' + JSON.stringify(A) + ' wid=' + JSON.stringify(wid)); fs.writeFileSync(AY + '/ba3-z1-criteria.json', JSON.stringify({ rows, aborted: true }, null, 2)); process.exit(1) }
  const back1 = await step('S4 读回组件', 'GET', '/api/dashboard/widget/' + wid)
  const hijack = await step('S5 PUT 里带 dashboardId=999999999（旧 DTO 的字段）', 'PUT', '/api/dashboard/widget', { id: wid, dashboardId: 999999999, title: 'zz_ba3_widget-hijack' })
  const back2 = await step('S6 再读回', 'GET', '/api/dashboard/widget/' + wid)
  const noId = await step('S7 PUT 不带 id', 'PUT', '/api/dashboard/widget', { title: 'zz_ba3_noId' })

  // ---- S7b 缺 configJson（库里该列 NOT NULL 无默认）----
  await step('S7b 建组件 缺 configJson', 'POST', '/api/dashboard/widget', { dashboardId: A, widgetType: 'bar', title: 'zz_ba3_noconfig', sort: 1 })
  await step('S7c PUT 的 id 传成对象（畸形 body）', 'PUT', '/api/dashboard/widget', { id: { a: 1 }, title: 'zz_ba3_badbody' })
  // ---- S8~S10 API-101：入参校验回 400 而不是 500 ----
  await step('S8 建仪表盘 空 body', 'POST', '/api/dashboard', {})
  await step('S9 建仪表盘 name 为空白', 'POST', '/api/dashboard', { name: '   ' })
  await step('S10 PUT 仪表盘 不带 id', 'PUT', '/api/dashboard', { name: 'zz_ba3_noId' })

  // ---- S11 API-102：孤儿组件被拒 ----
  await step('S11 建组件 父级不存在', 'POST', '/api/dashboard/widget', { dashboardId: 999999999, widgetType: 'bar', title: 'zz_ba3_orphan', configJson: '{}', dataConfigJson: '{}', positionJson: '{}', sort: 1 })
  await step('S11b 建组件 缺 configJson', 'POST', '/api/dashboard/widget', { dashboardId: A, widgetType: 'bar', title: 'zz_ba3_noconfig2', sort: 1 })
  await step('S11c 更新时把 configJson 传空串（探针，未修）', 'PUT', '/api/dashboard/widget', { id: wid, configJson: '' })

  // ---- S12~S13 陈旧 jar 那条：软删后读取回 404 而不是 500 ----
  const dashB = await step('S12 建仪表盘 B', 'POST', '/api/dashboard', { name: 'zz_ba3_dashB' })
  const B = dashB.json
  await step('S13 软删 B', 'DELETE', '/api/dashboard/' + B)
  await step('S14 读软删的 B', 'GET', '/api/dashboard/' + B)
  const listA = await step('S15 A 的组件列表（确认没多出孤儿）', 'GET', '/api/dashboard/widget/list/' + A)

  // ---- 清理：组件表没有 delete 端点，留 SQL 由外层执行 ----
  await step('S16 软删 A', 'DELETE', '/api/dashboard/' + A)

  const verdict = {
    'API-100 title 生效': back1.json && back1.json.title === 'zz_ba3_widget-renamed',
    'API-100 父级不变（S4）': back1.json && back1.json.dashboardId === A,
    'API-100 父级不被 dashboardId 改写（S6）': back2.json && back2.json.dashboardId === A,
    'API-100 缺 id 回 400': noId.code === 400,
    'API-101 空 body 回 400': rows.filter(r => r.name.startsWith('S8 '))[0].read === '200/400',
    'API-101 空白名回 400': rows.filter(r => r.name.startsWith('S9 '))[0].read === '200/400',
    'API-101 PUT 缺 id 回 400': rows.filter(r => r.name.startsWith('S10 '))[0].read === '200/400',
    'API-102 父级不存在回 404': rows.filter(r => r.name.startsWith('S11 '))[0].read === '200/404',
    '软删读取回 404（不是 500）': rows.filter(r => r.name.startsWith('S14 '))[0].read === '200/404',
    'API-103 缺 configJson 回 400（修后期望）': rows.filter(r => r.name.startsWith('S7b'))[0].read === '200/400',
    'API-104 畸形 body 回 400（修后期望）': rows.filter(r => r.name.startsWith('S7c'))[0].read === '400/400',
    'API-103 父级合法但缺 configJson 也回 400': rows.filter(r => r.name.startsWith('S11b'))[0].read === '200/400',
    'S11c 更新传空 configJson（读数，不判）': rows.filter(r => r.name.startsWith('S11c'))[0].read,
    'A 下组件数=1（无孤儿）': Array.isArray(listA.json) && listA.json.length === 1
  }
  console.log('\n=== 判据 ===')
  Object.keys(verdict).forEach(k => console.log((verdict[k] === true ? 'PASS ' : verdict[k] === false ? 'FAIL ' : 'READ ') + k + (typeof verdict[k] === 'string' ? ' = ' + verdict[k] : '')))
  fs.writeFileSync(AY + '/ba3-z1-criteria.json', JSON.stringify({ rows, verdict, hijackRead: hijack.status + '/' + hijack.code }, null, 2))
  console.log('\n待清理（组件表无 DELETE 端点）：dashboard_widget id=' + wid + '；仪表盘 A=' + A + ' B=' + B + ' 已软删')
  console.log('校验 SQL：SELECT id,dashboard_id,title FROM db_dashboard.dashboard_widget WHERE id=' + wid)
})()
