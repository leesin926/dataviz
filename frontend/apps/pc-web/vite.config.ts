import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueJsx from '@vitejs/plugin-vue-jsx'
import { resolve } from 'path'

export default defineConfig({
  plugins: [vue(), vueJsx()],
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src'),
      '@dataviz/shared-types': resolve(__dirname, '../../packages/shared-types/src'),
      '@dataviz/shared-utils': resolve(__dirname, '../../packages/shared-utils/src'),
      '@dataviz/api-client': resolve(__dirname, '../../packages/api-client/src'),
      '@dataviz/chart-engine': resolve(__dirname, '../../packages/chart-engine/src'),
      '@dataviz/screen-engine': resolve(__dirname, '../../packages/screen-engine/src'),
      '@dataviz/query-engine': resolve(__dirname, '../../packages/query-engine/src'),
      '@dataviz/etl-designer': resolve(__dirname, '../../packages/etl-designer/src'),
      '@dataviz/permission': resolve(__dirname, '../../packages/permission/src'),
    },
  },
  server: {
    // 5173 让给 uni H5 dev server（二者 localhost 双栈同端口会互相抢占）
    port: 5174,
    host: '0.0.0.0',
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/ws': {
        target: 'ws://localhost:8080',
        ws: true,
        changeOrigin: true,
      },
    },
  },
  css: {
    preprocessorOptions: {
      scss: {
        additionalData: `@use "@/styles/variables.scss" as *;`,
      },
    },
  },
  build: {
    outDir: 'dist',
    sourcemap: false,
    chunkSizeWarningLimit: 2000,
    rollupOptions: {
      output: {
        manualChunks: {
          'vue-vendor': ['vue', 'vue-router', 'pinia'],
          'element-plus': ['element-plus'],
          'echarts': ['echarts'],
        },
      },
    },
  },
})
