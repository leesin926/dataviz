import { describe, expect, it } from 'vitest'
import { buildSelectionOptions, type EtlSelectionOptions } from './selectionOptions'
import {
  buildPanningOptions,
  type EtlPanningOptions,
  type PanningEventType,
} from './panningOptions'

/**
 * panning 与框选的手势分工契约。
 *
 * 被测的不是 x6 本身，而是"我们传给 x6 的这两组选项会不会让 x6 自己把平移关掉"。
 * x6 v3 的 Selection 插件在 init() 里跑 resolvePanningSelectionConflict()
 * （@antv/x6/lib/plugin/selection/index.js:315-335）：事件类型有交集且两侧 modifiers 等价时
 * 直接 graph.panning.disablePanning()（graph/panning.js:184-189 → options.panning.enabled = false，
 * 而 pannable/allowPanning/allowMouseWheel 全读这个开关，于是左键、右键、空格、滚轮平移一起没）。
 * 本包不能 import '@antv/x6'（该包 type:"module" 却发 CJS，vitest 的 SSR 解析会抛 exports is not defined，
 * 详见 selectionOptions.test.ts 顶部说明），所以下面按源码逐行复刻那个判定；
 * 复刻的部分都标了源码位置，升级 x6 时对照改。
 */

/** graph/options.js:90-93 —— Graph 选项 panning 合并后的默认 eventTypes（写 { enabled: true } 时就是这个） */
const X6_PANNING_DEFAULT_EVENT_TYPES: PanningEventType[] = ['leftMouseDown']
/** plugin/selection/index.js:23 —— Selection.DefaultOptions.eventTypes（我们没显式传，用的就是它） */
const X6_SELECTION_DEFAULT_EVENT_TYPES: PanningEventType[] = ['leftMouseDown', 'mouseWheelDown']

type PanningProbe = {
  enabled?: boolean
  modifiers?: string | string[] | null
  eventTypes?: PanningEventType[]
}
/** 比 EtlSelectionOptions 宽：下面的用例要试 modifiers = null / rubberband = false 这些"不该再出现"的值 */
type SelectionProbe = {
  enabled?: boolean
  rubberband?: boolean
  modifiers?: string | string[] | null
  eventTypes?: PanningEventType[]
}

/** common/modifier/index.js:6-23：数组进 or，字符串按 '|' 拆 or、按 '&' 拆 and */
function parseModifierKey(modifiers: string | string[]): { or: string[]; and: string[] } {
  const or: string[] = []
  const and: string[] = []
  if (Array.isArray(modifiers)) {
    or.push(...modifiers)
  } else {
    modifiers.split('|').forEach((item) => {
      if (item.indexOf('&') === -1) or.push(item)
      else and.push(...item.split('&'))
    })
  }
  return { or, and }
}

/** common/modifier/index.js:24-42：null 与 undefined 同值（都=无修饰键）；否则 or/and 两组各自排序后全等 */
function isModifierKeyEqual(a?: string | string[] | null, b?: string | string[] | null): boolean {
  if (a != null && b != null) {
    const m1 = parseModifierKey(a)
    const m2 = parseModifierKey(b)
    const equal = (x: string[], y: string[]) =>
      x.length === y.length && (x.length === 0 || x.every((item, i) => item === y[i]))
    return equal(m1.or.sort(), m2.or.sort()) && equal(m1.and.sort(), m2.and.sort())
  }
  if (a == null && b == null) return true
  return false
}

/**
 * index.js:316-334 的完整复刻：返回 x6 是否会在装上 Selection 的那一刻 disablePanning()。
 * true = 平移被框选吃掉（缺陷），false = 两个手势各走各的。
 */
function x6WouldDisablePanning(panning: PanningProbe, selection: SelectionProbe): boolean {
  if (selection.enabled !== true || selection.rubberband !== true) return false
  if (!panning || panning.enabled === false) return false
  const selectionEvents = selection.eventTypes ?? X6_SELECTION_DEFAULT_EVENT_TYPES
  const panningEvents = panning.eventTypes ?? X6_PANNING_DEFAULT_EVENT_TYPES
  const hasOverlappingEvents = selectionEvents.some((event) => new Set(panningEvents).has(event))
  const hasSameModifiers = isModifierKeyEqual(panning.modifiers, selection.modifiers)
  return hasOverlappingEvents && hasSameModifiers
}

describe('buildPanningOptions (框选带出的平移回归)', () => {
  it('精确锁定传给 Graph 选项 panning 的对象形状', () => {
    expect(buildPanningOptions()).toEqual({
      enabled: true,
      modifiers: null,
      eventTypes: ['leftMouseDown', 'rightMouseDown'],
    })
  })

  it('左键拖拽平移仍在 —— 框选之前的既有手势，不能因为加框选被换掉', () => {
    expect(buildPanningOptions().eventTypes).toContain('leftMouseDown')
    expect(buildPanningOptions().modifiers).toBeNull()
  })

  it('右键拖拽是平移的第二手势（preventDefaultContextMenu 默认 true，不会弹菜单）', () => {
    expect(buildPanningOptions().eventTypes).toContain('rightMouseDown')
  })

  it('不接管裸滚轮：eventTypes 里没有 mouseWheel，否则 allowMouseWheel 会和 ctrl+滚轮缩放抢输入', () => {
    expect(buildPanningOptions().eventTypes).not.toContain('mouseWheel')
  })

  it('每次调用返回全新对象，避免 Graph 选项与插件共享同一可变引用', () => {
    expect(buildPanningOptions()).not.toBe(buildPanningOptions())
  })
})

describe('panning × selection 手势分工', () => {
  it('当前这套组合不会命中 x6 的冲突分支 —— 平移不会被静默关掉', () => {
    // 按 EtlSelectionOptions 标注：被测的必须是源码真正传给 new Selection() 的那一份
    const selection: EtlSelectionOptions = buildSelectionOptions()
    const panning: EtlPanningOptions = buildPanningOptions()
    expect(x6WouldDisablePanning(panning, selection)).toBe(false)
  })

  it('两套 modifiers 必然不等价，这正是 x6 判定"不冲突"的依据', () => {
    const panning = buildPanningOptions()
    const selection = buildSelectionOptions()
    expect(isModifierKeyEqual(panning.modifiers, selection.modifiers)).toBe(false)
  })

  it('旧配置（selection.modifiers = null + panning 只写 enabled）会命中冲突、平移被关：这条把回归钉住', () => {
    expect(
      x6WouldDisablePanning(
        { enabled: true }, // GraphManager 曾经的样子：graph/options.js:90-93 合并出 eventTypes ['leftMouseDown']
        { ...buildSelectionOptions(), modifiers: null },
      ),
    ).toBe(true)
  })

  it('敏感性：两边 modifiers 相同且事件有交集时，x6 依旧会关平移（防止有人把 panning 也挂上 shift）', () => {
    expect(
      x6WouldDisablePanning(
        { ...buildPanningOptions(), modifiers: 'shift' },
        { ...buildSelectionOptions(), modifiers: 'shift' },
      ),
    ).toBe(true)
  })

  it('敏感性：modifiers 相同但事件类型彻底不重叠，x6 也不会关平移（另一条可行的分工路线）', () => {
    expect(
      x6WouldDisablePanning(
        { enabled: true, modifiers: null, eventTypes: ['rightMouseDown'] },
        { ...buildSelectionOptions(), modifiers: null },
      ),
    ).toBe(false)
  })

  it('没开 rubberband 就没有冲突可言，但平移配置本身不受影响', () => {
    expect(
      x6WouldDisablePanning(buildPanningOptions(), {
        ...buildSelectionOptions(),
        rubberband: false,
      }),
    ).toBe(false)
  })
})
