/**
 * preload：渲染进程能拿到的一切桌面能力都在这里，是白名单而不是转发器。
 *
 * 之所以能这么薄：主进程侧每个 handler 都再校验一次入参（缓存键、文件扩展名、
 * 路径类型、显示器编号），所以这层不需要重复设防，但也绝不能在渲染进程加回 fs / require。
 */
const { contextBridge, ipcRenderer, webUtils } = require('electron')

function invoke(channel) {
  return (...args) => ipcRenderer.invoke(channel, ...args)
}

contextBridge.exposeInMainWorld('electronAPI', {
  isDesktop: true,

  app: {
    info: invoke('app:info'),
    openPath: invoke('app:paths'),
  },

  window: {
    state: invoke('window:state'),
    create: invoke('window:create'),
    toggleMain: invoke('window:toggleMain'),
    openScreen: invoke('window:openScreen'),
  },

  shortcut: {
    list: invoke('shortcut:list'),
    register: invoke('shortcut:register'),
    unregisterAll: invoke('shortcut:unregisterAll'),
  },

  settings: {
    get: invoke('settings:get'),
    set: invoke('settings:set'),
  },

  updater: {
    state: invoke('updater:state'),
    setFeed: invoke('updater:setFeed'),
    check: invoke('updater:check'),
  },

  cache: {
    list: invoke('cache:list'),
    read: invoke('cache:read'),
    // 主进程按 {key,data} 一个对象收，这里负责把两个参数拼成它，别把形状差异漏给渲染层
    write: (key, data) => ipcRenderer.invoke('cache:write', { key, data }),
    remove: invoke('cache:remove'),
    clear: invoke('cache:clear'),
  },

  file: {
    /** 拖拽进来的文件：Electron 28 删掉了 File.path，只能在 preload 这一侧换算 */
    pathForFile: (file) => webUtils.getPathForFile(file),
    readText: invoke('file:readText'),
  },

  print: {
    run: invoke('print:run'),
  },

  export: {
    pdf: (payload) => ipcRenderer.invoke('export:pdf', payload || {}),
    png: (payload) => ipcRenderer.invoke('export:png', payload || {}),
    reveal: invoke('export:reveal'),
    openDir: invoke('export:openDir'),
    recent: invoke('export:recent'),
  },
})
