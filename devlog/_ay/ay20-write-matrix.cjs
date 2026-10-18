const fs = require('fs')
const http = require('http')
const AY = 'E:/wowowo/devlog/_ay'
const PHONE = process.argv[2] || '13912345678'
const SLEEP = Number(process.argv[3] || 1200)
const P = 'zz_ay20_'
const sleep = ms => new Promise(r => setTimeout(r, ms))

function raw(method, p, body, token) {
  return new Promise(res => {
    const data = body === undefined || body === null ? null : JSON.stringify(body)
    const headers = {}
    if (data !== null) { headers['Content-Type'] = 'application/json'; headers['Content-Length'] = Buffer.byteLength(data) }
    if (token) headers.Authorization = 'Bearer ' + token
    const req = http.request({ host: '127.0.0.1', port: 8080, path: p, method, headers, timeout: 25000 }, r => {
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
let token = null
async function step(chain, name, method, p, body, expectStatus, expectCode) {
  await sleep(SLEEP)
  const r = await raw(method, p, body, token)
  const okStatus = r.status === expectStatus
  const okCode = expectCode === null || r.code === expectCode
  rows.push({ chain, name, method, path: p, status: r.status, code: r.code === undefined ? '-' : r.code, message: String(r.message === undefined ? r.raw : r.message).replace(/\s+/g, ' ').slice(0, 90), pass: okStatus && okCode, want: expectStatus + '/' + (expectCode === null ? '-' : expectCode) })
  const x = rows[rows.length - 1]
  console.log((x.pass ? '  ok ' : '  XX ') + chain + ' · ' + name + ' -> ' + x.status + '/' + x.code + ' (want ' + x.want + ') | ' + x.message.slice(0, 60))
  if (r.status === 429) { console.log('   限流：退避 30s'); await sleep(30000) }
  return r
}
function dataOf(r) { return r && r.data && r.data.id !== undefined ? r.data.id : r && r.data }

;(async () => {
  // ---- 登录（我自己的演示会话） ----
  await raw('POST', '/api/auth/sms/send', { phone: PHONE, terminal: 'pc-web' })
  const lg = await raw('POST', '/api/auth/sms/login', { phone: PHONE, code: '123123', terminal: 'pc-web' })
  token = lg.data && (lg.data.token || lg.data.accessToken)
  if (!token) { console.log('LOGIN FAIL'); process.exit(1) }
  console.log('login ok, token len=' + token.length)

  // ================= C1 dashboard + widget =================
  const dsd = await step('C1', '创建仪表盘', 'POST', '/api/dashboard', { name: P + 'dash', description: 'ay20 自建' }, 200, 0)
  const dashId = dataOf(dsd)
  await step('C1', '空 body 创建（期望 4xx 不是 500）', 'POST', '/api/dashboard', {}, 200, null)
  if (dashId) {
    await step('C1', '读取仪表盘', 'GET', '/api/dashboard/' + dashId, null, 200, 0)
    await step('C1', '改名', 'PUT', '/api/dashboard', { id: dashId, name: P + 'dash-renamed' }, 200, 0)
    const chk = await step('C1', '改名后读取', 'GET', '/api/dashboard/' + dashId, null, 200, 0)
    const gotName = chk.data && chk.data.name
    rows.push({ chain: 'C1', name: '断言：改名生效', path: 'assert', method: 'ASSERT', status: gotName === P + 'dash-renamed' ? 'PASS' : 'FAIL', code: '-', message: 'name=' + gotName, pass: gotName === P + 'dash-renamed', want: P + 'dash-renamed' })
    console.log('  ' + (gotName === P + 'dash-renamed' ? '  ok' : '  XX') + ' C1 · 断言改名生效 name=' + gotName)
    // publish / unpublish 状态机
    await step('C1', '发布', 'POST', '/api/dashboard/' + dashId + '/publish', null, 200, 0)
    await step('C1', '取消发布', 'POST', '/api/dashboard/' + dashId + '/unpublish', null, 200, 0)
    // widget
    const wg = await step('C1', '创建组件', 'POST', '/api/dashboard/widget', { dashboardId: dashId, widgetType: 'bar', title: P + 'widget', configJson: '{}', dataConfigJson: '{}', positionJson: '{"x":0,"y":0,"w":4,"h":4}', sort: 1 }, 200, 0)
    const wid = dataOf(wg)
    if (wid) {
      const w1 = await step('C1', '读取组件', 'GET', '/api/dashboard/widget/' + wid, null, 200, 0)
      console.log('     widget.id=' + wid + ' widget.dashboardId=' + (w1.data && w1.data.dashboardId) + ' (应为 ' + dashId + ')')
      // 主键错配探针：用组件自己的 id 当 dashboardId 传进去
      await step('C1', '更新组件（传 dashboardId=组件id，探针）', 'PUT', '/api/dashboard/widget', { dashboardId: wid, widgetType: 'bar', title: P + 'widget-hijack', configJson: '{}', dataConfigJson: '{}', positionJson: '{"x":1,"y":1,"w":4,"h":4}', sort: 2 }, 200, 0)
      const w2 = await step('C1', '探针后读取组件', 'GET', '/api/dashboard/widget/' + wid, null, 200, 0)
      const parentId = w2.data && w2.data.dashboardId
      const title = w2.data && w2.data.title
      const corrupt = parentId === wid || title === P + 'widget-hijack'
      rows.push({ chain: 'C1', name: '探针：PUT 是否改到了这个组件', path: 'assert', method: 'ASSERT', status: corrupt ? 'CHANGED' : 'UNCHANGED', code: '-', message: 'dashboardId=' + parentId + ' title=' + title, pass: !corrupt, want: 'dashboardId=' + dashId + ' 且 title 不变' })
      console.log('  ' + (corrupt ? '  XX' : '  ok') + ' C1 · 探针结果：' + (parentId === wid ? '父级被改成组件自身 id（重挂）' : '父级仍为 ' + parentId) + ' title=' + title)
      // 正常语义：想改这个组件，但 DTO 里没有组件 id 的位置
      await step('C1', '更新组件（传 dashboardId=父级id，正常语义）', 'PUT', '/api/dashboard/widget', { dashboardId: dashId, widgetType: 'bar', title: P + 'widget-normal' }, 200, 0)
      const w3 = await step('C1', '正常语义更新后读取', 'GET', '/api/dashboard/widget/' + wid, null, 200, 0)
      rows.push({ chain: 'C1', name: '断言：按父级 id 更新能否命中本组件', path: 'assert', method: 'ASSERT', status: (w3.data && w3.data.title) === P + 'widget-normal' ? 'PASS' : 'FAIL', code: '-', message: 'title=' + (w3.data && w3.data.title), pass: (w3.data && w3.data.title) === P + 'widget-normal', want: P + 'widget-normal' })
      await step('C1', '删除组件', 'DELETE', '/api/dashboard/widget/' + wid, null, 200, 0)
      await step('C1', '删除后读取组件（期望 404）', 'GET', '/api/dashboard/widget/' + wid, null, 200, 404)
    }
    await step('C1', '不存在的仪表盘 id（not-found 形状）', 'GET', '/api/dashboard/999999999', null, 200, 404)
    await step('C1', '删除仪表盘', 'DELETE', '/api/dashboard/' + dashId, null, 200, 0)
    const gone = await step('C1', '删除后读取仪表盘', 'GET', '/api/dashboard/' + dashId, null, 200, 404)
    rows.push({ chain: 'C1', name: '断言：删除后读不到（软删生效）', path: 'assert', method: 'ASSERT', status: gone.code === 404 ? 'PASS' : 'FAIL', code: String(gone.code), message: 'code=' + gone.code, pass: gone.code === 404, want: '404' })
  }

  // ================= C2 screen（含免登分享链） =================
  const sc = await step('C2', '创建大屏', 'POST', '/api/screen', { name: P + 'screen', width: 1920, height: 1080, adaptMode: 'auto', config: {}, components: [], layers: [] }, 200, 0)
  const screenId = sc.data && sc.data.id ? sc.data.id : dataOf(sc)
  if (screenId) {
    await step('C2', '读取大屏', 'GET', '/api/screen/' + screenId, null, 200, 0)
    await step('C2', '改名', 'PUT', '/api/screen/' + screenId, { name: P + 'screen-renamed' }, 200, 0)
    const sh = await step('C2', '生成分享 token', 'POST', '/api/screen/' + screenId + '/share', null, 200, 0)
    const shareToken = typeof sh.data === 'string' ? sh.data : sh.data && sh.data.token
    if (shareToken) {
      await sleep(SLEEP)
      const pub = await raw('GET', '/api/screen/share/' + shareToken, null, null)
      rows.push({ chain: 'C2', name: '免登读分享（无 token 头）', method: 'GET', path: '/api/screen/share/<t>', status: pub.status, code: pub.code === undefined ? '-' : pub.code, message: String(pub.message).slice(0, 60), pass: pub.status === 200 && pub.code === 0, want: '200/0' })
      console.log('  ' + (pub.status === 200 && pub.code === 0 ? '  ok ' : '  XX ') + 'C2 · 免登分享 -> ' + pub.status + '/' + pub.code)
    }
    await step('C2', '删除大屏', 'DELETE', '/api/screen/' + screenId, null, 200, 0)
    await step('C2', '删除后读取大屏', 'GET', '/api/screen/' + screenId, null, 200, 404)
    if (shareToken) {
      await sleep(SLEEP)
      const dead = await raw('GET', '/api/screen/share/' + shareToken, null, null)
      rows.push({ chain: 'C2', name: '断言：删屏后分享链应失效', method: 'GET', path: '/api/screen/share/<t>', status: dead.status, code: dead.code === undefined ? '-' : dead.code, message: String(dead.message).slice(0, 60), pass: dead.code === 404 || dead.code === 403, want: '非 200/0' })
      console.log('  ' + (dead.code === 404 || dead.code === 403 ? '  ok ' : '  XX ') + 'C2 · 删屏后分享链 -> ' + dead.status + '/' + dead.code)
    }
  }

  // ================= C3 collab（评论 + 订阅，用大屏做目标） =================
  const tgt = screenId || dashId
  if (tgt) {
    const cm = await step('C3', '创建评论', 'POST', '/api/collab/comment', { targetType: 'screen', targetId: tgt, content: P + 'comment' }, 200, 0)
    const cmId = dataOf(cm)
    if (cmId) {
      await step('C3', '读取评论', 'GET', '/api/collab/comment/' + cmId, null, 200, 0)
      await step('C3', '改评论内容（query 参数）', 'PUT', '/api/collab/comment/' + cmId + '?content=' + encodeURIComponent(P + 'comment-2'), null, 200, 0)
      const cm2 = await step('C3', '改后读取', 'GET', '/api/collab/comment/' + cmId, null, 200, 0)
      rows.push({ chain: 'C3', name: '断言：评论改动生效', method: 'ASSERT', path: 'assert', status: (cm2.data && cm2.data.content) === P + 'comment-2' ? 'PASS' : 'FAIL', code: '-', message: 'content=' + (cm2.data && cm2.data.content), pass: (cm2.data && cm2.data.content) === P + 'comment-2', want: P + 'comment-2' })
      await step('C3', '目标下评论列表', 'GET', '/api/collab/comment/list?targetType=screen&targetId=' + tgt, null, 200, 0)
      await step('C3', '删除评论', 'DELETE', '/api/collab/comment/' + cmId, null, 200, 0)
      await step('C3', '删除后读取评论', 'GET', '/api/collab/comment/' + cmId, null, 200, 404)
    }
    const sb = await step('C3', '创建订阅', 'POST', '/api/collab/subscription', { targetType: 'screen', targetId: tgt, notifyTypes: 'comment,approval' }, 200, 0)
    const sbId = dataOf(sb)
    if (sbId) {
      await step('C3', '取消订阅', 'DELETE', '/api/collab/subscription/' + sbId, null, 200, 0)
      await step('C3', '取消后再订阅一次（幂等面）', 'POST', '/api/collab/subscription', { targetType: 'screen', targetId: tgt, notifyTypes: 'comment' }, 200, 0)
      const lst = await step('C3', '我的订阅列表', 'GET', '/api/collab/subscription/list/1', null, 200, 0)
      const mine = Array.isArray(lst.data) ? lst.data.filter(x => x.targetId === tgt) : []
      for (const m of mine) await step('C3', '清理订阅 ' + m.id, 'DELETE', '/api/collab/subscription/' + m.id, null, 200, 0)
    }
  }

  // ================= C4 alert 联系人 + 通知组（引用守卫） =================
  const ct = await step('C4', '创建联系人', 'POST', '/api/alert/contact', { name: P + 'contact', email: 'ay20@example.com', mobile: '13900000000', remark: 'ay20 自建', enabled: true }, 200, 0)
  const ctId = ct.data && ct.data.id ? ct.data.id : dataOf(ct)
  if (ctId) {
    await step('C4', '读取联系人', 'GET', '/api/alert/contact/' + ctId, null, 200, 0)
    await step('C4', '停用', 'POST', '/api/alert/contact/' + ctId + '/disable', null, 200, 0)
    await step('C4', '启用', 'POST', '/api/alert/contact/' + ctId + '/enable', null, 200, 0)
    await step('C4', '改名（带 id）', 'PUT', '/api/alert/contact', { id: ctId, name: P + 'contact-2', email: 'ay20@example.com', mobile: '13900000000', enabled: true }, 200, 0)
    const grp = await step('C4', '创建引用该联系人的通知组', 'POST', '/api/alert/notify-group', { name: P + 'group', description: 'ay20 自建', enabled: true, contactIds: [ctId] }, 200, 0)
    const grpId = grp.data && grp.data.id ? grp.data.id : dataOf(grp)
    if (grpId) {
      await step('C4', '读取通知组', 'GET', '/api/alert/notify-group/' + grpId, null, 200, 0)
      const refDel = await step('C4', '被引用时删除联系人（期望被挡）', 'DELETE', '/api/alert/contact/' + ctId, null, 200, null)
      rows.push({ chain: 'C4', name: '断言：引用守卫挡住删除', method: 'ASSERT', path: 'assert', status: refDel.code !== 0 ? 'PASS' : 'FAIL', code: String(refDel.code), message: String(refDel.message).slice(0, 60), pass: refDel.code !== 0, want: '非 0' })
      await step('C4', '删除通知组', 'DELETE', '/api/alert/notify-group/' + grpId, null, 200, 0)
      await step('C4', '引用解除后再删联系人', 'DELETE', '/api/alert/contact/' + ctId, null, 200, 0)
      await step('C4', '删除后读取联系人', 'GET', '/api/alert/contact/' + ctId, null, 200, null)
    } else {
      await step('C4', '删除联系人', 'DELETE', '/api/alert/contact/' + ctId, null, 200, 0)
    }
  }

  // ================= C5 schedule 作业 =================
  const hd = await step('C5', '已注册 handler 清单', 'GET', '/api/schedule/job/handlers', null, 200, 0)
  const handler = Array.isArray(hd.data) && hd.data.length ? (hd.data[0].jobClass || hd.data[0].className || hd.data[0].name || hd.data[0]) : null
  if (handler) {
    await step('C5', '未知 handler 建作业（期望被拒）', 'POST', '/api/schedule/job', { jobName: P + 'bad', jobGroup: 'ay20', cronExpression: '0 0 3 * * ?', jobClass: 'zz.NoSuchJob', description: 'ay20 自建' }, 200, null)
    const jb = await step('C5', '真 handler 建作业', 'POST', '/api/schedule/job', { jobName: P + 'job', jobGroup: 'ay20', cronExpression: '0 0 3 * * ?', jobClass: String(handler), description: 'ay20 自建', misfirePolicy: 'doNothing' }, 200, 0)
    const jbId = jb.data && jb.data.id ? jb.data.id : dataOf(jb)
    if (jbId) {
      await step('C5', '读取作业', 'GET', '/api/schedule/job/' + jbId, null, 200, 0)
      await step('C5', '非法 cron 更新（期望被拒）', 'PUT', '/api/schedule/job', { id: jbId, jobName: P + 'job', jobGroup: 'ay20', cronExpression: 'not-a-cron', jobClass: String(handler) }, 200, null)
      await step('C5', '暂停', 'POST', '/api/schedule/job/' + jbId + '/pause', null, 200, 0)
      await step('C5', '恢复', 'POST', '/api/schedule/job/' + jbId + '/resume', null, 200, 0)
      await step('C5', '删除作业', 'DELETE', '/api/schedule/job/' + jbId, null, 200, 0)
      await step('C5', '删除后读取作业', 'GET', '/api/schedule/job/' + jbId, null, 200, null)
    }
  }

  // ================= C6 model 数据集 =================
  const dl = await step('C6', '数据源列表（只读，取一个 id）', 'GET', '/api/datasource/page?pageNum=1&pageSize=1', null, 200, 0)
  const rec = dl.data && dl.data.records && dl.data.records[0]
  const dsId = rec && rec.id
  if (dsId) {
    const dt = await step('C6', '创建数据集', 'POST', '/api/model/dataset', { name: P + 'dataset', datasourceId: dsId, tableName: 'sys_user', dimensions: '[]', metrics: '[]' }, 200, 0)
    const dtId = dt.data && dt.data.id ? dt.data.id : dataOf(dt)
    if (dtId) {
      await step('C6', '读取数据集', 'GET', '/api/model/dataset/' + dtId, null, 200, 0)
      await step('C6', '改名', 'PUT', '/api/model/dataset/' + dtId, { name: P + 'dataset-2', datasourceId: dsId, tableName: 'sys_user' }, 200, 0)
      await step('C6', '删除数据集', 'DELETE', '/api/model/dataset/' + dtId, null, 200, 0)
      await step('C6', '删除后读取数据集', 'GET', '/api/model/dataset/' + dtId, null, 200, null)
    }
  }

  // ---- 汇总 ----
  const dist = {}
  rows.forEach(x => { const k = x.status + '/' + x.code; dist[k] = (dist[k] || 0) + 1 })
  const fails = rows.filter(x => !x.pass)
  console.log('\n=== 步数 =', rows.length, ' 未达期望 =', fails.length)
  console.log('=== status/code 分布 ===')
  Object.entries(dist).sort((a, b) => b[1] - a[1]).forEach(([k, v]) => console.log('   ' + k + '  ' + v))
  console.log('=== 未达期望明细 ===')
  fails.forEach(x => console.log('   ' + x.chain + ' ' + x.name + ' ' + x.method + ' ' + x.path + ' -> ' + x.status + '/' + x.code + ' (want ' + x.want + ') ' + String(x.message).slice(0, 70)))
  fs.writeFileSync(AY + '/ay20-write-matrix.json', JSON.stringify({ dist, rows }, null, 2))
  console.log('\n落盘 devlog/_ay/ay20-write-matrix.json')
})()
