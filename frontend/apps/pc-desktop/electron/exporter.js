/**
 * 导出：PDF / PNG 直接落"下载"目录，不弹模态框。
 *
 * 为什么不弹保存框：大屏导出常常是无人值守的（值班截图、巡检留档），一个必须有人点"保存"
 * 的模态框会让整条链路停在对话框上；用户要的是"点一下就有一个确定的文件"。想换位置就用
 * 返回结果里的"打开所在目录"，而不是先选目录再导出。
 */
const fs = require('fs')
const path = require('path')
const { app, shell } = require('electron')

/**
 * 本次运行里我写出去的文件（路径 → 元信息）。
 * 只有登记在这里的路径才允许被"打开所在目录"：渲染进程传什么路径都能开的话，
 * 这个通道就变成了"任意文件定位器"。
 */
const written = new Map()

function targetDir() {
  const dir = app.getPath('downloads')
  fs.mkdirSync(dir, { recursive: true })
  return dir
}

function stamp(d) {
  const t = d instanceof Date ? d : new Date()
  const p = (n) => String(n).padStart(2, '0')
  return `${t.getFullYear()}${p(t.getMonth() + 1)}${p(t.getDate())}-${p(t.getHours())}${p(t.getMinutes())}${p(t.getSeconds())}`
}

/** 只当文件名用：分隔符、盘符、控制字符一律去掉，免得渲染进程用 ../ 把导出写到别处 */
function safeBase(base) {
  const cleaned = String(base || '')
    .replace(/[\\/:*?"<>|\u0000-\u001f]/g, '')
    .replace(/^\.+/, '')
    .trim()
  return (cleaned || 'dataviz').slice(0, 80)
}

function uniquePath(dir, base, ext) {
  const at = stamp()
  let candidate = path.join(dir, `${base}-${at}.${ext}`)
  for (let i = 2; fs.existsSync(candidate); i += 1) {
    candidate = path.join(dir, `${base}-${at}-${i}.${ext}`)
  }
  return candidate
}

function save(buffer, base, ext) {
  if (!buffer || buffer.length === 0) return { saved: false, reason: '导出内容为空' }
  const filePath = uniquePath(targetDir(), safeBase(base), ext)
  fs.writeFileSync(filePath, buffer)
  written.set(filePath, { bytes: buffer.length, savedAt: new Date().toISOString() })
  return { saved: true, kind: ext, filePath, fileName: path.basename(filePath), bytes: buffer.length, dir: path.dirname(filePath) }
}

async function toPdf(win, payload) {
  const opts = payload || {}
  const buffer = await win.webContents.printToPDF({
    printBackground: opts.printBackground !== false,
    landscape: opts.landscape === true,
    pageSize: opts.pageSize === 'A3' ? 'A3' : 'A4',
    margins: { marginType: 'none' },
  })
  return save(buffer, opts.fileName, 'pdf')
}

async function toPng(win, payload) {
  const opts = payload || {}
  const image = await win.webContents.capturePage()
  // 窗口最小化/隐藏时抓不到像素，这时如实报"抓不到"，不要落一个 0 字节或全透明的文件充成功
  if (image.isEmpty()) return { saved: false, reason: '窗口不可见，截不到画面' }
  return save(image.toPNG(), opts.fileName, 'png')
}

function reveal(filePath) {
  const target = String(filePath || '')
  if (!written.has(target)) throw new Error('只能打开本次导出的文件')
  if (!fs.existsSync(target)) return { revealed: false, reason: '文件已被移走或删除' }
  shell.showItemInFolder(target)
  return { revealed: true, filePath: target }
}

function openDir() {
  return shell.openPath(targetDir())
}

function recent() {
  return Array.from(written.entries())
    .map(([filePath, meta]) => ({ filePath, fileName: path.basename(filePath), ...meta, exists: fs.existsSync(filePath) }))
    .reverse()
}

module.exports = { toPdf, toPng, reveal, openDir, recent, targetDir }
