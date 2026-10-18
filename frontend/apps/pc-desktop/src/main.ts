import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import 'element-plus/dist/index.css'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import '@dataviz/shared-styles'
import { watchGlobalMourning, type MourningSubscriber } from '@dataviz/shared-styles'
import { request, getPublicConfig, MOURNING_CONFIG_KEY, subscribeMourningPush } from '@dataviz/api-client'
import { currentBase, isFileProtocol } from './desktop/apiBase'
import { i18n } from './locales'
import App from './App.vue'
import { router } from './router'
import './styles/index.scss'

// 打包态窗口是 file://，相对 /api 出不了本机：先用解析出来的绝对地址覆盖 axios 默认值
request.defaults.baseURL = currentBase()

// file:// 下 window.location.origin 是 "null"，api-client 拼不出那条 WS 地址；
// 打包态只保留首屏一次 HTTP 定初值，订阅器交一个空实现，不做静默降级
const noPush: MourningSubscriber = () => () => {}
watchGlobalMourning(
  () => getPublicConfig(MOURNING_CONFIG_KEY),
  isFileProtocol() ? noPush : subscribeMourningPush,
)

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
