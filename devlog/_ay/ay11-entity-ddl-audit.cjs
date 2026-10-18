const fs = require('fs')
const path = require('path')
const ROOT = 'E:/wowowo'
const SQL_DIR = path.join(ROOT, 'deploy/sql')
const BK = path.join(ROOT, 'backend')

// ---------- 1) 收集 DDL 里的表与其列（CREATE + ALTER ADD COLUMN） ----------
function walkSql(dir) {
  const out = []
  for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
    const fp = path.join(dir, e.name)
    if (e.isDirectory()) out.push(...walkSql(fp)); else if (/\.sql$/i.test(e.name)) out.push(fp)
  }
  return out
}
const tables = new Map() // name -> { schema, cols:Set, file }
let schema = '?'
for (const f of walkSql(SQL_DIR)) {
  const src = fs.readFileSync(f, 'utf8')
  const lines = src.split(/\r?\n/)
  let cur = null
  for (const ln of lines) {
    const use = ln.match(/^\s*USE\s+`?(\w+)`?\s*;/i)
    if (use) { schema = use[1]; continue }
    if (/^\s*--/.test(ln)) continue
    const ct = ln.match(/^\s*CREATE\s+TABLE(?:\s+IF\s+NOT\s+EXISTS)?\s+`?(\w+)`?/i)
    if (ct) {
      const name = ct[1].toLowerCase()
      if (!tables.has(name)) {
        // 备份表（*_bak_* / *_backup* / *_copy）不是活表，跳过口径由 ledger 记录
        tables.set(name, { schema, cols: new Set(), file: path.basename(f), backup: /_(bak|backup|copy)\d*/i.test(name) })
      }
      cur = name
      continue
    }
    if (cur) {
      const col = ln.match(/^\s*`(\w+)`\s+[A-Za-z]/)
      if (col) tables.get(cur).cols.add(col[1].toLowerCase())
      if (/^\s*\)/.test(ln)) cur = null
      continue
    }
    const alt = ln.match(/^\s*ALTER\s+TABLE\s+`?(\w+)`?\s*$/i) || ln.match(/^\s*ALTER\s+TABLE\s+`?(\w+)`?\s+(.*)$/i)
    if (alt) {
      const name = alt[1].toLowerCase()
      if (!tables.has(name)) tables.set(name, { schema, cols: new Set(), file: path.basename(f), virtual: true })
      const adds = (src.match(/ADD\s+COLUMN\s+`(\w+)`/gi) || [])
      void adds
      const one = ln.match(/ADD\s+(?:COLUMN\s+)?`(\w+)`/i)
      if (one) tables.get(name).cols.add(one[1].toLowerCase())
    }
    const addCol = ln.match(/^\s*ADD\s+(?:COLUMN\s+)?`(\w+)`/i)
    if (addCol) {
      // 续行形式：ALTER TABLE t\n ADD COLUMN a ..., ADD COLUMN b ...
      const prev = tables.get(cur || '__none__')
      void prev
    }
  }
  // 逐文件二次扫描：把 "ALTER TABLE x ADD [COLUMN] `y`" 多行块补齐
  let curAlt = null
  for (const ln of src.split(/\r?\n/)) {
    const a = ln.match(/^\s*ALTER\s+TABLE\s+`?(\w+)`?/i)
    if (a) curAlt = a[1].toLowerCase()
    if (curAlt) {
      for (const m of ln.matchAll(/ADD\s+(?:COLUMN\s+|FIELDS\s+\()?`(\w+)`/gi)) {
        if (!tables.has(curAlt)) tables.set(curAlt, { schema, cols: new Set(), file: path.basename(f), virtual: true })
        tables.get(curAlt).cols.add(m[1].toLowerCase())
      }
      if (/;/.test(ln)) curAlt = null
    }
    if (/^\s*CREATE\s+TABLE/i.test(ln)) curAlt = null
  }
}

// ---------- 2) 收集实体：@TableName + 是否继承 BaseEntity/TenantEntity ----------
function walkJava(dir) {
  const out = []
  if (!fs.existsSync(dir)) return out
  for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
    const fp = path.join(dir, e.name)
    if (e.isDirectory()) out.push(...walkJava(fp)); else if (/\.java$/i.test(e.name)) out.push(fp)
  }
  return out
}
const NEED_COLS = ['deleted']
const ents = []
for (const svc of fs.readdirSync(BK).filter(d => /-service$/.test(d))) {
  const srcDir = path.join(BK, svc, 'src/main/java')
  for (const f of walkJava(srcDir)) {
    const s = fs.readFileSync(f, 'utf8')
    const tn = s.match(/@TableName\("([^"]+)"\)/)
    if (!tn) continue
    const ext = s.match(/class\s+\w+\s+extends\s+(\w+)/)
    const base = ext ? ext[1] : ''
    if (base !== 'BaseEntity' && base !== 'TenantEntity') continue
    const logicOff = /@TableLogic\(exist\s*=\s*false\)/.test(s)
    ents.push({ svc, table: tn[1].toLowerCase(), entity: path.basename(f, '.java'), base, logicOff, file: f.slice(ROOT.length + 1) })
  }
}

// ---------- 3) 对账 ----------
const missingTable = []
const missingCol = []
const unknown = []
for (const e of ents) {
  const t = tables.get(e.table)
  if (!t) { unknown.push(e); continue }
  if (t.backup) continue
  for (const c of NEED_COLS) if (!t.cols.has(c)) missingCol.push({ ...e, schema: t.schema, ddlFile: t.file, hasDelete: false })
}
console.log('实体（继承 BaseEntity/TenantEntity 且带 @TableName）总数 =', ents.length)
console.log('DDL 里找不到的表名 =', unknown.length)
unknown.forEach(x => console.log('   ? ' + x.svc + '  ' + x.table + '  (' + x.entity + ')'))
console.log('\n【缺 `deleted` 列 ⇒ SELECT 必 500】共', missingCol.length, '处')
const bySvc = {}
missingCol.forEach(x => { bySvc[x.svc] = (bySvc[x.svc] || 0) + 1 })
Object.entries(bySvc).sort((a, b) => b[1] - a[1]).forEach(([k, v]) => console.log('   ' + k + '  ' + v))
console.log('')
missingCol.forEach(x => console.log('   ' + x.svc + '  ' + x.schema + '.' + x.table + '   <- ' + x.entity + '   DDL: ' + x.ddlFile))
fs.writeFileSync(path.join(ROOT, 'devlog/_ay/ay11-entity-ddl-audit.json'), JSON.stringify({ entityTotal: ents.length, unknown, missingCol }, null, 2))
