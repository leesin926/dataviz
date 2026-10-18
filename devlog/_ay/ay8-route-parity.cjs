const fs = require('fs')
const path = require('path')
const AY = 'E:/wowowo/devlog/_ay'

// 路由表：严格按 application.yml 的声明顺序，Spring Cloud Gateway 取第一条匹配的 RouteDefinition
const ROUTES = [
  { id: 'auth-service', port: '8081', paths: ['/api/auth/**'], strip: 1 },
  { id: 'user-service', port: '8082', paths: ['/api/user/**', '/api/dept/**', '/api/role/**', '/api/permission/**'], strip: 1 },
  { id: 'datasource-service', port: '8083', paths: ['/api/datasource/**'], strip: 0 },
  { id: 'etl-operator-service', port: '8084', paths: ['/api/etl/operator/**'], strip: 1 },
  { id: 'etl-service', port: '8084', paths: ['/api/etl/**'], strip: 0 },
  { id: 'model-service', port: '8085', paths: ['/api/model/**'], strip: 0 },
  { id: 'analysis-service', port: '8086', paths: ['/api/analysis/**'], strip: 0 },
  { id: 'dashboard-service', port: '8087', paths: ['/api/dashboard/**'], strip: 0 },
  { id: 'screen-service', port: '8088', paths: ['/api/screen/**'], strip: 0 },
  { id: 'collab-service', port: '8089', paths: ['/api/collab/**'], strip: 0 },
  { id: 'alert-service', port: '8090', paths: ['/api/alert/**'], strip: 0 },
  { id: 'ai-service', port: '8091', paths: ['/api/ai/**'], strip: 0 },
  { id: 'openapi-service', port: '8092', paths: ['/api/openapi/**'], strip: 0 },
  { id: 'admin-service', port: '8093', paths: ['/api/admin/**'], strip: 0 },
  { id: 'file-service', port: '8094', paths: ['/api/file/**'], strip: 0 },
  { id: 'schedule-service', port: '8095', paths: ['/api/schedule/**'], strip: 0 },
  { id: 'monitor-service', port: '8096', paths: ['/api/monitor/**'], strip: 0 },
]

// Ant 匹配：只支持本项目实际用到的两种写法 —— /** （含零段）与 {var}
function antMatch(pattern, p) {
  if (pattern.endsWith('/**')) {
    const head = pattern.slice(0, -3)
    return p === head || p.startsWith(head + '/')
  }
  return pattern === p
}
const matchRoute = p => ROUTES.find(r => r.paths.some(x => antMatch(x, p)))

// 把每个服务的 openapi 映射收两份：上游真实路径（剥完前缀后应该看到的）+ 网关对外路径
const upstream = new Map()
const gwOf = new Map()
for (const f of fs.readdirSync(path.join(AY, 'docs'))) {
  const port = f.slice(0, 4)
  const doc = JSON.parse(fs.readFileSync(path.join(AY, 'docs', f), 'utf8'))
  // 只有 auth / user 两条路由带 StripPrefix=1，所以这两个服务的文档路径不带 /api；其余服务自带 /api
  const needApi = port === '8081' || port === '8082'
  const set = new Set()
  for (const p0 of Object.keys(doc.paths || {})) {
    set.add(p0)
    const gw = needApi ? '/api' + p0 : p0
    if (!gwOf.has(gw)) gwOf.set(gw, { port, up: p0 })
  }
  upstream.set(port, set)
}

// 路径变量段不参与字面比较：/api/x/{id} 与 /api/x/7 视为同一形状
const shape = p => p.split('/').map(s => (s.startsWith('{') && s.endsWith('}')) ? '\u0000' : /^\d+$/.test(s) ? '\u0000' : s).join('/')
const hasShape = (port, p) => {
  const t = shape(p)
  for (const d of upstream.get(port) || []) if (shape(d) === t) return true
  return false
}

const noRoute = []
const stripped = []
for (const [gw, info] of gwOf) {
  const r = matchRoute(gw)
  if (!r) { noRoute.push({ gw, port: info.port }); continue }
  if (r.port !== info.port) { stripped.push({ gw, routedTo: r.id, expectPort: info.port, upstream: info.up, gotUpstream: null, why: '端口不符' }); continue }
  let up = gw
  if (r.strip) up = '/' + gw.split('/').filter(Boolean).slice(r.strip).join('/')
  if (!hasShape(info.port, up)) stripped.push({ gw, routedTo: r.id, upstream: up, realUpstream: info.up, port: info.port })
}

console.log('活端点（网关对外视角）总数 =', gwOf.size)
console.log('\n【A】没有任何网关路由能匹配 =', noRoute.length)
noRoute.forEach(x => console.log('   ' + x.gw + '   (' + x.port + ')'))
console.log('\n【B】路由命中、但转发到上游后的路径在该服务里不存在 =', stripped.length)
stripped.forEach(b => console.log('   ' + b.gw + '  --[' + b.routedTo + ']-->  ' + b.upstream + '    实际映射是 ' + b.realUpstream))
fs.writeFileSync(path.join(AY, 'ay8-route-parity.json'), JSON.stringify({ total: gwOf.size, noRoute, stripped }, null, 2))
