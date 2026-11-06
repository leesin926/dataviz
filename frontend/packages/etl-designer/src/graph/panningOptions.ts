/**
 * 画布平移（`Graph` 选项 `panning`）参数的纯函数部分。
 *
 * 单独成模块的原因与 `minimapOptions.ts` / `selectionOptions.ts` 相同：`new Graph()` 要真实 DOM，
 * 但"平移该挂在哪个手势上、会不会被框选插件静默关掉"是可判定对错的纯数据，抽出来才能在没有浏览器时被测试钉死。
 *
 * 背景：x6 v3 的 Selection 插件在 `init()` 里跑 `resolvePanningSelectionConflict()`
 * （@antv/x6/lib/plugin/selection/index.js:315-335）—— 只要框选与平移**事件类型有交集且 modifiers 等价**，
 * 就无条件 `graph.panning.disablePanning()`（graph/panning.js:184-189，把 `options.panning.enabled` 写成 false，
 * 于是左键/右键/空格/滚轮全部平移一起没）。判定用的 modifiers 比较是 `isModifierKeyEqual`
 * （common/modifier/index.js:24-42）：`undefined` 与 `null` 视作同值，即"两边都不要修饰键"就算等价。
 * 框选那边 `modifiers: null`（空画布左键直接拖拽框选）正好和 `panning: { enabled: true }`（默认无修饰键）撞车。
 * 既有手势不能因为新增手势被悄悄换掉，所以这里让框选改挂修饰键、平移保住左键拖拽。
 */

/**
 * v3 支持的平移手势（对应 @antv/x6/lib/graph/panning.d.ts:4 的私有 `EventType`）。
 * 该联合类型没从包根导出，这里结构等价重述，避免 deep-import 包内路径。
 */
export type PanningEventType = 'leftMouseDown' | 'rightMouseDown' | 'mouseWheel' | 'mouseWheelDown'

/**
 * 传给 `Graph` 选项 `panning` 的选项
 * （@antv/x6/lib/graph/panning.d.ts:5-9 的 `PanningOptions` 子集，构造签名见 graph/graph.d.ts:296）。
 */
export interface EtlPanningOptions {
  /** 总开关；`pannable` 读的就是它（graph/panning.js:11-13），false 时一切平移手势全废 */
  enabled: true
  /** null = 不要求修饰键，左键拖拽即平移（框选之前就在用的既有手势） */
  modifiers: null
  /** 触发平移的鼠标事件；判定见 graph/panning.js:143-150（左键）与 :119-127（右键） */
  eventTypes: PanningEventType[]
}

/**
 * 组装 designer 需要的平移选项。全部键显式写死，任何一缺失或改名都应让 panningOptions.test.ts 失败。
 *
 * - `leftMouseDown`：空白拖拽平移，与框选之前完全一致，不因框选回归。
 * - `rightMouseDown`：平移的第二手势；`preventDefaultContextMenu` 默认 true（graph/options.js:155），
 *   右键拖拽不会弹浏览器菜单。
 * - 不写 `mouseWheel`：`allowMouseWheel`（graph/panning.js:151-156）会拿裸滚轮平移，
 *   和 `ctrl + 滚轮` 缩放（见 GraphManager 的 mousewheel 选项）抢同一个输入设备。
 */
export function buildPanningOptions(): EtlPanningOptions {
  return {
    enabled: true,
    modifiers: null,
    eventTypes: ['leftMouseDown', 'rightMouseDown'],
  }
}
