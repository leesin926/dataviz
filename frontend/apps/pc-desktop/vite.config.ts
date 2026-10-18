import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src'),
      // 大屏渲染引擎走源码直链：pnpm install 在本机是离线不可用，
      // 而 screen-engine 的 package.json main 本来就指向 src/index.ts，别名等价于它被链接后的解析结果
      '@dataviz/screen-engine': resolve(__dirname, '../../packages/screen-engine/src/index.ts'),
    },
  },
  base: './',
  server: {
    port: 3300,
    strictPort: true,
    host: true,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        // 免登全局配置推送通道走 /api/admin/ws/public，开发态要让 vite 透传 Upgrade
        ws: true,
      },
    },
  },
})
