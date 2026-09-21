import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'

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
]

export const router = createRouter({
  history: createWebHistory(),
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
