import { createRouter, createWebHashHistory, type RouteRecordRaw } from 'vue-router'

/**
 * 必须是 hash 路由：打包后窗口加载的是 file://.../dist/index.html，
 * history 路由刷新或深链（#/screen-view）都会让 Electron 去找一个不存在的文件或目录。
 */
const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/LoginView.vue'),
    meta: { public: true },
  },
  {
    path: '/',
    name: 'Home',
    component: () => import('../views/Home.vue'),
  },
  {
    // 多窗口/多屏（M01-02-03/04）：主窗口用 window:openScreen 在指定显示器上开这个路由
    path: '/screen-view',
    name: 'ScreenView',
    component: () => import('../views/ScreenView.vue'),
  },
]

export const router = createRouter({
  history: createWebHashHistory(),
  routes,
  scrollBehavior: () => ({ left: 0, top: 0 }),
})

router.beforeEach((to) => {
  const token = localStorage.getItem('dataviz_access_token')
  if (to.path === '/login') {
    return token ? '/' : true
  }
  if (!token && !to.meta.public) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  return true
})
