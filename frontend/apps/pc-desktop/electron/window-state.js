const fs = require('fs')
const path = require('path')

/**
 * 窗口位置尺寸持久化。
 *
 * 只存 bounds + isMaximized，不存"第几个显示器"：显示器编号在拔过屏之后会重排，
 * 按编号恢复会把窗口甩到用户看不见的地方。恢复时一律用"是否落在任一块屏的工作区里"
 * 这道校验兜底，落不下就回默认居中。
 */
const FILE = 'window-state.json'
const MIN_VISIBLE = 40

function statePath(userDataDir) {
  return path.join(userDataDir, FILE)
}

function load(userDataDir) {
  try {
    const raw = JSON.parse(fs.readFileSync(statePath(userDataDir), 'utf8'))
    if (!raw || typeof raw !== 'object') return null
    const b = raw.bounds
    if (!b || ![b.x, b.y, b.width, b.height].every((n) => Number.isFinite(n))) return null
    return { bounds: b, isMaximized: raw.isMaximized === true }
  } catch {
    // 首次启动没有文件、或者上一次写坏了：都是"按默认开"，不值得吵
    return null
  }
}

function save(userDataDir, win) {
  if (!win || win.isDestroyed()) return
  const bounds = win.isMinimized() ? win.getBounds() : win.getNormalBounds()
  const data = { bounds, isMaximized: win.isMaximized() }
  try {
    fs.writeFileSync(statePath(userDataDir), JSON.stringify(data, null, 2))
  } catch {
    // 写不进去只是下次记不住窗口位置，不能让主进程因为这个崩
  }
}

/** 落点必须在某块屏的工作区内，否则视为不可恢复 */
function isWithinDisplays(bounds, displays) {
  return displays.some((d) => {
    const wa = d.workArea
    return (
      bounds.x + MIN_VISIBLE >= wa.x &&
      bounds.y + MIN_VISIBLE >= wa.y &&
      bounds.x + bounds.width - MIN_VISIBLE <= wa.x + wa.width &&
      bounds.y + bounds.height - MIN_VISIBLE <= wa.y + wa.height
    )
  })
}

module.exports = { load, save, isWithinDisplays }
