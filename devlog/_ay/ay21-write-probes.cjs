const fs = require('fs')
const http = require('http')
const AY = 'E:/wowowo/devlog/_ay'
const ADMIN_PHONE = process.argv[2] || '13912345678'
const VIEWER_PHONE = process.argv[3] || '13900000905'
const SLEEP = Number(process.argv[4] || 1200)
const P = 'zz_ay21_'
const sleep = ms => new Promise(r => setTimeout(r, ms))

function raw(method, p, body, token) {
  return new Promise(res => {
    const data = body === undefined || body === null ? null : JSON.stringify(body)
    const headers = {}
    if (data !== null) { headers['Content-Type'] = 'application/json'; headers['Content-Length'] = Buffer.byteLength(data) }
    if (token) headers.Authorization = 'Bearer ' + token
    const req = http.request({ host: '127.0.0.1', port: 8080, path: p, method, headers, timeout: 30000 }, r => {
      let b = ''
      r.on('data', c => (b += c))
      r.on('end', () => {
        let j = null
        try { j = JSON.parse(b) } catch {}
        const isObj = j !== null && typeof j === 'object' && !Array.isArray(j)
        res({ status: r.statusCode, code: isObj && 'code' in j ? j.code : undefined, message: isObj ? j.message : undefined, data: isObj ? j.data : j, raw: b })
      })
    })
    req.on('timeout', () => { req.destroy(); res({ status: 'ERR', message: 'timeout', raw: '' }) })
    req.on('error', e => res({ status: 'ERR', message: String(e.message), raw: '' }))
    if (data !== null) req.write(data)
    req.end()
  })
}

const rows = []
async function step(chain, name, method, p, body, token, wantStatus, wantCode) {
  await sleep(SLEEP)
  const r = await raw(method, p, body, token)
  const pass = (wantStatus === null || r.status === wantStatus) && (wantCode === null || r.code === wantCode)
  rows.push({ chain, name, method, path: p, status: r.status, code: r.code === undefined ? '-' : r.code, message: String(r.message === undefined ? r.raw : r.message).replace(/\s+/g, ' ').slice(0, 110), pass, want: wantStatus + '/' + (wantCode === null ? '-' : wantCode) })
  console.log((pass ? '  ok ' : '  XX ') + chain + ' · ' + name + ' -> ' + r.status + '/' + (r.code === undefined ? '-' : r.code) + (pass ? '' : ' (want ' + rows[rows.length - 1].want + ')') + ' | ' + rows[rows.length - 1].message.slice(0, 62))
  if (r.status === 429) { console.log('   限流：退避 30s'); await sleep(30000) }
  return r
}
async function login(phone) {
  await raw('POST', '/api/auth/sms/send', { phone, terminal: 'pc-web' })
  const lg = await raw('POST', '/api/auth/sms/login', { phone, code: '123123', terminal: 'pc-web' })
  return lg.data && (lg.data.token || lg.data.accessToken)
}
function note(chain, name, detail, pass) {
  rows.push({ chain, name, method: 'NOTE', path: 'note', status: pass ? 'PASS' : 'FAIL', code: '-', message: detail, pass, want: '判据' })
  console.log('  ' + (pass ? 'ok ' : 'XX ') + chain + ' · ' + name + '：' + detail)
}

;(async () => {
  const admin = await login(ADMIN_PHONE)
  if (!admin) { console.log('ADMIN LOGIN FAIL'); process.exit(1) }

  // ---------- P1 分享链正向：发布 -> 分享 -> 免登可读 -> 下架 -> 删屏 ----------
  const sc = await step('P1', '创建大屏', 'POST', '/api/screen', { name: P + 'screen', width: 1920, height: 1080, adaptMode: 'auto', config: {}, components: [], layers: [] }, admin, 200, 0)
  const screenId = sc.data && sc.data.id ? sc.data.id : null
  if (screenId) {
    await step('P1', '发布', 'POST', '/api/screen/' + screenId + '/publish', null, admin, 200, 0)
    const sh = await step('P1', '生成分享 token', 'POST', '/api/screen/' + screenId + '/share', null, admin, 200, 0)
    const t = typeof sh.data === 'string' ? sh.data : sh.data && sh.data.token
    if (t) {
      const pub = await step('P1', '已发布：免登读分享', 'GET', '/api/screen/share/' + t, null, null, 200, 0)
      note('P1', '免登读到的是我这块屏', 'name=' + (pub.data && pub.data.name), (pub.data && pub.data.name) === P + 'screen')
      await step('P1', '下架', 'POST', '/api/screen/' + screenId + '/unpublish', null, admin, 200, 0)
      await step('P1', '下架后：免登读分享应被拒', 'GET', '/api/screen/share/' + t, null, null, 200, 404)
      await step('P1', '重新发布', 'POST', '/api/screen/' + screenId + '/publish', null, admin, 200, 0)
      await step('P1', '删除大屏', 'DELETE', '/api/screen/' + screenId, null, admin, 200, 0)
      const dead = await step('P1', '删屏后：免登读分享应为"不存在"', 'GET', '/api/screen/share/' + t, null, null, 200, 404)
      note('P1', '删屏后分享链报的是哪一句', String(dead.message), /不存在|无效/.test(String(dead.message)))
    }
  }

  // ---------- P2 调度：handlerKey 取自服务端注册表 ----------
  const hd = await step('P2', 'handler 清单', 'GET', '/api/schedule/job/handlers', null, admin, 200, 0)
  const hk = Array.isArray(hd.data) && hd.data.length ? hd.data[0].handlerKey : null
  note('P2', '注册表给的 handlerKey', 'hk=' + hk + ' 全部=' + JSON.stringify((hd.data || []).map(x => x.handlerKey)), !!hk)
  if (hk) {
    const jb = await step('P2', '用真 handlerKey 建作业', 'POST', '/api/schedule/job', { jobName: P + 'job', jobGroup: 'ay21', cronExpression: '0 0 3 * * ?', jobClass: hk, description: 'ay21 自建', misfirePolicy: 'doNothing' }, admin, 200, 0)
    const jbId = jb.data && jb.data.id ? jb.data.id : (typeof jb.data === 'number' ? jb.data : null)
    if (jbId) {
      const g1 = await step('P2', '读取作业', 'GET', '/api/schedule/job/' + jbId, null, admin, 200, 0)
      note('P2', 'jobClass 落库值', 'jobClass=' + (g1.data && g1.data.jobClass) + ' status=' + (g1.data && g1.data.status), g1.data && g1.data.jobClass === hk)
      await step('P2', '改名（带 id）', 'PUT', '/api/schedule/job', { id: jbId, jobName: P + 'job-2', jobGroup: 'ay20', cronExpression: '0 0 4 * * ?', jobClass: hk, misfirePolicy: 'doNothing' }, admin, 200, 0)
      const g2 = await step('P2', '改名后读取', 'GET', '/api/schedule/job/' + jbId, null, admin, 200, 0)
      note('P2', '改名是否生效', 'jobName=' + (g2.data && g2.data.jobName) + ' cron=' + (g2.data && g2.data.cronExpression), (g2.data && g2.data.jobName) === P + 'job-2')
      await step('P2', '暂停', 'POST', '/api/schedule/job/' + jbId + '/pause', null, admin, 200, 0)
      await step('P2', '恢复', 'POST', '/api/schedule/job/' + jbId + '/resume', null, admin, 200, 0)
      await step('P2', '删除作业', 'DELETE', '/api/schedule/job/' + jbId, null, admin, 200, 0)
      await step('P2', '删除后读取', 'GET', '/api/schedule/job/' + jbId, null, admin, 200, null)
    }
  }

  // ---------- P3 数据集（真实路径 /api/datasource/list） ----------
  const dl = await step('P3', '数据源列表', 'GET', '/api/datasource/list?pageNum=1&pageSize=1', null, admin, 200, 0)
  const rec = dl.data && (dl.data.records || dl.data.list || dl.data)
  const dsId = Array.isArray(rec) ? rec[0] && rec[0].id : rec && rec.id
  note('P3', '取到一个 datasourceId', 'dsId=' + dsId, !!dsId)
  if (dsId) {
    const dt = await step('P3', '创建数据集', 'POST', '/api/model/dataset', { name: P + 'dataset', datasourceId: dsId, tableName: 'sys_user', dimensions: '[]', metrics: '[]' }, admin, 200, 0)
    const dtId = dt.data && dt.data.id ? dt.data.id : (typeof dt.data === 'number' ? dt.data : null)
    if (dtId) {
      await step('P3', '读取数据集', 'GET', '/api/model/dataset/' + dtId, null, admin, 200, 0)
      await step('P3', '改名', 'PUT', '/api/model/dataset/' + dtId, { name: P + 'dataset-2', datasourceId: dsId, tableName: 'sys_user' }, admin, 200, 0)
      await step('P3', '缺名创建（@Valid 面）', 'POST', '/api/model/dataset', { datasourceId: dsId }, admin, 400, null)
      await step('P3', '删除数据集', 'DELETE', '/api/model/dataset/' + dtId, null, admin, 200, 0)
      await step('P3', '删除后读取', 'GET', '/api/model/dataset/' + dtId, null, admin, 200, null)
    }
  }

  // ---------- P4 组件的父级引用完整性 ----------
  const orph = await step('P4', '把组件挂到不存在的仪表盘下', 'POST', '/api/dashboard/widget', { dashboardId: 999999999, widgetType: 'bar', title: P + 'orphan', configJson: '{}', dataConfigJson: '{}', positionJson: '{}', sort: 1 }, admin, 200, null)
  const orphId = typeof orph.data === 'number' ? orph.data : orph.data && orph.data.id
  note('P4', '孤儿组件是否被拒', 'code=' + orph.code + ' 返回 id=' + orphId, orph.code !== 0)
  if (orphId) {
    await step('P4', '清理孤儿组件', 'DELETE', '/api/dashboard/widget/' + orphId, null, admin, 200, null)
  }
  await step('P4', 'PUT 仪表盘不带 id', 'PUT', '/api/dashboard', { name: P + 'x' }, admin, 200, null)

  // ---------- P5 只读角色（viewer01）的写面反向用例 ----------
  const viewer = await login(VIEWER_PHONE)
  if (viewer) {
    await step('P5', 'viewer 建仪表盘（期望 403）', 'POST', '/api/dashboard', { name: P + 'viewer' }, viewer, 403, 403)
    await step('P5', 'viewer 建联系人（期望 403）', 'POST', '/api/alert/contact', { name: P + 'viewer', email: 'x@example.com' }, viewer, 403, 403)
    await step('P5', 'viewer 建评论（期望 403）', 'POST', '/api/collab/comment', { targetType: 'screen', targetId: 1, content: 'x' }, viewer, 403, 403)
    await step('P5', 'viewer 读仪表盘列表（期望放行）', 'GET', '/api/dashboard/page?pageNum=1&pageSize=1', null, viewer, 200, null)
  } else {
    note('P5', 'viewer 登录未成功', '本轮反向用例未跑', false)
  }

  const dist = {}
  rows.forEach(x => { const k = x.status + '/' + x.code; dist[k] = (dist[k] || 0) + 1 })
  const fails = rows.filter(x => !x.pass)
  console.log('\n=== 读数 =', rows.length, ' 未达期望 =', fails.length)
  console.log('=== status/code 分布 ===')
  Object.entries(dist).sort((a, b) => b[1] - a[1]).forEach(([k, v]) => console.log('   ' + k + '  ' + v))
  fails.forEach(x => console.log('   XX ' + x.chain + ' ' + x.name + ' ' + x.method + ' ' + x.path + ' -> ' + x.status + '/' + x.code + ' (want ' + x.want + ') ' + String(x.message).slice(0, 70)))
  fs.writeFileSync(AY + '/ay21-write-probes.json', JSON.stringify({ dist, rows }, null, 2))
  console.log('\n落盘 devlog/_ay/ay21-write-probes.json')
})()
