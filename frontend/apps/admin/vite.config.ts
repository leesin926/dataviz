import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src'),
      '@dataviz/shared-utils': resolve(__dirname, '../../packages/shared-utils/src'),
      '@dataviz/shared-types': resolve(__dirname, '../../packages/shared-types/src'),
      '@dataviz/api-client': resolve(__dirname, '../../packages/api-client/src'),
      '@dataviz/permission': resolve(__dirname, '../../packages/permission/src'),
    },
  },
  server: {
    port: 3100,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        // 免登全局配置推送通道走 /api/admin/ws/public，开发态要让 vite 透传 Upgrade
        ws: true,
      },
    },
  },
  build: {
    outDir: 'dist',
    rollupOptions: {
      output: {
        manualChunks: {
          'vue-vendor': ['vue', 'vue-router', 'pinia'],
          'element-plus': ['element-plus'],
        },
      },
    },
  },
})
