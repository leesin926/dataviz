const fs = require('fs')
const path = require('path')

/**
 * 离线缓存：把"上一次成功拿到的数据"落到 userData/offline-cache/<key>.json。
 *
 * 这不是 HTTP 缓存，也不替代后端：它只是本机能读到的最后一份快照，所以每条都带 savedAt，
 * 界面必须把它显示成"离线快照 · 某时刻"而不是"当前数据"。
 * key 只允许 [A-Za-z0-9._-]，防止渲染进程用 "../" 写到缓存目录外面。
 */
const DIR = 'offline-cache'
const MAX_KEY = 80
const SAFE_KEY = /^[A-Za-z0-9._-]+$/
const MAX_TEXT_BYTES = 4 * 1024 * 1024
const TEXT_EXT = new Set(['.json', '.csv', '.txt', '.tsv', '.ndjson', '.geojson'])

function cacheDir(userDataDir) {
  const dir = path.join(userDataDir, DIR)
  fs.mkdirSync(dir, { recursive: true })
  return dir
}

function target(userDataDir, key) {
  const k = String(key || '')
  if (!k || k.length > MAX_KEY || !SAFE_KEY.test(k) || k.includes('..')) {
    throw new Error('缓存键不合法：只允许字母、数字、点、下划线和连字符')
  }
  return path.join(cacheDir(userDataDir), `${k}.json`)
}

function write(userDataDir, key, payload) {
  const file = target(userDataDir, key)
  const entry = { key: String(key), savedAt: new Date().toISOString(), payload }
  fs.writeFileSync(file, JSON.stringify(entry))
  return { key: entry.key, savedAt: entry.savedAt, bytes: fs.statSync(file).size }
}

function read(userDataDir, key) {
  try {
    const raw = JSON.parse(fs.readFileSync(target(userDataDir, key), 'utf8'))
    if (!raw || typeof raw !== 'object' || !('savedAt' in raw)) return null
    return raw
  } catch {
    return null
  }
}

function list(userDataDir) {
  const dir = cacheDir(userDataDir)
  let names = []
  try {
    names = fs.readdirSync(dir)
  } catch {
    return []
  }
  const out = []
  for (const name of names) {
    if (!name.endsWith('.json')) continue
    const key = name.slice(0, -'.json'.length)
    const entry = read(userDataDir, key)
    if (!entry) continue
    // 体积在文件系统上问，不指望文件内容里带：entry 只有 key/savedAt/payload
    let bytes = 0
    try {
      bytes = fs.statSync(path.join(dir, name)).size
    } catch {
      bytes = 0
    }
    out.push({ key: entry.key, savedAt: entry.savedAt, bytes })
  }
  return out.sort((a, b) => String(b.savedAt).localeCompare(String(a.savedAt)))
}

function remove(userDataDir, key) {
  try {
    fs.unlinkSync(target(userDataDir, key))
    return true
  } catch {
    return false
  }
}

function clear(userDataDir) {
  const removed = list(userDataDir).length
  for (const item of list(userDataDir)) remove(userDataDir, item.key)
  return removed
}

/**
 * 本地文件拖拽导入：渲染进程只交出 File 对象，绝对路径由主进程用 webUtils 取，
 * 读也在主进程读 —— 不给渲染进程开 fs 权限（nodeIntegration 关着就是这个意思）。
 */
function readTextFile(fp) {
  const abs = path.resolve(fp)
  const ext = path.extname(abs).toLowerCase()
  if (!TEXT_EXT.has(ext)) {
    throw new Error(`不支持的文件类型：${ext || '无扩展名'}。可导入 ${[...TEXT_EXT].join(' / ')}`)
  }
  const size = fs.statSync(abs).size
  if (size > MAX_TEXT_BYTES) throw new Error(`文件 ${Math.round(size / 1024)} KB 超过 4 MB 上限`)
  const text = fs.readFileSync(abs, 'utf8')
  return { path: abs, name: path.basename(abs), ext: ext.slice(1), size, text }
}

module.exports = { cacheDir, write, read, list, remove, clear, readTextFile, TEXT_EXT, MAX_TEXT_BYTES }
