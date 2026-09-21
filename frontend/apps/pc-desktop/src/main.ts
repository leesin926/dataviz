import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import 'element-plus/dist/index.css'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import '@dataviz/shared-styles'
import { watchGlobalMourning } from '@dataviz/shared-styles'
import { getPublicConfig, MOURNING_CONFIG_KEY } from '@dataviz/api-client'
import { i18n } from './locales'
import App from './App.vue'
import { router } from './router'
import './styles/index.scss'

// 全局哀悼模式由管理端系统配置驱动；读不到或未开启都按不灰度处理
watchGlobalMourning(() => getPublicConfig(MOURNING_CONFIG_KEY))

const app = createApp(App)
const pinia = createPinia()

app.use(pinia)
app.use(i18n)
app.use(router)
app.use(ElementPlus, { locale: zhCn })

for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

app.mount('#app')
