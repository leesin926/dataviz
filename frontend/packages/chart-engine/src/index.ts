// Core
export * from './core/ChartRenderer'
export * from './core/ChartThemeManager'

// Chart generators (class-based)
export * from './charts/BarChart'
export * from './charts/LineChart'
export * from './charts/PieChart'
export * from './charts/ScatterChart'
export * from './charts/MapChart'
export * from './charts/GaugeChart'
export * from './charts/RadarChart'

// Vue Component
export { default as ChartComponent } from './components/ChartComponent.vue'

// Legacy exports (backward compatible)
export { default as ChartEngine } from './ChartEngine.vue'
export * from './renderer/BaseRenderer'
export * from './renderer/EChartsRenderer'
export * from './renderer/CanvasRenderer'
export * from './adapters/DataAdapter'
export * from './adapters/ChartConfigAdapter'
// themes 的 ThemeName 与 core/ChartThemeManager 的同名类型冲突（后者是对外契约），这里只具名转出其余导出
export { BUILT_IN_THEMES, getTheme, registerTheme } from './themes'
export * from './charts/bar'
export * from './charts/line'
export * from './charts/pie'
export * from './charts/scatter'
export * from './charts/map'
export * from './charts/radar'
export * from './charts/gauge'
export * from './charts/heatmap'
export * from './charts/treemap'
export * from './charts/sunburst'
export * from './charts/kpi-card'
export * from './charts/table'
export * from './interaction/linkage'
export * from './interaction/drill-down'
export * from './interaction/filter'
