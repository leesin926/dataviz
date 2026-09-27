/**
 * @dataviz/uni-screen-engine —— uni 三端原生大屏渲染引擎（Phase 4）。
 *
 * 消费口径（三条都是实测出来的，不是风格偏好）：
 * ① **SFC 不在这里再导出**。小程序端的 `usingComponents` 由"模板标签 → 导入绑定 → 文件路径"静态生成，
 *    经过 barrel 再导出时 uni 编译器解不出路径，产物只剩一个 `.wxml`（js/json/wxss 全缺、页面找不到组件）；
 *    app 侧写深路径导入：`@dataviz/uni-screen-engine/src/screens/UniScreenShow.vue`。
 * ② 小程序构建还需要 `resolve.preserveSymlinks: true`（见各 app 的 vite.config.ts）：workspace 软链
 *    一旦被解析成真实路径就落到 app 目录外，uni-mp 的 chunkFileNames 会得到 `../../../packages/...`
 *    这种 rollup 直接拒绝的相对路径名。
 * ③ 小程序端不支持 `<component :is="">`（编译期报错），引擎模板按 kind 静态分支（core/registry.ts）。
 */

export { computeStage, componentBox, scaledFontSize, type StageMetrics } from './core/scale'
export {
  getViewport,
  uniRequest,
  createCanvasContext,
  hasUniRuntime,
  navigateTo,
  setNavigationBarTitle,
  stopPullDownRefresh,
  type UniRequestFn,
  type UniRequestOptions,
  type Viewport,
} from './core/uniRuntime'
export { toUCharts, type UChartsPayload, type ChartData } from './core/chartAdapter'
export { toTabular, cellText, type Tabular } from './core/tabular'
export { useComponentData, type DataEnv } from './core/dataSource'
export {
  resolveRenderer,
  resolveUniRenderer,
  registerUniRenderer,
  type ResolvedRenderer,
  type UniRendererKind,
} from './core/registry'
export { globalMourning, refreshGlobalMourning, watchGlobalMourningPush, MOURNING_CONFIG_KEY } from './core/mourning'
