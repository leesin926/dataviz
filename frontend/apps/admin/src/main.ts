import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import 'element-plus/dist/index.css'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'

import '@dataviz/shared-styles'
import { watchGlobalMourning } from '@dataviz/shared-styles'
import { i18n, t } from './locales'
import { getPublicConfig, MOURNING_CONFIG_KEY, setAuthMessageResolver } from '@dataviz/api-client'
import App from './App.vue'
import router from './router'
import { setupPermissionDirective } from '@dataviz/permission/directive'

// 401/演示态等认证提示走应用层词典，避免 api-client 依赖 vue-i18n
setAuthMessageResolver((key) => t(key === 'demoMode' ? 'common.demoMode' : 'common.sessionExpired'))

// 全局哀悼模式由系统配置驱动；读不到或未开启都按不灰度处理
watchGlobalMourning(() => getPublicConfig(MOURNING_CONFIG_KEY))

const app = createApp(App)

app.use(createPinia())
app.use(i18n)
app.use(router)
app.use(ElementPlus, { locale: zhCn })

for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

setupPermissionDirective(app)

app.mount('#app')
