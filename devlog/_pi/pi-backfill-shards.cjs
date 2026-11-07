// PI4 · 分片回填驱动：把 7 张增长表的存量行按 (租户→实例, 时间→月表) 搬到物理分片，然后对账。
//
// 为什么是驱动脚本而不是直接跑 pi4-03 模板：跨实例搬运没有本地做法（MySQL 不能 INSERT 到另一台服务器），
// 必须 mysqldump→管道→另一台 mysql；而"搬完了到底对不对"只能靠两边行数对账，不能靠语句跑没报错。
// 对不平就非零退出、不出"完成"标记 —— 因为下一步（把逻辑表变成只读/让应用走分片）建立在"行数对得平"上，
// 那时才发现少行，就是真的丢数据了。
//
// 三条不会静默处理的规则：
//   1) tenant_id 为空的行不参与分片（MOD(NULL) 两边都数不到）→ 单独数、留在逻辑表（README 的 API-162 决定）。
//   2) 时间列为空的行落不到任何月表 → 单独数出来报错，不猜一个月。
//   3) 落在窗口外的行 → 数出来并要求扩窗口重跑，不让它留在没人读的表里。
//
// 跑法：
//   node devlog/_pi/pi-backfill-shards.cjs                 只数不写（dry-run，打印将搬到哪 + 当前对账差）
//   node devlog/_pi/pi-backfill-shards.cjs --apply         真回填 + 回填后再对账
//   node devlog/_pi/pi-backfill-shards.cjs --verify        只对账（回填之后、打开 shard.enabled 之前再跑一次）
//   node devlog/_pi/pi-backfill-shards.cjs --apply --reset 先清空两侧月表的派生行，再从头回填（半途失败后用）
const { execFileSync } = require('child_process')
const { TABLES, MONTHS, BUCKETS, DS0_CONTAINER, DS1_CONTAINER, bucketExpr, routablePredicate, monthBounds } = require('./shard-config.cjs')

const APPLY = process.argv.includes('--apply')
const VERIFY_ONLY = process.argv.includes('--verify')
const RESET = process.argv.includes('--reset')
const WINDOW = new Set(MONTHS)

function mysql (container, text) {
  // 口令只从容器环境变量取；数据走 stdin，不经 Windows 命令行（中文会被转成 GBK 打坏，见记忆里的 API-110）
  return execFileSync('docker', ['exec', '-i', container, 'sh', '-c',
    'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot -h 127.0.0.1 --default-character-set=utf8mb4 -B --raw'],
  { input: 'SET NAMES utf8mb4;\n' + text, encoding: 'utf8', maxBuffer: 64 * 1024 * 1024 })
}

function containerOf (bucket) {
  return bucket === 0 ? DS0_CONTAINER : DS1_CONTAINER
}

/** 源表按 (月份, 桶) 分组计数；桶为 NULL 表示路由维度缺值，月份为 NULL 表示时间列为空。 */
function expectCells (t) {
  const out = mysql(DS0_CONTAINER, `
SELECT CONCAT('@@CELL\t', IFNULL(m,'NO-TIME'), '\t', IFNULL(b,'UNASSIGNED'), '\t', c) FROM (
  SELECT DATE_FORMAT(\`${t.time}\`, '%Y%m') AS m, ${bucketExpr(t)} AS b, COUNT(*) AS c
  FROM \`${t.schema}\`.\`${t.logic}\`
  GROUP BY m, b) x ORDER BY 1;`)
  const cells = []
  for (const line of out.split('\n')) {
    if (!line.startsWith('@@CELL')) continue
    const [, m, b, c] = line.split('\t')
    cells.push({ month: m, bucket: b, count: Number(c) })
  }
  return cells
}

function countPhysical (t, month, bucket) {
  const out = mysql(containerOf(bucket),
    `SELECT COUNT(*) FROM \`${t.schema}\`.\`${t.logic}_${month}\`;`)
  return Number(out.split('\n').filter(l => /^\d+$/.test(l.trim()))[0])
}

/** 留在逻辑表不参与分片的行数：路由维度缺值的那些（openapi_log 的鉴权失败行就属于这类）。 */
function countLogicalRemainder (t) {
  const out = mysql(DS0_CONTAINER,
    `SELECT COUNT(*) FROM \`${t.schema}\`.\`${t.logic}\` WHERE NOT (${routablePredicate(t)});`)
  return Number(out.split('\n').filter(l => /^\d+$/.test(l.trim()))[0])
}

/** bucket=0：同实例内 INSERT ... SELECT，数据不出库。 */
function copyLocally (t, month) {
  const [from, to] = monthBounds(month)
  mysql(DS0_CONTAINER, `INSERT INTO \`${t.schema}\`.\`${t.logic}_${month}\`
  SELECT * FROM \`${t.schema}\`.\`${t.logic}\`
  WHERE \`${t.time}\` >= '${from}' AND \`${t.time}\` < '${to}'
    AND ${routablePredicate(t)} AND ${bucketExpr(t)} = 0;`)
  return 'ds0 本地写入完成'
}

/** bucket>=1：mysqldump 导出该 (月, 桶) 的行，把 INSERT 的目标表名换成月表后灌进目标实例。 */
function copyAcrossInstances (t, month, bucket) {
  const [from, to] = monthBounds(month)
  // 列名在这里不能带反引号：这条串最终进的是 sh -c，反引号会被 shell 当成命令替换
  // （症状是 "sh: line 1: create_time: command not found" 加一条少了列名的 1064 语法错）。
  // 列名来自 shard-config 的白名单，无空格无保留字，裸写安全；走 stdin 的 SQL 才用反引号。
  const where = `${t.time} >= '${from}' AND ${t.time} < '${to}'`
    + ` AND ${routablePredicate(t)} AND ${bucketExpr(t)} = ${bucket}`
  if (!APPLY) return `mysqldump --where=... -> ${containerOf(bucket)}:${t.logic}_${month}`
  const inner = ['mysqldump -uroot -h 127.0.0.1',
    '--no-create-info', '--complete-insert', '--skip-triggers',
    '--skip-comments', '--no-tablespaces', '--set-gtid-purged=OFF',
    `--where="${where.replace(/"/g, '\\"')}"`,
    t.schema, t.logic].join(' ')
  const dump = execFileSync('docker', ['exec', DS0_CONTAINER, 'sh', '-c',
    `MYSQL_PWD="$MYSQL_ROOT_PASSWORD" ${inner}`], { encoding: 'utf8', maxBuffer: 128 * 1024 * 1024 })
  // 只改写 INSERT 的目标表名；列名由 --complete-insert 带着，不依赖列序。
  // 其余语句（DROP/CREATE/LOCK/UNLOCK/USE）一概不进目标实例：这是搬运，不是重建。
  const redirected = dump.split('\n')
    .filter(line => /^INSERT INTO `/i.test(line))
    .map(line => line.replace('INSERT INTO `' + t.logic + '`', 'INSERT INTO `' + t.logic + '_' + month + '`'))
    .join('\n')
  if (!redirected) return '0 行（该月该桶没有数据）'
  // dump 里的 USE/CREATE/DROP 都被我滤掉了（不该在目标实例上重建库表），
  // 所以这里必须自己把库选上 —— 不选就是 ERROR 1046 No database selected。
  mysql(containerOf(bucket), 'USE `' + t.schema + '`;\n' + redirected)
  return redirected.split('\n').length + ' 条 INSERT 已搬运'
}

/**
 * 回填前的闸门：目标月表必须还是空的。
 * 为什么挡：本地 INSERT...SELECT 与跨实例搬运都不带去重（主键相同会撞号报错，
 * 但"上个月已经搬过一半"这种半截状态靠撞号是发现不了的），只有"起点为空"才让对账有意义。
 */
function assertTargetsEmpty () {
  const dirty = []
  for (const t of TABLES) {
    for (const month of MONTHS) {
      for (let bucket = 0; bucket < BUCKETS; bucket++) {
        const existing = countPhysical(t, month, bucket)
        if (existing > 0) dirty.push(`${t.schema}.${t.logic}_${month}@ds${bucket} 已有 ${existing} 行`)
      }
    }
  }
  if (dirty.length) {
    console.log('目标月表非空，拒绝回填（先确认是不是已经搬过一半）：')
    for (const line of dirty.slice(0, 10)) console.log('  ! ' + line)
    if (dirty.length > 10) console.log(`  ! ……共 ${dirty.length} 张`)
    process.exit(2)
  }
}

function reconcile (t) {
  const problems = []
  let moved = 0
  for (const cell of expectCells(t)) {
    if (cell.month === 'NO-TIME') {
      problems.push(`时间列为空 ${cell.count} 行（落不到月表，必须人工定时间列）`)
      continue
    }
    if (cell.bucket === 'UNASSIGNED') {
      problems.push(`路由维度缺值 ${cell.count} 行（按决定留在逻辑表，不搬）`)
      continue
    }
    if (!WINDOW.has(cell.month)) {
      problems.push(`窗口外月份 ${cell.month} 有 ${cell.count} 行（扩 MONTHS 后重跑）`)
      continue
    }
    const bucket = Number(cell.bucket)
    if (bucket >= BUCKETS) {
      problems.push(`桶号 ${bucket} 超出配置（BUCKETS=${BUCKETS}）`)
      continue
    }
    const actual = countPhysical(t, cell.month, bucket)
    moved += cell.count
    if (actual !== cell.count) {
      problems.push(`${t.logic}_${cell.month}@ds${bucket} 期望 ${cell.count} 实到 ${actual}`)
    }
  }
  return { problems, moved, remainder: countLogicalRemainder(t) }
}

/**
 * 半途失败后的收尾：清空两侧所有月表的派生行，回到"还没搬过"的起点。
 * 为什么敢清：回填只<b>读</b>逻辑表，月表里的每一行都是逻辑表的派生物，逻辑表一行没动；
 * 不给这条路，操作者只能手写 98 条 TRUNCATE，更容易漏掉一个实例，然后在半截状态上继续加行。
 * 要求 --apply --reset 同时给，避免一个手滑就清了别人已经在用的分片。
 */
function resetTargets () {
  const cleared = []
  for (const t of TABLES) {
    for (const month of MONTHS) {
      for (let bucket = 0; bucket < BUCKETS; bucket++) {
        const before = countPhysical(t, month, bucket)
        if (before === 0) continue
        mysql(containerOf(bucket), `TRUNCATE TABLE \`${t.schema}\`.\`${t.logic}_${month}\`;`)
        cleared.push(`${t.schema}.${t.logic}_${month}@ds${bucket} 清掉 ${before} 行`)
      }
    }
  }
  console.log('RESET：清掉 ' + cleared.length + ' 张月表的派生行（逻辑表一行没动）')
  for (const line of cleared.slice(0, 12)) console.log('  - ' + line)
  if (cleared.length > 12) console.log('  - ……共 ' + cleared.length + ' 张')
}

let failures = 0
console.log(APPLY ? '=== 回填 + 对账 ===' : VERIFY_ONLY ? '=== 只对账 ===' : '=== DRY-RUN（不写） ===')
if (RESET) {
  if (!APPLY) {
    console.log('--reset 必须和 --apply 一起给：不想真清就别带它')
    process.exit(2)
  }
  resetTargets()
}
if (APPLY) assertTargetsEmpty()

for (const t of TABLES) {
  const cells = expectCells(t)
  const total = cells.reduce((sum, c) => sum + c.count, 0)
  console.log(`\n[${t.schema}.${t.logic}] 源表 ${total} 行，时间列 ${t.time}`)

  for (const cell of cells) {
    if (cell.bucket === 'UNASSIGNED' || cell.month === 'NO-TIME' || !WINDOW.has(cell.month)) continue
    const bucket = Number(cell.bucket)
    const route = bucket === 0
      ? `ds0 本地 INSERT...SELECT -> ${t.logic}_${cell.month}`
      : `mysqldump --where -> ${containerOf(bucket)} 的 ${t.logic}_${cell.month}`
    if (!VERIFY_ONLY) {
      const done = APPLY ? (bucket === 0 ? copyLocally(t, cell.month) : copyAcrossInstances(t, cell.month, bucket)) : null
      console.log(`  ${cell.month}@ds${bucket} ${cell.count} 行 -> ${APPLY ? done : route}`)
    }
  }

  const result = reconcile(t)
  console.log(`  对账：应搬 ${result.moved} 行，未归属留表 ${result.remainder} 行`)
  if (result.problems.length) {
    // "留在逻辑表"是按决定该发生的事，不算失败；其余每一条都是对账不平
    failures += result.problems.filter(p => !p.includes('留在逻辑表')).length
    for (const problem of result.problems) console.log('  ! ' + problem)
  }
}

console.log('\n=== 读数 ===')
console.log('表数=' + TABLES.length + ' 实例数=' + BUCKETS + ' 窗口月数=' + MONTHS.length)
if (!APPLY && !VERIFY_ONLY) {
  console.log('没写任何东西：加 --apply 才真回填')
} else if (failures > 0) {
  console.log('对账不平 ' + failures + ' 项 —— 不要打开 shard.enabled，先修上面列出的每一行')
  process.exitCode = 1
} else {
  console.log('对账平：可以打开 shard.enabled 跑跨实例判据')
}
