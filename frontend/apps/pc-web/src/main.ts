import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import 'element-plus/dist/index.css'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import App from './App.vue'
import { router } from './router'
import { setupPermissionDirective } from '@dataviz/permission'
import { createPermissionGuard } from '@dataviz/permission'
import '@dataviz/shared-styles'
import { watchGlobalMourning } from '@dataviz/shared-styles'
import './styles/index.scss'
import { i18n, t } from './locales'
import { getPublicConfig, MOURNING_CONFIG_KEY, setAuthMessageResolver, subscribeMourningPush } from '@dataviz/api-client'

// 401/演示态等认证提示走应用层词典，避免 api-client 依赖 vue-i18n
setAuthMessageResolver(() => t('common.sessionExpired'))

// 全局哀悼模式由管理端系统配置驱动；首屏一次 HTTP 定初值，之后走免登推送通道
watchGlobalMourning(() => getPublicConfig(MOURNING_CONFIG_KEY), subscribeMourningPush)

const app = createApp(App)
const pinia = createPinia()

app.use(pinia)
app.use(i18n)
app.use(router)
app.use(ElementPlus, { locale: zhCn })

// 注册所有 Element Plus 图标
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

// 注册权限指令
setupPermissionDirective(app)

// 路由权限守卫
createPermissionGuard(router)

app.mount('#app')
