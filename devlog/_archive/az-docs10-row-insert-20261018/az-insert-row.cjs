const fs = require('fs')
const path = 'E:/wowowo/docs/10-移动端大屏重构进度表.md'
const lines = fs.readFileSync(path, 'utf8').split('\n')
const idx = lines.findIndex(l => l.indexOf('阶段 AY5：10 条 500 按证据拆成两层') >= 0)
if (idx < 0) { console.log('ANCHOR NOT FOUND'); process.exit(1) }
if (lines[idx].indexOf('| 2026-10-18 |') !== 0) { console.log('UNEXPECTED ROW START: ' + lines[idx].slice(0, 40)); process.exit(1) }
const row = fs.readFileSync('E:/wowowo/devlog/_ay/az-changelog-row.md', 'utf8').replace(/\n+$/, '')
lines.splice(idx + 1, 0, row)
fs.writeFileSync(path, lines.join('\n'))
console.log('inserted after line ' + (idx + 1) + '，新行长 ' + row.length)
