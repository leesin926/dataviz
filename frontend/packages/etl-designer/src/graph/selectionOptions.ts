/**
 * 框选/多选插件参数的纯函数部分（缺陷 API-137）。
 *
 * 单独成模块、并把选项对象原样导出的原因：`Selection` 在 x6 v3 已并入主包（`import { Selection } from '@antv/x6'`），
 * 它不是 Graph 的 `selecting` 选项（该选项在 v3 根本不存在，写了会被静默忽略 —— 这正是 API-137 的根因），
 * 而是一个要 `graph.use(new Selection(opts))` 安装的核心插件。构造插件本身需要真实画布与 DOM，
 * 但"该传哪些键、值是什么"是可判定对错的纯数据，抽出来才能在没有浏览器时被测试钉死：
 * 一旦未来升级 x6 把某个键改名（比如 rubberband → xxx），这里会立刻红，而不是又一次静默失效。
 */

/** v3 里 Selection 插件注册到 graph 上的名字，卸载时 `graph.disposePlugins(SELECTION_PLUGIN_NAME)` 用它定位 */
export const SELECTION_PLUGIN_NAME = 'selection'

/** v3 支持的修饰键（见 @antv/x6/lib/common/modifier：'alt' | 'ctrl' | 'meta' | 'shift' | 'space'） */
export type SelectionModifierKey = 'alt' | 'ctrl' | 'meta' | 'shift' | 'space'

/**
 * 传给 `new Selection()` 的选项。
 * x6 v3 把插件并进了主包但没有把 `SelectionOptions` 透出根入口（`plugin/selection/index.d.ts` 内部类型），
 * 这里按构造函数签名声明结构等价的子集，避免 deep-import 包内路径。
 */
export interface EtlSelectionOptions {
  /** 插件总开关；构造默认 true，显式写出以便测试锁定 */
  enabled: true
  /** 橡皮筋框选开关。注意：x6 v3 的 DefaultOptions.rubberband 是 false，不显式打开就没有框选 */
  rubberband: true
  /** 允许多选（配合 rubberband 拖框圈中多个节点） */
  multiple: true
  /** 选中多个节点后一起拖动 */
  movable: true
  /** 给被选中的节点画出选框，提供视觉反馈（DefaultOptions 里此键缺省即 false） */
  showNodeSelectionBox: true
  /**
   * 触发框选所需的修饰键。
   *
   * 不能是 null：`panning` 那边不要修饰键，x6 的 `resolvePanningSelectionConflict()`
   * （@antv/x6/lib/plugin/selection/index.js:315-335）判定"两侧事件有交集 + modifiers 等价"后
   * 会直接 `graph.panning.disablePanning()`，空白拖拽平移就被框选静默吃掉了
   * （modifiers 比较把 null 与 undefined 视作同值，见 common/modifier/index.js:24-42）。
   * 挂到 'shift' 后两个手势彻底分开：左键拖拽 = 平移（见 panningOptions.ts），shift + 左键拖拽 = 框选，
   * 且两侧的 allow* 判定互斥（panning.js:108-118 与 selection/index.js:278-289），不会同时触发。
   */
  modifiers: SelectionModifierKey
  /**
   * 追加选择（在已有选区上增/删节点）所需的修饰键。
   * x6 v3 默认只认 ['ctrl', 'meta']；这里补上 'shift'，让 shift 也能加选（v3 确实支持，见 multipleSelectionModifiers）。
   */
  multipleSelectionModifiers: SelectionModifierKey[]
}

/**
 * 组装 designer 需要的框选/多选选项。全部键显式写死，任何一缺失或改名都应让 selectionOptions.test.ts 失败。
 * 框选手势是 shift + 左键拖拽（左键拖拽归平移），改动 modifiers 前先看 panningOptions.ts 的冲突说明。
 */
export function buildSelectionOptions(): EtlSelectionOptions {
  return {
    enabled: true,
    rubberband: true,
    multiple: true,
    movable: true,
    showNodeSelectionBox: true,
    modifiers: 'shift',
    multipleSelectionModifiers: ['ctrl', 'meta', 'shift'],
  }
}
