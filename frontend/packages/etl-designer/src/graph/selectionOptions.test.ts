import { describe, expect, it } from 'vitest'
import { SELECTION_PLUGIN_NAME, buildSelectionOptions } from './selectionOptions'

/**
 * API-137：框选/多选在 x6 v3 的正确安装方式。
 *
 * v3 没有 `selecting` 这个 Graph 选项，也没有可用的 @antv/x6-plugin-selection 包（已废弃、不发布 JS），
 * 唯一正解是把核心导出的 Selection 插件 graph.use(new Selection(opts)) 装上。
 *
 * 这里只测纯数据（选项形状），不 import '@antv/x6'：
 * 该包是 dual-entry（main=lib/index.js 是 CJS，但 package.json 又声明 type:"module"），
 * vitest 的 SSR 解析会选中 CJS 的 lib/index.js 并在 ES module 作用域里抛 `exports is not defined`，
 * 修这个需要动 vitest.config.ts 的依赖优化/别名 —— 而本轮明令禁止改测试配置，
 * 因此 Graph 无法在本 harness 里实例化，行为面测试留待有浏览器/真机时补，此处先把选项形状钉死：
 * 一旦未来升级 x6 把某个键改名或删除，buildSelectionOptions 的精确断言会立刻红，
 * 而不是让框选又一次“配置了却什么都不做”。
 */
describe('buildSelectionOptions (API-137)', () => {
  it('精确锁定传给 new Selection() 的选项对象形状', () => {
    expect(buildSelectionOptions()).toEqual({
      enabled: true,
      rubberband: true,
      multiple: true,
      movable: true,
      showNodeSelectionBox: true,
      modifiers: 'shift',
      multipleSelectionModifiers: ['ctrl', 'meta', 'shift'],
    })
  })

  it('显式打开 rubberband —— v3 的 DefaultOptions.rubberband 是 false，不写就没有框选（缺陷根因）', () => {
    expect(buildSelectionOptions().rubberband).toBe(true)
  })

  it('modifiers 为 "shift"：框选让位给既有的左键拖拽平移（旧值 null 会让 x6 直接关掉平移）', () => {
    const opts = buildSelectionOptions()
    expect(opts.modifiers).toBe('shift')
    // 曾经的值是 null（= 空画布左键直接框选），与 panning 的"无修饰键"在 x6 看来等价，
    // 于是 Selection.init() 调 disablePanning() 把平移整个关掉 —— 这条断言就是钉住那个回归。
    expect(opts.modifiers).not.toBeNull()
  })

  it('multiple + movable：多选后整体拖动', () => {
    const opts = buildSelectionOptions()
    expect(opts.multiple).toBe(true)
    expect(opts.movable).toBe(true)
  })

  it('加选修饰键在 v3 默认 ctrl/meta 之外补上 shift', () => {
    expect(buildSelectionOptions().multipleSelectionModifiers).toEqual(['ctrl', 'meta', 'shift'])
  })

  it('卸载用插件名固定为 "selection"（graph.disposePlugins 靠它定位 Selection 插件）', () => {
    expect(SELECTION_PLUGIN_NAME).toBe('selection')
  })

  it('每次调用返回全新对象，避免多处 graph.use 共享同一可变引用', () => {
    expect(buildSelectionOptions()).not.toBe(buildSelectionOptions())
  })
})
