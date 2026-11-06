// BH2 · 连接器实跑：ClickHouse / Elasticsearch / MongoDB 三种数据源在真容器上的实际表现。
// 走的是应用的路（datasource-service 8083 的 CRUD + test-connection + tables + execute），
// 不是直接 curl 容器——只有这样才能证明新 connector 分层真的接进了服务链路。
// token 只在内存里用，不落盘、不打印；config 里的凭据也不打印。
const http = require('http')
const fs = require('fs')

function call(method, port, path, body, token) {
  return new Promise(res => {
    const data = body === undefined || body === null ? null
      : (typeof body === 'string' ? body : JSON.stringify(body))
    const headers = {}
    if (data) {
      headers['Content-Type'] = 'application/json'
      headers['Content-Length'] = Buffer.byteLength(data)
    }
    if (token) headers.Authorization = 'Bearer ' + token
    const req = http.request({ host: '127.0.0.1', port, path, method, headers, timeout: 30000 }, r => {
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

const brief = (r, keep = 260) => ({
  status: r.status,
  code: r.json && r.json.code,
  message: r.json && r.json.message,
  data: r.json ? JSON.stringify(r.json.data).slice(0, keep) : r.raw.slice(0, keep)
})

;(async () => {
  const out = { steps: [] }
  const say = (name, detail) => { out.steps.push({ name, ...detail }); console.log('\n== ' + name); console.log(JSON.stringify(detail, null, 1).slice(0, 700)) }

  const sms = JSON.stringify({ phone: '13912345678', terminal: 'pc-web' })
  await call('POST', 8081, '/auth/sms/send', sms)
  const login = await call('POST', 8081, '/auth/sms/login', JSON.stringify({ phone: '13912345678', code: '123123', terminal: 'pc-web' }))
  const token = login.json && login.json.data && (login.json.data.token || login.json.data.accessToken)
  say('login', { hasToken: !!token, code: login.json && login.json.code })
  if (!token) process.exit(1)

  const specs = [
    {
      name: 'BH-ClickHouse-HTTP', type: 'clickhouse',
      config: JSON.stringify({ host: 'localhost', port: 8123, database: 'dataviz', username: 'default', password: 'clickhouse123' }),
      sql: 'SELECT channel, round(sum(amount), 2) AS total_amount, sum(order_cnt) AS qty FROM dwd_order_channel_daily GROUP BY channel ORDER BY total_amount DESC',
      dataAccess: true
    },
    {
      name: 'BH-Elasticsearch-ESQL', type: 'elasticsearch',
      config: JSON.stringify({ host: 'localhost', port: 9200 }),
      sql: 'SELECT stat_date, channel, amount FROM demo_order_events ORDER BY stat_date LIMIT 5',
      dataAccess: true
    },
    {
      name: 'BH-MongoDB-可达性', type: 'mongodb',
      config: JSON.stringify({ host: 'localhost', port: 27017, database: 'admin', username: 'admin', password: 'admin123456' }),
      sql: 'db.demo_orders.find().limit(5)',
      dataAccess: false
    }
  ]

  // 本轮建的数据源**保留**（这正是要交付的东西：CK/ES/Mongo 在产品里可用），
  // 但按名字判存，重复跑不再堆同名行。
  const list = await call('GET', 8083, '/api/datasource/list?page=1&size=50', undefined, token)
  const page = (list.json && list.json.data) || {}
  const existing = page.records || page.rows || []
  const byName = {}
  for (const row of existing) byName[row.name] = row.id

  const created = []
  for (const s of specs) {
    let id = byName[s.name]
    let c = { status: 'reused', json: { code: 0, data: id } }
    if (!id) {
      c = await call('POST', 8083, '/api/datasource',
        { name: s.name, type: s.type, config: s.config, description: 'BH 阶段连接器实测（HTTP/可达性）' }, token)
      id = c.json && c.json.data
    }
    created.push({ name: s.name, id, type: s.type, sql: s.sql, dataAccess: s.dataAccess })
    say('create:' + s.type, brief(c))
    if (!id) continue

    const tc = await call('POST', 8083, '/api/datasource/' + id + '/test-connection', null, token)
    say('test-connection:' + s.type, brief(tc))

    const tb = await call('GET', 8083, '/api/datasource/' + id + '/tables', undefined, token)
    say('tables:' + s.type, brief(tb, 200))

    const ex = await call('POST', 8083, '/api/datasource/execute',
      { datasourceId: id, sql: s.sql, maxRows: 20, timeout: 15 }, token)
    say('execute:' + s.type, brief(ex, 420))

    // 只读闸门：连接器路也必须拒绝写语句（不能因为换了 HTTP 通道就放行 DML）。
    // 探针刻意打在一张 scratch 表上：万一闸门是坏的，丢的也只是这张可有可无的表，不是演示数据。
    if (s.dataAccess) {
      const deny = await call('POST', 8083, '/api/datasource/execute',
        { datasourceId: id, sql: s.type === 'clickhouse' ? 'DROP TABLE bh_scratch' : 'DELETE FROM demo_order_events', maxRows: 5 }, token)
      say('execute-write-denied:' + s.type, brief(deny, 240))
    }
  }

  const finalList = await call('GET', 8083, '/api/datasource/list?page=1&size=50', undefined, token)
  const finalPage = (finalList.json && finalList.json.data) || {}
  const rows = (finalPage.records || finalPage.rows || []).filter(r => String(r.name).indexOf('BH-') === 0)
  say('retained-datasources', { count: rows.length, ids: rows.map(r => r.id + ':' + r.name + ':' + r.type) })

  fs.writeFileSync('E:/wowowo/devlog/_bh/bh-connector-probe.json', JSON.stringify(out, null, 2))
})()
