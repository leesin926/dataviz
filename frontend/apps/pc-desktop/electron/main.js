/**
 * pc-desktop 主进程。
 *
 * 安全基线（先说清楚，因为下面每一处暴露给渲染进程的能力都要对着它检查）：
 * - nodeIntegration 关闭、contextIsolation 开启：渲染进程只有 preload 暴露的那几个函数；
 * - sandbox 显式关闭，只为在 preload 里拿到 webUtils（Electron 28 已移除 File.path，
 *   拖拽进来的文件必须由主进程侧换算绝对路径）。文件系统读取仍然只在主进程做；
 * - 所有跨进程入参都在主进程侧再校验一次（缓存键、文件扩展名、路径类型、URL、显示器编号），
 *   渲染进程传来的字符串一律当恶意输入。
 * 桌面能力开关只持久化 closeToTray 一项（desktop-settings.json）。
 */
const fs = require('fs')
const path = require('path')
const { app, BrowserWindow, Tray, Menu, ipcMain, globalShortcut, screen, shell, dialog, nativeImage, session, Notification } = require('electron')
const windowState = require('./window-state')
const cache = require('./cache')
const updater = require('./updater')
const exporter = require('./exporter')

const DEV_PORT = 3300
const SETTINGS_FILE = 'desktop-settings.json'
const DEFAULT_BOUNDS = { width: 1400, height: 900 }
const SHORTCUTS = [
  { accelerator: 'CommandOrControl+Alt+D', action: 'toggle-main', label: '显示/隐藏主窗口' },
  { accelerator: 'CommandOrControl+Alt+W', action: 'close-current-window', label: '关闭当前窗口' },
]

/** 只有这几个 action 能被渲染进程点，白名单而不是透传：ipc 一旦被当"任意 shell"用就是 RCE */
const PERMISSION_ALLOW = new Set(['media', 'notifications', 'clipboard-sanitized-write', 'fullscreen'])

let mainWindow = null
let tray = null
const screenWindows = new Set()
const registeredShortcuts = []
let quitRequested = false

function userDataDir() {
  return app.getPath('userData')
}

function readSettings() {
  try {
    const raw = JSON.parse(fs.readFileSync(path.join(userDataDir(), SETTINGS_FILE), 'utf8'))
    return { closeToTray: raw.closeToTray !== false }
  } catch {
    return { closeToTray: true }
  }
}

/** 托盘是否存在只问当前进程，不写进配置文件：落一份"我建过托盘"的旧状态，界面就会撒谎 */
function settingsState() {
  return { closeToTray: readSettings().closeToTray, trayAvailable: !!tray }
}

function writeSettings(patch) {
  const next = { ...readSettings(), ...patch }
  try {
    fs.writeFileSync(path.join(userDataDir(), SETTINGS_FILE), JSON.stringify(next, null, 2))
  } catch {
    // 设置写不进去不影响本次运行
  }
  return next
}

/** 开发态走 vite 端口，打包后走 dist/index.html；两者都用 hash 路由，file:// 下 history 路由不可用 */
function appUrl(hashRoute) {
  const hash = hashRoute && hashRoute.startsWith('/') ? hashRoute : '/'
  if (app.isPackaged) {
    return { url: `file://${path.join(__dirname, '../dist/index.html')}${hash === '/' ? '' : hash}` }
  }
  return { url: `http://localhost:${DEV_PORT}/#${hash}` }
}

function webPrefs() {
  return {
    preload: path.join(__dirname, 'preload.js'),
    nodeIntegration: false,
    contextIsolation: true,
    // 关掉沙箱只是为了在 preload 里拿 webUtils（Electron 28 移除了 File.path）；
    // 文件读取仍然只在主进程做，渲染进程一个 fs 都摸不到
    sandbox: false,
  }
}

/** move/resize 会连发，落盘按 300ms 合并；否则拖一次窗口要写几百次文件 */
function debouncedPersist(win) {
  let timer = null
  return () => {
    if (timer) clearTimeout(timer)
    timer = setTimeout(() => {
      timer = null
      windowState.save(userDataDir(), win)
    }, 300)
  }
}

function buildWindow(opts) {
  const { route, isPrimary, bounds } = opts
  const win = new BrowserWindow({
    x: bounds ? bounds.x : undefined,
    y: bounds ? bounds.y : undefined,
    width: bounds ? bounds.width : DEFAULT_BOUNDS.width,
    height: bounds ? bounds.height : DEFAULT_BOUNDS.height,
    show: false,
    title: isPrimary ? 'DataViz 桌面端' : `DataViz 桌面端 · 窗口 ${Date.now() % 1000}`,
    icon: nativeImage.createFromPath(path.join(__dirname, 'assets/tray.png')),
    webPreferences: webPrefs(),
  })
  win.loadURL(appUrl(route || '/').url)
  win.once('ready-to-show', () => win.show())
  if (!app.isPackaged) win.webContents.openDevTools({ mode: 'detach' })

  const persist = debouncedPersist(win)
  win.on('move', persist)
  win.on('resize', persist)
  win.on('maximize', persist)
  win.on('unmaximize', persist)
  win.on('close', (e) => {
    persist()
    // 关窗不等于退程序：只有托盘真的在才允许藏进托盘，否则用户会以为程序没了
    if (isPrimary && readSettings().closeToTray && tray && !quitRequested) {
      e.preventDefault()
      win.hide()
    }
  })
  return win
}

function createMainWindow() {
  const saved = windowState.load(userDataDir())
  const displays = screen.getAllDisplays()
  const restored = saved && windowState.isWithinDisplays(saved.bounds, displays) ? saved.bounds : null
  const win = buildWindow({ route: '/', isPrimary: true, bounds: restored })
  mainWindow = win
  if (saved && saved.isMaximized) win.once('ready-to-show', () => win.maximize())
  win.on('closed', () => {
    mainWindow = null
  })
  return win
}

/** 多窗口（M01-02-03）：再开一个业务窗口，不参与"关窗进托盘"，也不覆盖主窗口引用 */
function createAuxWindow() {
  return buildWindow({ route: '/', isPrimary: false }).id
}

function focusMain() {
  if (!mainWindow || mainWindow.isDestroyed()) {
    createMainWindow()
    return
  }
  if (mainWindow.isMinimized()) mainWindow.restore()
  mainWindow.show()
  mainWindow.focus()
}

function toggleMain() {
  if (mainWindow && mainWindow.isVisible() && mainWindow.isFocused()) {
    mainWindow.hide()
  } else {
    focusMain()
  }
}

function createTray() {
  if (tray) return tray
  const image = nativeImage.createFromPath(path.join(__dirname, 'assets/tray.png'))
  if (image.isEmpty()) {
    // 图标读不到就明说，不要静默造一个点不到的托盘
    dialog.showErrorBox('托盘图标不可用', `读不到 ${path.join(__dirname, 'assets/tray.png')}，窗口关闭行为已退回"直接退出"`)
    writeSettings({ closeToTray: false })
    return null
  }
  tray = new Tray(image)
  tray.setToolTip('DataViz 桌面端')
  tray.setContextMenu(
    Menu.buildFromTemplate([
      { label: '显示主窗口', click: focusMain },
      { type: 'separator' },
      {
        label: '检查更新',
        click: async () => {
          const r = await runUpdateCheck()
          const body = r.state === 'available' ? `发现 ${r.latestVersion}\n${r.note}` : describeUpdateState(r)
          new Notification({ title: 'DataViz 更新检查', body }).show()
        },
      },
      { label: '打开缓存目录', click: () => void shell.openPath(cache.cacheDir(userDataDir())) },
      { type: 'separator' },
      {
        label: '退出',
        click: () => {
          quitRequested = true
          app.quit()
        },
      },
    ]),
  )
  tray.on('click', focusMain)
  return tray
}

function describeUpdateState(r) {
  if (r.state === 'not-configured') return `未配置更新源：${r.hint}`
  if (r.state === 'up-to-date') return `已是最新版（${r.currentVersion}）`
  if (r.state === 'failed') return `检查失败：${r.error}`
  return r.state
}

async function runUpdateCheck() {
  try {
    const r = await updater.check(userDataDir(), app.getVersion())
    return { ...r, stateLabel: describeUpdateState(r) }
  } catch (e) {
    return { state: 'failed', error: e.message, stateLabel: describeUpdateState({ state: 'failed', error: e.message }) }
  }
}

function registerShortcuts() {
  // 先注销再注册：同一组合键重复 register 会被判失败，界面就会出现"明明能用却显示未生效"
  globalShortcut.unregisterAll()
  registeredShortcuts.length = 0
  for (const item of SHORTCUTS) {
    const ok = globalShortcut.register(item.accelerator, () => {
      if (item.action === 'toggle-main') toggleMain()
      else if (item.action === 'close-current-window') {
        const win = BrowserWindow.getFocusedWindow()
        if (win) win.close()
      }
    })
    // 被别的程序抢走的快捷键注册不上的话，如实回报 failed，别让界面显示"已生效"
    registeredShortcuts.push({ ...item, registered: ok === true })
  }
}

function openScreenWindow(target, displayId) {
  const displays = screen.getAllDisplays()
  const index = Number.isInteger(displayId) ? displayId : (displays[0] || {}).id
  const display = displays.find((d) => d.id === Number(index)) || displays[0]
  if (!display) throw new Error('系统里没有可用显示器')
  const wa = display.workArea
  const win = new BrowserWindow({
    x: wa.x + 40,
    y: wa.y + 40,
    width: Math.min(1280, wa.width - 80),
    height: Math.min(760, wa.height - 80),
    title: `大屏 ${target}`,
    autoHideMenuBar: true,
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      nodeIntegration: false,
      contextIsolation: true,
      sandbox: false,
    },
  })
  screenWindows.add(win)
  win.loadURL(appUrl(`/screen-view?target=${encodeURIComponent(String(target))}`).url)
  win.on('closed', () => screenWindows.delete(win))
  return win.id
}

function displayInfo() {
  const primary = screen.getPrimaryDisplay()
  return screen.getAllDisplays().map((d) => ({
    id: d.id,
    label: `${d.scaleFactor}x ${d.size.width}×${d.size.height}`,
    bounds: d.bounds,
    workArea: d.workArea,
    internal: d.internal,
    isPrimary: d.id === primary.id,
  }))
}

function windowInfo() {
  return BrowserWindow.getAllWindows().map((w) => ({
    id: w.id,
    title: w.getTitle(),
    visible: w.isVisible(),
    focused: w.isFocused(),
    minimized: w.isMinimized(),
    maximized: w.isMaximized(),
    bounds: w.getBounds(),
    isScreenWindow: screenWindows.has(w),
  }))
}

/** 谁发起的 ipc 就作用在谁的窗口上；大屏窗口和主窗口各有各的画面 */
function senderWindow(e) {
  const win = BrowserWindow.fromWebContents(e.sender) || mainWindow
  if (!win || win.isDestroyed()) throw new Error('没有可导出的窗口')
  return win
}

async function printWindow(win) {
  const printers = await win.webContents.getPrintersAsync()
  return new Promise((resolve) => {
    win.webContents.print({ silent: false, printBackground: true }, (success, failureType) => {
      resolve({ success: success === true, failureType: failureType || '', printerCount: printers.length })
    })
  })
}

function registerIpc() {
  ipcMain.handle('app:info', () => ({
    version: app.getVersion(),
    name: app.getName(),
    platform: process.platform,
    packaged: app.isPackaged,
    userDataDir: userDataDir(),
    electronVersion: process.versions.electron,
    chromeVersion: process.versions.chrome,
  }))
  ipcMain.handle('app:paths', (e, kind) => {
    // 只开这三个目录：任何 kind 都当路径用的话，渲染进程就拿到"在任意目录打开文件管理器"了
    const allowed = { userData: 'userData', downloads: 'downloads', cache: 'cache' }
    if (!allowed[kind]) throw new Error('不允许的路径类型')
    return shell.openPath(app.getPath(allowed[kind]))
  })
  ipcMain.handle('window:state', () => ({ windows: windowInfo(), displays: displayInfo() }))
  ipcMain.handle('window:openScreen', (e, payload) => {
    const target = String((payload && payload.target) || '').trim()
    if (!target) throw new Error('要打开的大屏标识不能为空')
    return openScreenWindow(target, payload && payload.displayId)
  })
  ipcMain.handle('window:create', () => createAuxWindow())
  ipcMain.handle('window:toggleMain', () => toggleMain())
  ipcMain.handle('shortcut:list', () => registeredShortcuts)
  ipcMain.handle('shortcut:register', () => {
    registerShortcuts()
    return registeredShortcuts
  })
  ipcMain.handle('shortcut:unregisterAll', () => {
    globalShortcut.unregisterAll()
    const n = registeredShortcuts.length
    registeredShortcuts.length = 0
    return n
  })
  ipcMain.handle('updater:state', () => updater.readConfig(userDataDir()))
  ipcMain.handle('updater:setFeed', (e, feedUrl) => updater.writeConfig(userDataDir(), feedUrl))
  ipcMain.handle('updater:check', async () => runUpdateCheck())
  ipcMain.handle('cache:list', () => cache.list(userDataDir()))
  ipcMain.handle('cache:read', (e, key) => cache.read(userDataDir(), key))
  ipcMain.handle('cache:write', (e, payload) => cache.write(userDataDir(), payload && payload.key, payload && payload.data))
  ipcMain.handle('cache:remove', (e, key) => cache.remove(userDataDir(), key))
  ipcMain.handle('cache:clear', () => cache.clear(userDataDir()))
  ipcMain.handle('file:readText', (e, filePath) => cache.readTextFile(filePath))
  ipcMain.handle('print:run', async (e) => {
    const win = BrowserWindow.fromWebContents(e.sender) || mainWindow
    return win ? printWindow(win) : { success: false, failureType: 'no-window', printerCount: 0 }
  })
  // 导出走"发起它的那个窗口"：大屏窗口的画面要导出大屏，不能一律抓主窗口
  ipcMain.handle('export:pdf', async (e, payload) => exporter.toPdf(senderWindow(e), payload))
  ipcMain.handle('export:png', async (e, payload) => exporter.toPng(senderWindow(e), payload))
  ipcMain.handle('export:reveal', (e, filePath) => exporter.reveal(filePath))
  ipcMain.handle('export:openDir', () => exporter.openDir())
  ipcMain.handle('export:recent', () => exporter.recent())
  ipcMain.handle('settings:get', () => settingsState())
  ipcMain.handle('settings:set', (e, patch) => {
    writeSettings({ closeToTray: !!(patch && patch.closeToTray) })
    if (!patch || !patch.closeToTray) {
      if (tray) {
        tray.destroy()
        tray = null
      }
    } else if (!tray) {
      createTray()
    }
    return settingsState()
  })
}

function guardPermissions() {
  session.defaultSession.setPermissionRequestHandler((wc, permission, callback, details) => {
    const grant = PERMISSION_ALLOW.has(permission)
    if (!grant) console.warn('已拒绝权限请求:', permission, details.requestingOrigin || '')
    callback(grant)
  })
  // 请求 external 打开的一律只允许 http(s)：file:// 与自定义协议会让窗口把用户带到本机文件上
  app.on('web-contents-created', (_e, contents) => {
    contents.setWindowOpenHandler(({ url }) => {
      if (/^https?:\/\//.test(url)) {
        void shell.openExternal(url)
        return { action: 'deny' }
      }
      console.warn('已拦截非 http(s) 的新窗口:', url)
      return { action: 'deny' }
    })
    contents.on('will-navigate', (event, url) => {
      if (url.startsWith('file://') || url.startsWith(`http://localhost:${DEV_PORT}`)) return
      event.preventDefault()
      if (/^https?:\/\//.test(url)) void shell.openExternal(url)
    })
  })
}

const gotLock = app.requestSingleInstanceLock()
if (!gotLock) {
  app.quit()
} else {
  app.on('second-instance', () => {
    focusMain()
    const win = BrowserWindow.getAllWindows().find((w) => w === mainWindow)
    if (win && win.isMinimized()) win.restore()
  })

  app.whenReady().then(() => {
    registerIpc()
    guardPermissions()
    createMainWindow()
    if (readSettings().closeToTray) createTray()
    registerShortcuts()

    app.on('activate', () => {
      if (BrowserWindow.getAllWindows().length === 0) createMainWindow()
    })
  })

  app.on('browser-window-focus', () => {
    // 快捷键可能被别的程序抢走后注册失败，聚焦时补一次注册（幂等）
    if (registeredShortcuts.some((s) => !s.registered)) registerShortcuts()
  })

  app.on('window-all-closed', () => {
    if (process.platform !== 'darwin') app.quit()
  })

  app.on('will-quit', () => {
    globalShortcut.unregisterAll()
    if (mainWindow && !mainWindow.isDestroyed()) windowState.save(userDataDir(), mainWindow)
  })

  app.on('quit', () => {
    if (tray) tray.destroy()
  })
}
