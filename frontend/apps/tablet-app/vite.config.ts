import { defineConfig } from 'vite'
import uni from '@dcloudio/vite-plugin-uni'
import { resolve } from 'path'

// 小程序端不支持把 workspace 包按"包外源码路径"参与构建：uni-mp 的 chunkFileNames 会用
// 相对 inputDir 的路径命名，源码在 app 目录外就会得到 `../../../packages/...` 这种 rollup 拒绝的名字。
// 因此 mp 构建走 app 自己的 node_modules 软链 + preserveSymlinks，让模块 id 落在 inputDir 内。
const IS_MP = String(process.env.UNI_PLATFORM || '').startsWith('mp')

export default defineConfig({
  plugins: [uni()],
  resolve: IS_MP
    ? { preserveSymlinks: true }
    : {
        // 与 pc-web 一致：workspace 包按源码消费，改包不需要重新 install
        alias: {
          '@dataviz/shared-types': resolve(__dirname, '../../packages/shared-types/src'),
          '@dataviz/uni-screen-engine': resolve(__dirname, '../../packages/uni-screen-engine'),
        },
      },
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        // H5 态下免登推送通道（/api/admin/ws/public）要透传 Upgrade
        ws: true,
      },
    },
  },
})
