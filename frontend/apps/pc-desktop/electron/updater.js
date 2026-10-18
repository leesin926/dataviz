const fs = require('fs')
const path = require('path')
const http = require('http')
const https = require('https')

/**
 * 更新检查（M01-02-07）。
 *
 * 这一版只做"检查"，不做"下载并安装"，两条都是硬约束不是偷懒：
 * ① 没有可用的更新源。electron-builder 的 generic publish 需要先有一个能放
 *    latest.yml + 安装包的内网地址；没有它，任何"检查更新"按钮都只能返回成功骗人。
 * ② 下载安装要签名与打包链路（winCodeSign / 证书），当前仓库没有配 publish，
 *    也没有 electron-updater 依赖。
 * 所以更新源从 userData/updates.json 里读，缺省状态如实回报 not-configured。
 */
const CONFIG_FILE = 'updates.json'
const MANIFEST = 'latest.yml'
const TIMEOUT_MS = 8000

function configPath(userDataDir) {
  return path.join(userDataDir, CONFIG_FILE)
}

function readConfig(userDataDir) {
  try {
    const raw = JSON.parse(fs.readFileSync(configPath(userDataDir), 'utf8'))
    const feedUrl = typeof raw.feedUrl === 'string' ? raw.feedUrl.trim() : ''
    return { feedUrl, source: CONFIG_FILE }
  } catch {
    return { feedUrl: '', source: null }
  }
}

function writeConfig(userDataDir, feedUrl) {
  const url = String(feedUrl || '').trim()
  if (!url) {
    try {
      fs.unlinkSync(configPath(userDataDir))
    } catch {
      // 本来就没有配置文件
    }
    return { feedUrl: '' }
  }
  if (!/^https?:\/\/\S+$/.test(url)) throw new Error('更新源地址必须是 http(s):// 开头的完整地址')
  fs.writeFileSync(configPath(userDataDir), JSON.stringify({ feedUrl: url }, null, 2))
  return { feedUrl: url }
}

function fetchText(url) {
  return new Promise((resolve, reject) => {
    const mod = url.startsWith('https://') ? https : http
    const req = mod.get(url, (res) => {
      if (res.statusCode && res.statusCode >= 300 && res.statusCode < 400 && res.headers.location) {
        res.resume()
        resolve(fetchText(new URL(res.headers.location, url).toString()))
        return
      }
      if (res.statusCode !== 200) {
        res.resume()
        reject(new Error(`更新源返回 ${res.statusCode}`))
        return
      }
      let body = ''
      res.setEncoding('utf8')
      res.on('data', (c) => {
        body += c
      })
      res.on('end', () => resolve(body))
    })
    req.setTimeout(TIMEOUT_MS, () => req.destroy(new Error(`更新源 ${TIMEOUT_MS / 1000}s 内没有响应`)))
    req.on('error', (e) => reject(new Error(`连不上更新源：${e.message}`)))
  })
}

/** latest.yml 是 electron-builder 生成的扁平 YAML，只需要 version / path / sha512 三行 */
function parseManifest(text) {
  const pick = (key) => {
    const m = text.match(new RegExp('^' + key + ':\\s*(.+)$', 'm'))
    return m ? m[1].trim().replace(/^["']|["']$/g, '') : ''
  }
  return { version: pick('version'), file: pick('path'), sha512: pick('sha512') }
}

function compareVersions(a, b) {
  const pa = String(a).split('.').map((n) => parseInt(n, 10) || 0)
  const pb = String(b).split('.').map((n) => parseInt(n, 10) || 0)
  for (let i = 0; i < Math.max(pa.length, pb.length); i++) {
    const x = pa[i] || 0
    const y = pb[i] || 0
    if (x !== y) return x > y ? 1 : -1
  }
  return 0
}

async function check(userDataDir, currentVersion) {
  const cfg = readConfig(userDataDir)
  if (!cfg.feedUrl) {
    return { state: 'not-configured', currentVersion, hint: `在 ${configPath(userDataDir)} 里写 feedUrl 才能检查更新` }
  }
  const url = `${cfg.feedUrl.replace(/\/+$/, '')}/${MANIFEST}`
  const text = await fetchText(url)
  const m = parseManifest(text)
  if (!m.version) return { state: 'failed', currentVersion, error: `更新源没有 version 字段：${url}` }
  const newer = compareVersions(m.version, currentVersion) > 0
  return {
    state: newer ? 'available' : 'up-to-date',
    currentVersion,
    latestVersion: m.version,
    file: m.file,
    url: m.file ? `${cfg.feedUrl.replace(/\/+$/, '')}/${m.file.replace(/^\/+/, '')}` : '',
    sha512: m.sha512,
    manifestUrl: url,
    // 下载安装不在这一版：见文件头第 ② 条。界面上必须把这行显示出来，不能只报"发现新版本"
    note: '本版本只检查不安装：下载安装包与签名链路尚未配置',
  }
}

module.exports = { readConfig, writeConfig, check, configPath, parseManifest, compareVersions }
