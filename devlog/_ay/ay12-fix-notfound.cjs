const fs = require('fs')
const BK = 'E:/wowowo/backend'
const files = [
  'analysis-service/src/main/java/com/dataviz/analysis/service/impl/ReportServiceImpl.java',
  'dashboard-service/src/main/java/com/dataviz/dashboard/service/impl/DashboardServiceImpl.java',
  'dashboard-service/src/main/java/com/dataviz/dashboard/service/impl/WidgetServiceImpl.java',
  'collab-service/src/main/java/com/dataviz/collab/service/impl/CommentServiceImpl.java',
  'openapi-service/src/main/java/com/dataviz/openapi/service/impl/OpenApiAppServiceImpl.java',
  'openapi-service/src/main/java/com/dataviz/openapi/service/impl/WebhookServiceImpl.java',
]
// 只替换"字面量里含 not found / 不存在"的那一处 throw new RuntimeException(...)，其它 RuntimeException 一律不动
const RE = /throw new RuntimeException\(("(?:[^"\\]|\\.)*"|\+|[^;]*)\)/g
let total = 0
for (const rel of files) {
  const f = BK + '/' + rel
  const src = fs.readFileSync(f, 'utf8')
  const lines = src.split('\n')
  let n = 0
  for (let i = 0; i < lines.length; i++) {
    const m = lines[i].match(/throw new RuntimeException\((.+)\);/)
    if (!m) continue
    const arg = m[1]
    if (!/not found|不存在/i.test(arg)) continue
    lines[i] = lines[i].replace('throw new RuntimeException(' + arg + ');', 'throw new BizException(ErrorCode.NOT_FOUND, ' + arg + ');')
    n++
  }
  if (n) {
    let s = lines.join('\n')
    const adds = []
    if (!/import com\.dataviz\.common\.core\.exception\.BizException;/.test(s)) adds.push('import com.dataviz.common.core.exception.BizException;')
    if (!/import com\.dataviz\.common\.core\.result\.ErrorCode;/.test(s)) adds.push('import com.dataviz.common.core.result.ErrorCode;')
    if (adds.length) {
      const ls = s.split('\n')
      let at = -1
      ls.forEach((l, i) => { if (/^import \S/.test(l)) at = i })
      if (at < 0) at = ls.findIndex(l => /^package \S/.test(l))
      ls.splice(at + 1, 0, ...adds)
      s = ls.join('\n')
    }
    fs.writeFileSync(f, s)
    void RE
  }
  total += n
  console.log(n + ' 处  ' + rel.split('/').pop())
}
console.log('合计替换 =', total)
