// PI1 · 端口整体迁移 8080-8096 → 17080-17096（旧 + 9000）
// 为什么是个脚本而不是一串 sed：
// ① 只允许碰**显式清单**里的文件——devlog/** 与 docs/10·11·12 里的端口是历史测量记录，
//    改写它们等于伪造当时的读数（台账纪律：append 而非 rewrite）；target/、dist/、*.jar 会被文本编辑毁掉。
// ② 词边界 \b 保证 '#808080'（shared-utils 的十六进制颜色）与 808080 这类串不会被当成端口改掉。
// 用法：node devlog/_pi/remap-ports.cjs [--apply]   默认只报告，不写盘。
const fs = require('fs')
const path = require('path')

const MAP = {}
for (let p = 8080; p <= 8096; p++) MAP[String(p)] = String(p + 9000)

const SERVICES = ['gateway', 'auth', 'user', 'datasource', 'etl', 'model', 'dashboard', 'screen',
  'collab', 'alert', 'ai', 'openapi', 'admin', 'file', 'schedule', 'analysis', 'monitor']

const files = []
for (const s of SERVICES) files.push(`backend/${s}-service/src/main/resources/application.yml`)
files.push(
  'backend/common/common-core/src/main/java/com/dataviz/common/core/client/DatasourceQueryClient.java',
  'backend/common/common-core/src/main/java/com/dataviz/common/core/client/DatasourceWriteClient.java',
  'backend/analysis-service/src/main/java/com/dataviz/analysis/client/ModelServiceClient.java',
  'backend/file-service/src/main/java/com/dataviz/file/service/strategy/LocalStorageStrategy.java',
  'backend/openapi-service/src/main/java/com/dataviz/openapi/config/OpenApiProxyProperties.java',
  'backend/Dockerfile',
  'backend/start-all.sh',
  'deploy/nginx/nginx.conf',
  'deploy/prometheus/prometheus.yml',
  'deploy/k8s/namespace.yaml',
  'frontend/.env.development',
  'frontend/apps/pc-web/vite.config.ts',
  'frontend/apps/admin/vite.config.ts',
  'frontend/apps/pc-desktop/vite.config.ts',
  'frontend/apps/admin/src/locales/zh-CN.json',
  'frontend/apps/admin/src/locales/en-US.json',
  'frontend/apps/pc-desktop/src/locales/zh-CN.json',
  'frontend/apps/pc-desktop/src/locales/en-US.json',
  'frontend/apps/pc-web/src/router/index.ts',
  'frontend/packages/api-client/src/modules/ai.ts',
  'frontend/packages/shared-utils/src/validate.test.ts',
  'README.md',
  'docs/01-总体技术架构文档.md',
  'docs/09-后端服务开发文档.md',
  'docs/03-PC浏览器端开发文档.md',
  'docs/13-桌面端分发与更新方案.md'
)

const RE = /\b(808[0-9]|809[0-6])\b/g
const apply = process.argv.includes('--apply')
const root = 'E:/wowowo'
let totalFiles = 0, totalHits = 0, missing = []

for (const rel of files) {
  const abs = path.join(root, rel)
  if (!fs.existsSync(abs)) { missing.push(rel); continue }
  const src = fs.readFileSync(abs, 'utf8')
  let hits = 0
  const out = src.replace(RE, (m) => { hits++; return MAP[m] })
  totalHits += hits
  if (hits > 0) totalFiles++
  if (hits > 0 && apply) fs.writeFileSync(abs, out, 'utf8')
  console.log(String(hits).padStart(3) + '  ' + rel + (hits ? '  → ' + [...new Set(src.match(RE))].join(',') : ''))
}

console.log(`\n${apply ? '已改写' : '试运行'}：${totalFiles} 个文件 / ${totalHits} 处端口；清单里不存在的文件 ${missing.length} 个`)
if (missing.length) console.log('缺失：\n  ' + missing.join('\n  '))
