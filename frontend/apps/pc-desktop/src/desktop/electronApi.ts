/**
 * preload 暴露的那一层能力的类型面。
 *
 * desktop() 返回 null 是有意保留的状态：浏览器里跑 `vite` 时窗口没有 electronAPI，
 * 界面必须显示"当前不是桌面运行环境"，而不是把每个能力都渲染成能点、点了报错。
 */
import type { Screen } from '@dataviz/shared-types'

export interface Rect {
  x: number
  y: number
  width: number
  height: number
}

export interface AppInfo {
  version: string
  name: string
  platform: string
  packaged: boolean
  userDataDir: string
  electronVersion: string
  chromeVersion: string
}

export interface DisplayInfo {
  id: number
  label: string
  bounds: Rect
  workArea: Rect
  internal: boolean
  isPrimary: boolean
}

export interface WindowInfo {
  id: number
  title: string
  visible: boolean
  focused: boolean
  minimized: boolean
  maximized: boolean
  bounds: Rect
  isScreenWindow: boolean
}

export interface ShortcutInfo {
  accelerator: string
  action: string
  label: string
  registered: boolean
}

export interface DesktopSettings {
  closeToTray: boolean
  /** 托盘此刻在不在，只问主进程：落盘一个"我建过托盘"的旧值，界面就会撒谎 */
  trayAvailable: boolean
}

export interface CacheMeta {
  key: string
  savedAt: string
  bytes: number
}

export interface CacheEntry<T = unknown> {
  key: string
  savedAt: string
  payload: T
}

export interface UpdateConfig {
  feedUrl: string
  source: string | null
}

export interface UpdateResult {
  state: 'not-configured' | 'up-to-date' | 'available' | 'failed'
  currentVersion?: string
  latestVersion?: string
  file?: string
  url?: string
  sha512?: string
  manifestUrl?: string
  hint?: string
  error?: string
  note?: string
  stateLabel: string
}

export interface TextFile {
  path: string
  name: string
  ext: string
  size: number
  text: string
}

export interface PrintResult {
  success: boolean
  failureType: string
  printerCount: number
}

export interface ExportOptions {
  /** 文件名主体（不含扩展名），主进程会剥掉路径分隔符 */
  fileName?: string
  /** 仅 PDF：横向 */
  landscape?: boolean
  /** 仅 PDF：A3 / A4，默认 A4 */
  pageSize?: 'A3' | 'A4'
  /** 仅 PDF：是否带背景色，默认带 */
  printBackground?: boolean
}

export type ExportResult =
  | { saved: true; kind: 'pdf' | 'png'; filePath: string; fileName: string; bytes: number; dir: string }
  | { saved: false; reason: string }

export interface ExportRecord {
  filePath: string
  fileName: string
  bytes: number
  savedAt: string
  exists: boolean
}

export type RevealResult = { revealed: true; filePath: string } | { revealed: false; reason: string }

export type PathKind = 'userData' | 'downloads' | 'cache'

export interface DesktopApi {
  isDesktop: true
  app: {
    info(): Promise<AppInfo>
    openPath(kind: PathKind): Promise<string>
  }
  window: {
    state(): Promise<{ windows: WindowInfo[]; displays: DisplayInfo[] }>
    create(): Promise<number>
    toggleMain(): Promise<void>
    openScreen(payload: { target: string; displayId?: number }): Promise<number>
  }
  shortcut: {
    list(): Promise<ShortcutInfo[]>
    register(): Promise<ShortcutInfo[]>
    unregisterAll(): Promise<number>
  }
  settings: {
    get(): Promise<DesktopSettings>
    set(patch: { closeToTray: boolean }): Promise<DesktopSettings>
  }
  updater: {
    state(): Promise<UpdateConfig>
    setFeed(feedUrl: string): Promise<UpdateConfig>
    check(): Promise<UpdateResult>
  }
  cache: {
    list(): Promise<CacheMeta[]>
    read<T = unknown>(key: string): Promise<CacheEntry<T> | null>
    write(key: string, data: unknown): Promise<CacheMeta>
    remove(key: string): Promise<boolean>
    clear(): Promise<number>
  }
  file: {
    pathForFile(file: File): string
    readText(filePath: string): Promise<TextFile>
  }
  print: {
    run(): Promise<PrintResult>
  }
  export: {
    pdf(options?: ExportOptions): Promise<ExportResult>
    png(options?: ExportOptions): Promise<ExportResult>
    /** 只认本次运行里主进程亲手写出去的文件 */
    reveal(filePath: string): Promise<RevealResult>
    openDir(): Promise<string>
    recent(): Promise<ExportRecord[]>
  }
}

declare global {
  interface Window {
    electronAPI?: DesktopApi
  }
}

export function desktop(): DesktopApi | null {
  return window.electronAPI ?? null
}

/** 快照键：只允许 [A-Za-z0-9._-]，主进程再校验一次 */
export const SCREEN_CACHE_PREFIX = 'screen'

export function screenCacheKey(id: string | number): string {
  return `${SCREEN_CACHE_PREFIX}-${id}`
}

export async function saveScreenSnapshot(screen: Screen): Promise<CacheMeta> {
  const api = desktop()
  if (!api) throw new Error('no-desktop')
  return api.cache.write(screenCacheKey(screen.id), screen)
}

export async function loadScreenSnapshot<T = Screen>(id: string | number): Promise<CacheEntry<T> | null> {
  const api = desktop()
  if (!api) return null
  return api.cache.read<T>(screenCacheKey(id))
}

export function formatBytes(bytes: number): string {
  if (!Number.isFinite(bytes) || bytes <= 0) return '0 B'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(2)} MB`
}
