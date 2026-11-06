import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'

/**
 * 前端唯一的 vitest 入口（2026-11-06 阶段 BG 建立）。
 * <p>
 * 为什么不按包各配一份：本仓 9 个 packages 与 3 个 apps 是同一条构建链（pnpm workspace + shamefullyHoist），
 * 分开配置会让"哪些代码在门禁里"再次变成每个目录各说各话 —— 上一轮 `common/*` 零测试就是因为没有统一入口。
 * <p>
 * environment 用 happy-dom 而不是 jsdom：被测面是图表数据适配与组件渲染断言，不需要真实布局引擎；
 * 少一个重型依赖也意味着这条门禁在别人机器上更容易跑起来。
 */
export default defineConfig({
  plugins: [vue()],
  test: {
    environment: 'happy-dom',
    include: ['packages/*/src/**/*.test.ts', 'apps/*/src/**/*.test.ts'],
    restoreMocks: true,
    reporters: ['default'],
  },
})
