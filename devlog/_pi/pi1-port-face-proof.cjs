// PI1 · 端口面判据：17 个服务在新段（17080-17096）逐个可服务，且网关 17080 的每条路由
// 真的打到它声称的那个端口 —— 不是"配置改了"，是"改完还能被穿过"。
//
// 两条容易被糊过去的地方，这里各有一条硬断言：
//  1) 网关"没有这条路由"与上游"这个路径不存在"都是 404。所以探针路径不是拍脑袋写的，
//     是从**那个服务自己的 /v3/api-docs 里挑一条真存在的 GET**，再按该路由的 StripPrefix 换回网关侧形状；
//     这样 404 只可能是"网关没转到"，不可能是"我编了个不存在的接口"。
//  2) 路由表不在这里抄第二遍：直接解析 gateway 的 application.yml，改配置就等于改判据。
const http = require('http')
const fs = require('fs')

const YML = 'E:/wowowo/backend/gateway-service/src/main/resources/application.yml'
const GW = 17080

/** 从网关配置里读路由：id / 上游端口 / Path 谓词 / 是否 StripPrefix=1 */
function readRoutes () {
  const lines = fs.readFileSync(YML, 'utf8').split(/\r?\n/)
  const routes = []
  let cur = null
  for (const raw of lines) {
    const line = raw.trim()
    if (/^- id: /.test(line)) {
      if (cur) routes.push(cur)
      cur = { id: line.slice(6).trim(), port: 0, paths: [], strip: 0 }
      continue
    }
    if (!cur) continue
    const uri = line.match(/^uri:\s*http:\/\/localhost:(\d+)/)
    if (uri) cur.port = Number(uri[1])
    const path = line.match(/^- Path=(.+)$/)
    if (path) cur.paths = path[1].split(',').map(s => s.trim())
    const strip = line.match(/^- StripPrefix=(\d+)$/)
    if (strip) cur.strip = Number(strip[1])
  }
  if (cur) routes.push(cur)
  return routes.filter(r => r.port && r.paths.length)
}

function get (port, path) {
  return new Promise(resolve => {
    const req = http.request({ host: '127.0.0.1', port, method: 'GET', path, timeout: 15000 }, res => {
      let text = ''
      res.setEncoding('utf8')
      res.on('data', c => { text += c })
      res.on('end', () => resolve({ status: res.statusCode, text }))
    })
    req.on('error', e => resolve({ status: 'ERR', text: String(e.message) }))
    req.on('timeout', () => { req.destroy(); resolve({ status: 'ERR', text: 'timeout' }) })
    req.end()
  })
}

/** 上游侧路径 -> 网关侧路径：StripPrefix=1 的路由上游少一段，网关侧要补回 /api 头 */
function toGatewayPath (upstream, strip) {
  return strip ? '/api' + upstream : upstream
}

/**
 * Path 谓词 -> 正则。`**` 吃任意深度，`*` 只吃一段，其余正则元字符按字面。
 * 为什么要这一步：auth-service 的文档里同时有 `/auth/**` 与 `/token/**`，而网关**刻意只路由前者**
 * （`/token/check` 把 token 挂在 query 上，是网关自己验签的内部口径 —— 那段理由就写在 gateway 的 yml 注释里）。
 * 探针若不按谓词筛，就会挑中一条"设计上到不了"的路径，把 404 记成网关的错。
 */
function toMatcher (pattern) {
  let out = ''
  for (let i = 0; i < pattern.length; i++) {
    const c = pattern[i]
    if (c === '*' && pattern[i + 1] === '*') { out += '.*'; i++; continue }
    if (c === '*') { out += '[^/]*'; continue }
    out += /[.+?^${}()|[\]\\]/.test(c) ? '\\' + c : c
  }
  return new RegExp('^' + out + '$')
}

;(async () => {
  const routes = readRoutes()
  console.log('路由条数（解析自 gateway application.yml）= ' + routes.length)

  const rows = []
  for (const r of routes) {
    const matchers = r.paths.map(toMatcher)
    const docRes = await get(r.port, '/v3/api-docs')
    let doc = null
    if (docRes.status === 200) { try { doc = JSON.parse(docRes.text) } catch (e) { /* 原样进读数 */ } }
    let endpoints = 0
    const getPaths = []
    if (doc) {
      for (const [p, ops] of Object.entries(doc.paths || {})) {
        endpoints += Object.keys(ops).length
        if (Object.keys(ops).some(m => m.toLowerCase() === 'get')) getPaths.push(p)
      }
    }
    // 只挑"这条路由声称能转过去"的路径：按谓词筛，且排序保证两次跑挑到同一条
    const probeUpstream = getPaths.sort()
      .find(p => matchers.some(m => m.test(toGatewayPath(p, r.strip)))) || null
    const gwPath = probeUpstream ? toGatewayPath(probeUpstream, r.strip) : null
    const gwRes = gwPath ? await get(GW, gwPath) : { status: 'NO-PROBE' }
    rows.push({
      id: r.id,
      port: r.port,
      docStatus: docRes.status,
      endpoints,
      gwPath,
      gwStatus: gwRes.status
    })
    const line = rows[rows.length - 1]
    console.log(String(line.port).padEnd(6) + line.id.padEnd(20) + 'doc=' + String(line.docStatus).padEnd(4) +
      'ops=' + String(line.endpoints).padEnd(5) + 'via-gw ' + (line.gwPath || '<没有命中该路由的 GET>') +
      ' -> ' + line.gwStatus)
  }

  // 判据
  const checks = []
  for (const row of rows) {
    checks.push([row.id + ' 直连 /v3/api-docs 200', row.docStatus === 200 && row.endpoints > 0])
    // 网关侧：200/400/401/403/405/500 都算"已经打到上游"（服务自己的回答）；
    // 只有 404（没匹配到路由）与 503/ERR（上游不可达）才是网关层的问题
    checks.push([row.id + ' 经网关 17080 可达', row.gwStatus !== 404 && row.gwStatus !== 503 && row.gwStatus !== 'ERR' && row.gwStatus !== 'NO-PROBE'])
  }
  const total = rows.reduce((a, b) => a + b.endpoints, 0)
  // 16 条路由对 16 个业务服务：网关自己不贡献业务端点，而历史上第 17 条 etl-operator-service
  // 路由在 API-96 那轮已经删掉（EtlOperatorController 自带 /api 上下文，剥前缀反而恒 404）
  checks.push(['路由表覆盖 16 个业务服务，且每条都挑到了探针路径',
    rows.length === 16 && rows.every(x => x.gwPath !== null)])

  let failed = 0
  for (const [name, ok] of checks) {
    if (!ok) { console.log('FAIL ' + name); failed++ }
  }
  console.log('\n总端点数（16 个业务服务聚合，不含网关自身）= ' + total)
  console.log(failed ? '未通过 ' + failed + ' 条' : '判据全部 PASS：' + checks.length + ' 条')
  if (failed) process.exitCode = 1
})().catch(e => { console.error('探针失败: ' + e.message); process.exitCode = 1 })
