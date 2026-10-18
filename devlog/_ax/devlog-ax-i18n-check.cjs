const fs = require('fs')

function used(file, prefix) {
  const s = fs.readFileSync(file, 'utf8')
  const re = new RegExp("['\"]" + prefix.replace(/[.*+?^${}()|[\]\\]/g, '\\$&') + '([A-Za-z0-9_.]+)', 'g')
  const out = new Set()
  let m
  while ((m = re.exec(s))) out.add(m[1])
  return out
}
function has(doc, path) {
  return path.split('.').reduce((a, k) => (a == null ? undefined : a[k]), doc)
}

const pc = { zh: 'apps/pc-web/src/locales/zh-CN.json', en: 'apps/pc-web/src/locales/en-US.json' }
const ad = { zh: 'apps/admin/src/locales/zh-CN.json', en: 'apps/admin/src/locales/en-US.json' }

function check(viewFile, prefix, locales) {
  if (!fs.existsSync(viewFile)) {
    console.log('MISSING VIEW', viewFile)
    return
  }
  const zh = JSON.parse(fs.readFileSync(locales.zh, 'utf8'))
  const en = JSON.parse(fs.readFileSync(locales.en, 'utf8'))
  const keys = [...used(viewFile, prefix)]
  const miss = []
  for (const k of keys) {
    if (has(zh, prefix + k) === undefined) miss.push('zh:' + k)
    if (has(en, prefix + k) === undefined) miss.push('en:' + k)
  }
  const ns = prefix.replace('.', '')
  const declared = Object.keys((zh[ns] || {}))
  const declaredEn = Object.keys((en[ns] || {}))
  console.log(
    viewFile,
    'used=' + keys.length,
    'declaredZh=' + declared.length,
    'declaredEn=' + declaredEn.length,
    'missing=' + (miss.length ? miss.join(',') : 'none')
  )
  const unused = declared.filter((d) => !keys.some((u) => u === d || u.startsWith(d + '.')))
  console.log('  declared-but-not-used:', unused.join(',') || 'none')
}

check('apps/pc-web/src/views/ai/AiAssistant.vue', 'ai.', pc)
check('apps/admin/src/views/FileManage.vue', 'file.', ad)
console.log(
  'nav.ai=', has(JSON.parse(fs.readFileSync(pc.zh, 'utf8')), 'nav.ai'),
  'nav.files=', has(JSON.parse(fs.readFileSync(ad.zh, 'utf8')), 'nav.files')
)
