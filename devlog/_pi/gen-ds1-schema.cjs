// PI4 · 目标实例（ds1）的基表镜像生成器
//
// 为什么需要这一步：`CREATE TABLE ... LIKE db_x.t` 里的基表必须在**同一台实例**上存在。
// dataviz-mysql-2 是空实例（docker-compose 里故意不挂 init SQL —— 见该文件的注释），
// 所以 pi4-02 在 3307 上直接跑会报 Unknown database，一半的月度表建不出来。
//
// 这个脚本连库（读结构），生成物 deploy/sql/shard/2026-11-07-pi4-01b-target-instance-schema.sql
// 才是不连库的迁移产物。改表清单只改 shard-config.cjs 的 TABLES —— 建表、镜像、回填三个脚本共用这一份。
//
// 跑法：node devlog/_pi/gen-ds1-schema.cjs [--apply]
//   默认只生成 SQL 文件（不碰库）；--apply 才在 ds1 上执行。执行不带 --force，
//   任何一条建表失败就当场非零退出 —— 半套镜像比没有镜像更难查。
const fs = require('fs')
const { execFileSync } = require('child_process')
const { TABLES, DS0_CONTAINER, DS1_CONTAINER } = require('./shard-config.cjs')

const OUT = 'E:/wowowo/deploy/sql/shard/2026-11-07-pi4-01b-target-instance-schema.sql'

// 表清单来自 shard-config.cjs —— 与建表生成器、回填驱动同一份，不在此处重写第二遍
function tablesBySchema () {
  const found = new Map()
  for (const t of TABLES) {
    if (!found.has(t.schema)) found.set(t.schema, [])
    if (!found.get(t.schema).includes(t.logic)) found.get(t.schema).push(t.logic)
  }
  return found
}

function dumpStructure (schema, tables) {
  // 口令只从容器环境变量取，不出现在命令行里（会进 ps 与 shell 历史）
  const inner = [
    'mysqldump -uroot -h 127.0.0.1',
    '--no-data', '--skip-comments', '--skip-dump-date',
    '--skip-routines', '--skip-triggers', '--skip-events',
    '--set-gtid-purged=OFF',
    schema, ...tables
  ].join(' ')
  return execFileSync('docker', ['exec', DS0_CONTAINER, 'sh', '-c',
    `MYSQL_PWD="$MYSQL_ROOT_PASSWORD" ${inner}`], { encoding: 'utf8', maxBuffer: 32 * 1024 * 1024 })
}

// mysqldump 给的是裸 CREATE TABLE，重跑会"表已存在"直接失败 —— 迁移产物必须可重跑
function makeIdempotent (sql) {
  return sql.replace(/^CREATE TABLE `/gm, 'CREATE TABLE IF NOT EXISTS `')
}

// 只保留结构与字符集设置：DROP TABLE 一定摘掉（这是镜像，不是重建），其余原样
function stripDrops (sql) {
  return sql
    .split('\n')
    .filter(line => !/^DROP TABLE/i.test(line.trim()))
    .join('\n')
    .replace(/\n{3,}/g, '\n\n')
    .trim()
}

const parts = []
const bySchema = tablesBySchema()

for (const [schema, tables] of bySchema) {
  const body = stripDrops(makeIdempotent(dumpStructure(schema, tables)))
  parts.push(`-- ==== ${schema}（${tables.join(', ')}）====\n` +
    `CREATE DATABASE IF NOT EXISTS \`${schema}\` DEFAULT CHARACTER SET utf8mb4;\n` +
    `USE \`${schema}\`;\n\n${body}\n`)
}

const tuples = []
for (const [schema, tables] of bySchema) {
  for (const table of tables) tuples.push(`  ('${schema}','${table}')`)
}

const readback = `
-- 回读自检：每张逻辑表在目标实例上应该恰好存在 1 张（结构镜像，不装数据），条数 = ${tuples.length}
SELECT CONCAT('@@BASE ', table_schema, '.', table_name) AS check_line
FROM information_schema.TABLES
WHERE (table_schema, table_name) IN (
${tuples.join(',\n')}
)
ORDER BY 1;
`

const header = `-- PI4 · 目标实例（ds1 / 3307）的分片基表镜像 —— 结构 only，零行
--
-- 为什么需要它：pi4-02 用 CREATE TABLE ... LIKE 建月度表，LIKE 要求基表在**同一实例**上存在。
-- dataviz-mysql-2 是空实例（docker-compose.yml 里没挂 init SQL，原因写在同一处），
-- 所以不镜像基表的话 3307 上一条月度表都建不出来，落在 ds1 的租户写入即失败。
--
-- 执行顺序：pi4-01（派生 tenant_id）→ **本文件（只在 ds1 跑）** → pi4-02（两个实例各跑）→ pi4-03（回填）
--
-- ⚠️ 这里建出来的 7 张基表在 ds1 上**永远是空的**，这是设计而不是漏：
--    分片读写都会被 ShardTableNameHandler 改写成 _YYYYMM 物理表，逻辑基表只是 LIKE 的模板。
--    真要在 ds1 上查数据，查的是 db_x.t_YYYYMM，不是 db_x.t。
--
-- 生成物，别手改：重跑 node devlog/_pi/gen-shard-ddl.cjs 与 node devlog/_pi/gen-ds1-schema.cjs。
SET NAMES utf8mb4;

`

fs.writeFileSync(OUT, header + parts.join('\n') + readback, 'utf8')
console.log('WROTE ' + OUT + ' bytes=' + fs.statSync(OUT).size)

if (process.argv.includes('--apply')) {
  // 不带 --force：任何一条建表失败就非零退出，"半套镜像"比"没跑"更难查
  execFileSync('docker', ['exec', '-i', DS1_CONTAINER, 'sh', '-c',
    'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot -h 127.0.0.1 --default-character-set=utf8mb4 -B'],
    { input: fs.readFileSync(OUT, 'utf8'), stdio: ['pipe', 'inherit', 'inherit'], maxBuffer: 32 * 1024 * 1024 })
  console.log('APPLIED to ' + DS1_CONTAINER)
} else {
  console.log('DRY-RUN（加 --apply 才真执行）')
}
