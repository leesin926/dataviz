# 手机 App 端开发文档

> 版本：v1.0  
> 日期：2026-09-17  
> 状态：初稿

---

## 一、概述

### 1.1 定位

手机 App 端面向重度移动用户，提供完整的移动端数据查看、交互能力。支持 iOS、Android、HarmonyOS 三平台，提供推送通知、离线包、生物识别、拍照上传、定位、语音、消息中心等原生能力。

### 1.2 核心能力

- 多平台支持（iOS/Android/HarmonyOS）
- 消息推送（系统推送 + 厂商通道）
- 离线数据包
- 生物识别登录
- 拍照上传数据
- 定位服务
- 语音交互
- 消息中心

---

## 二、技术栈

| 类别 | 技术 | 版本 | 说明 |
|------|------|------|------|
| 框架 | uni-app (Vue 3) | 4.x | 跨平台移动应用 |
| 语言 | TypeScript | 5.x | 类型安全 |
| UI 库 | uView Plus | 3.x | 移动端组件 |
| 图表 | uCharts + ECharts | - | 图表渲染 |
| 状态 | Pinia | 2.x | 状态管理 |
| 推送 | uni-push + 厂商通道 | - | 消息推送 |
| 存储 | uni-storage | - | 本地存储 |
| 构建 | Vite | 5.x | 构建工具 |

### 2.1 备选方案：Flutter

如需更强的原生性能和 UI 一致性，可考虑 Flutter 方案：

| 类别 | 技术 | 说明 |
|------|------|------|
| 框架 | Flutter 3.x | 跨平台框架 |
| 语言 | Dart | 编程语言 |
| 图表 | fl_chart | 图表库 |
| 状态 | Riverpod | 状态管理 |
| 网络 | Dio | HTTP 客户端 |

---

## 三、项目结构

```
packages/mobile-app/
├── src/
│   ├── pages/                    # 页面
│   │   ├── splash/               # 启动页
│   │   ├── login/                # 登录
│   │   ├── home/                 # 首页
│   │   ├── dashboard/            # 仪表板列表
│   │   ├── dashboard-view/       # 仪表板详情
│   │   ├── screen/               # 大屏列表
│   │   ├── screen-view/          # 大屏详情
│   │   ├── analysis/             # 自助分析
│   │   ├── message/              # 消息中心
│   │   ├── todo/                 # 待办审批
│   │   ├── upload/               # 拍照上传
│   │   ├── profile/              # 个人中心
│   │   └── settings/             # 设置
│   ├── components/               # 组件
│   │   ├── chart/                # 图表组件
│   │   ├── kpi/                  # KPI 卡片
│   │   ├── filter/               # 筛选器
│   │   ├── pull-refresh/         # 下拉刷新
│   │   └── common/               # 通用组件
│   ├── composables/              # 组合式函数
│   │   ├── useAuth.ts            # 认证
│   │   ├── usePush.ts            # 推送
│   │   ├── useOffline.ts         # 离线
│   │   ├── useBiometric.ts       # 生物识别
│   │   ├── useCamera.ts          # 相机
│   │   ├── useLocation.ts        # 定位
│   │   ├── useVoice.ts           # 语音
│   │   └── useNetwork.ts         # 网络状态
│   ├── stores/                   # 状态管理
│   │   ├── user.ts
│   │   ├── dashboard.ts
│   │   ├── message.ts
│   │   └── offline.ts
│   ├── api/                      # API 接口
│   │   ├── modules/
│   │   └── request.ts
│   ├── native/                   # 原生插件
│   │   ├── push/                 # 推送插件
│   │   ├── biometric/            # 生物识别
│   │   └── voice/                # 语音识别
│   ├── utils/                    # 工具函数
│   ├── static/                   # 静态资源
│   ├── App.vue
│   ├── main.ts
│   ├── manifest.json
│   ├── pages.json
│   └── uni.scss
├── native/                       # 原生工程 (可选)
│   ├── ios/
│   ├── android/
│   └── harmony/
├── vite.config.ts
└── package.json
```

---

## 四、核心功能实现

### 4.1 启动与登录

#### 4.1.1 启动页

```vue
<!-- pages/splash/index.vue -->
<template>
  <view class="splash-page">
    <image src="/static/logo.png" class="logo" mode="aspectFit" />
    <text class="app-name">数据可视化平台</text>
    <view class="loading">
      <uni-load-more status="loading" />
    </view>
  </view>
</template>

<script setup lang="ts">
onLoad(async () => {
  // 1. 检查网络
  const networkAvailable = await checkNetwork()
  
  // 2. 检查登录状态
  const token = uni.getStorageSync('token')
  
  if (token) {
    // 验证 Token 有效性
    const valid = await validateToken(token)
    if (valid) {
      // 尝试生物识别快速登录
      const biometricEnabled = uni.getStorageSync('biometricEnabled')
      if (biometricEnabled) {
        const success = await biometricAuth()
        if (success) {
          navigateToHome()
          return
        }
      }
      navigateToHome()
      return
    }
  }
  
  // 3. 跳转登录页
  uni.reLaunch({ url: '/pages/login/index' })
})

function navigateToHome() {
  uni.switchTab({ url: '/pages/home/index' })
}
</script>
```

#### 4.1.2 登录页

```vue
<!-- pages/login/index.vue -->
<template>
  <view class="login-page">
    <view class="login-header">
      <image src="/static/logo.png" class="logo" />
      <text class="title">数据可视化平台</text>
    </view>
    
    <view class="login-form">
      <!-- 账号密码登录 -->
      <view class="form-item">
        <uni-icons type="person" size="20" />
        <input
          v-model="form.username"
          placeholder="请输入账号"
          type="text"
        />
      </view>
      
      <view class="form-item">
        <uni-icons type="locked" size="20" />
        <input
          v-model="form.password"
          placeholder="请输入密码"
          type="password"
        />
      </view>
      
      <button class="login-btn" @click="handleLogin" :loading="loading">
        登录
      </button>
      
      <!-- 生物识别登录 -->
      <view class="biometric-login" v-if="biometricAvailable">
        <button class="biometric-btn" @click="handleBiometricLogin">
          <uni-icons type="finger" size="24" />
          <text>生物识别登录</text>
        </button>
      </view>
      
      <!-- 第三方登录 -->
      <view class="third-party">
        <text class="divider">其他登录方式</text>
        <view class="third-party-btns">
          <!-- #ifdef APP-PLUS -->
          <button @click="handleWechatLogin">
            <image src="/static/wechat.png" />
          </button>
          <!-- #endif -->
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
const form = reactive({
  username: '',
  password: ''
})
const loading = ref(false)
const biometricAvailable = ref(false)

onLoad(async () => {
  // 检查生物识别可用性
  biometricAvailable.value = await checkBiometric()
})

async function handleLogin() {
  if (!form.username || !form.password) {
    uni.showToast({ title: '请输入账号密码', icon: 'none' })
    return
  }
  
  loading.value = true
  try {
    const { login } = useAuth()
    await login(form.username, form.password)
    uni.switchTab({ url: '/pages/home/index' })
  } catch (error) {
    uni.showToast({ title: '登录失败', icon: 'none' })
  } finally {
    loading.value = false
  }
}

async function handleBiometricLogin() {
  const { biometricLogin } = useAuth()
  const success = await biometricLogin()
  if (success) {
    uni.switchTab({ url: '/pages/home/index' })
  }
}
</script>
```

### 4.2 消息推送

```typescript
// composables/usePush.ts
export function usePush() {
  // 初始化推送
  async function initPush() {
    // 获取推送 clientId
    const info = await uni.getPushClientId()
    const clientId = info.cid
    
    // 上报 clientId 到后端
    await pushApi.register({
      clientId,
      platform: uni.getSystemInfoSync().platform,
      deviceToken: await getDeviceToken()
    })
    
    // 监听推送消息
    uni.onPushMessage((res) => {
      if (res.type === 'receive') {
        handleMessage(res.data)
      } else if (res.type === 'click') {
        handleClick(res.data)
      }
    })
  }
  
  // 处理接收到的消息
  function handleMessage(data: PushMessage) {
    const messageStore = useMessageStore()
    messageStore.addMessage(data)
    
    // 角标 +1
    const badge = uni.getStorageSync('badge') || 0
    uni.setBadgeNumber(badge + 1)
  }
  
  // 处理点击消息
  function handleClick(data: PushMessage) {
    const { payload } = data
    
    if (payload.type === 'dashboard') {
      uni.navigateTo({
        url: `/pages/dashboard-view/index?id=${payload.id}`
      })
    } else if (payload.type === 'alert') {
      uni.navigateTo({
        url: `/pages/message/detail?id=${payload.id}`
      })
    }
  }
  
  // 获取厂商推送 Token
  async function getDeviceToken(): Promise<string> {
    const platform = uni.getSystemInfoSync().platform
    
    if (platform === 'ios') {
      const result = await uni.push.getMessageProvider()
      return result.token || ''
    }
    
    // Android 厂商通道
    // 由 uni-push 自动处理
    return ''
  }
  
  // 设置推送权限
  async function requestPermission() {
    // #ifdef APP-PLUS
    const result = await uni.requestPushNotification({})
    return result.ok
    // #endif
    return true
  }
  
  return { initPush, requestPermission }
}
```

### 4.3 离线功能

```typescript
// composables/useOffline.ts
export function useOffline() {
  const offlineStore = useOfflineStore()
  const networkStore = useNetworkStore()
  
  // 监听网络状态
  function watchNetwork() {
    uni.onNetworkStatusChange((res) => {
      networkStore.setOnline(res.isConnected)
      
      if (res.isConnected) {
        // 网络恢复，同步离线数据
        syncOfflineData()
      } else {
        // 断网，缓存当前数据
        cacheCurrentData()
      }
    })
  }
  
  // 缓存仪表板数据
  async function cacheDashboard(id: string) {
    const dashboard = await dashboardApi.get(id)
    const chartData: Record<string, any> = {}
    
    // 缓存每个图表的数据
    for (const item of dashboard.layout) {
      chartData[item.id] = await analysisApi.query(item.dataConfig)
    }
    
    offlineStore.cacheDashboard(id, {
      dashboard,
      chartData,
      cachedAt: Date.now()
    })
  }
  
  // 获取离线数据
  function getOfflineDashboard(id: string) {
    return offlineStore.getCachedDashboard(id)
  }
  
  // 同步离线数据
  async function syncOfflineData() {
    const pendingOps = offlineStore.getPendingOperations()
    
    for (const op of pendingOps) {
      try {
        await executeOperation(op)
        offlineStore.removePendingOperation(op.id)
      } catch (error) {
        // 同步失败，保留待下次重试
        console.error('Sync failed:', error)
      }
    }
  }
  
  return {
    watchNetwork,
    cacheDashboard,
    getOfflineDashboard,
    syncOfflineData
  }
}
```

### 4.4 生物识别

```typescript
// composables/useBiometric.ts
export function useBiometric() {
  // 检查生物识别可用性
  async function checkAvailability(): Promise<{
    available: boolean
    type: 'finger' | 'face' | 'none'
  }> {
    // #ifdef APP-PLUS
    try {
      const result = await uni.checkBiometricAuthentication({
        success: () => ({ available: true, type: 'finger' })
      })
      return { available: true, type: result.type }
    } catch {
      return { available: false, type: 'none' }
    }
    // #endif
    
    return { available: false, type: 'none' }
  }
  
  // 生物识别认证
  async function authenticate(): Promise<boolean> {
    // #ifdef APP-PLUS
    return new Promise((resolve) => {
      uni.startBiometricAuthentication({
        reason: '验证身份以登录',
        success: () => resolve(true),
        fail: () => resolve(false)
      })
    })
    // #endif
    
    return false
  }
  
  // 开启生物识别登录
  async function enableBiometric() {
    const available = await checkAvailability()
    if (!available.available) {
      uni.showToast({ title: '设备不支持生物识别', icon: 'none' })
      return false
    }
    
    // 验证一次
    const success = await authenticate()
    if (success) {
      uni.setStorageSync('biometricEnabled', true)
      uni.showToast({ title: '已开启', icon: 'success' })
      return true
    }
    return false
  }
  
  // 关闭生物识别登录
  function disableBiometric() {
    uni.removeStorageSync('biometricEnabled')
  }
  
  return {
    checkAvailability,
    authenticate,
    enableBiometric,
    disableBiometric
  }
}
```

### 4.5 拍照上传

```typescript
// composables/useCamera.ts
export function useCamera() {
  // 拍照
  async function takePhoto(): Promise<string> {
    return new Promise((resolve, reject) => {
      uni.chooseImage({
        count: 1,
        sourceType: ['camera'],
        success: (res) => resolve(res.tempFilePaths[0]),
        fail: reject
      })
    })
  }
  
  // 从相册选择
  async function chooseFromAlbum(maxCount = 9): Promise<string[]> {
    return new Promise((resolve, reject) => {
      uni.chooseImage({
        count: maxCount,
        sourceType: ['album'],
        success: (res) => resolve(res.tempFilePaths),
        fail: reject
      })
    })
  }
  
  // 上传图片
  async function uploadImage(filePath: string, onProgress?: (progress: number) => void): Promise<string> {
    return new Promise((resolve, reject) => {
      const task = uni.uploadFile({
        url: `${BASE_URL}/api/file/upload`,
        filePath,
        name: 'file',
        header: {
          Authorization: `Bearer ${useUserStore().token}`
        },
        success: (res) => {
          const data = JSON.parse(res.data)
          resolve(data.url)
        },
        fail: reject
      })
      
      if (onProgress) {
        task.onProgressUpdate((res) => {
          onProgress(res.progress)
        })
      }
    })
  }
  
  // 拍照上传完整流程
  async function takePhotoAndUpload(): Promise<string> {
    const filePath = await takePhoto()
    const url = await uploadImage(filePath)
    return url
  }
  
  return {
    takePhoto,
    chooseFromAlbum,
    uploadImage,
    takePhotoAndUpload
  }
}
```

### 4.6 定位服务

```typescript
// composables/useLocation.ts
export function useLocation() {
  // 获取当前位置
  async function getCurrentLocation(): Promise<{
    latitude: number
    longitude: number
    address?: string
  }> {
    return new Promise((resolve, reject) => {
      uni.getLocation({
        type: 'gcj02',
        success: (res) => {
          resolve({
            latitude: res.latitude,
            longitude: res.longitude
          })
        },
        fail: reject
      })
    })
  }
  
  // 逆地理编码
  async function reverseGeocode(lat: number, lng: number): Promise<string> {
    // 使用地图 SDK 逆地理编码
    // #ifdef APP-PLUS
    const result = await plus.geolocation.reverseGeocode(
      new plus.geolocation.Position({ latitude: lat, longitude: lng })
    )
    return result.address
    // #endif
    
    return ''
  }
  
  // 请求定位权限
  async function requestPermission(): Promise<boolean> {
    // #ifdef APP-PLUS
    const result = await uni.authorize({
      scope: 'scope.userLocation'
    })
    return result.ok
    // #endif
    return true
  }
  
  return {
    getCurrentLocation,
    reverseGeocode,
    requestPermission
  }
}
```

### 4.7 语音交互

```typescript
// composables/useVoice.ts
export function useVoice() {
  const isRecording = ref(false)
  const recorderManager = uni.getRecorderManager()
  const innerAudioContext = uni.createInnerAudioContext()
  
  // 开始录音
  function startRecord() {
    recorderManager.start({
      duration: 60000,
      sampleRate: 16000,
      numberOfChannels: 1,
      encodeFormat: 'aac'
    })
    isRecording.value = true
  }
  
  // 停止录音
  function stopRecord(): Promise<string> {
    return new Promise((resolve) => {
      recorderManager.onStop((res) => {
        isRecording.value = false
        resolve(res.tempFilePath)
      })
      recorderManager.stop()
    })
  }
  
  // 语音识别 (调用后端 ASR)
  async function recognizeSpeech(filePath: string): Promise<string> {
    // 上传音频文件
    const url = await uploadAudio(filePath)
    
    // 调用语音识别 API
    const result = await aiApi.speechToText({ audioUrl: url })
    return result.text
  }
  
  // 语音问数完整流程
  async function voiceQuery(): Promise<string> {
    // 1. 录音
    startRecord()
    
    // 等待用户停止（通过 UI 按钮）
    const filePath = await stopRecord()
    
    // 2. 语音识别
    const text = await recognizeSpeech(filePath)
    
    // 3. NL2SQL 查询
    const queryResult = await aiApi.nl2sql({ question: text })
    
    return queryResult.answer
  }
  
  // 文字转语音
  function speak(text: string) {
    innerAudioContext.src = `${BASE_URL}/api/tts?text=${encodeURIComponent(text)}`
    innerAudioContext.play()
  }
  
  return {
    startRecord,
    stopRecord,
    recognizeSpeech,
    voiceQuery,
    speak,
    isRecording
  }
}
```

### 4.8 消息中心

```vue
<!-- pages/message/index.vue -->
<template>
  <view class="message-page">
    <!-- 消息分类 -->
    <view class="message-tabs">
      <view
        v-for="tab in tabs"
        :key="tab.type"
        :class="['tab-item', { active: activeTab === tab.type }]"
        @click="activeTab = tab.type"
      >
        <text>{{ tab.name }}</text>
        <uni-badge
          v-if="tab.count > 0"
          :text="tab.count"
          type="error"
        />
      </view>
    </view>
    
    <!-- 消息列表 -->
    <scroll-view
      scroll-y
      class="message-list"
      @scrolltolower="loadMore"
    >
      <view
        v-for="msg in messageList"
        :key="msg.id"
        :class="['message-item', { unread: !msg.read }]"
        @click="readMessage(msg)"
      >
        <view class="message-icon">
          <uni-icons :type="getIcon(msg.type)" size="24" />
        </view>
        <view class="message-content">
          <view class="message-header">
            <text class="message-title">{{ msg.title }}</text>
            <text class="message-time">{{ formatTime(msg.createTime) }}</text>
          </view>
          <text class="message-body">{{ msg.content }}</text>
        </view>
      </view>
      
      <uni-load-more :status="loadingStatus" />
    </scroll-view>
    
    <!-- 全部已读 -->
    <view class="read-all" @click="markAllRead">
      <text>全部已读</text>
    </view>
  </view>
</template>

<script setup lang="ts">
const tabs = [
  { type: 'all', name: '全部', count: 0 },
  { type: 'alert', name: '告警', count: 0 },
  { type: 'approval', name: '审批', count: 0 },
  { type: 'system', name: '系统', count: 0 }
]

const activeTab = ref('all')
const messageList = ref<Message[]>([])
const loadingStatus = ref<'more' | 'loading' | 'noMore'>('more')

async function readMessage(msg: Message) {
  if (!msg.read) {
    await messageApi.read(msg.id)
    msg.read = true
    updateBadge()
  }
  
  // 跳转到详情
  if (msg.payload?.type === 'dashboard') {
    uni.navigateTo({
      url: `/pages/dashboard-view/index?id=${msg.payload.id}`
    })
  }
}

async function markAllRead() {
  await messageApi.readAll()
  messageList.value.forEach(msg => msg.read = true)
  updateBadge()
}
</script>
```

---

## 五、页面配置

### 5.1 pages.json

```json
{
  "pages": [
    {
      "path": "pages/splash/index",
      "style": { "navigationStyle": "custom" }
    },
    {
      "path": "pages/login/index",
      "style": { "navigationStyle": "custom" }
    },
    {
      "path": "pages/home/index",
      "style": { "navigationBarTitleText": "首页" }
    },
    {
      "path": "pages/dashboard/index",
      "style": {
        "navigationBarTitleText": "仪表板",
        "enablePullDownRefresh": true
      }
    },
    {
      "path": "pages/dashboard-view/index",
      "style": {
        "navigationBarTitleText": "仪表板",
        "enablePullDownRefresh": true
      }
    },
    {
      "path": "pages/screen/index",
      "style": { "navigationBarTitleText": "大屏" }
    },
    {
      "path": "pages/screen-view/index",
      "style": { "navigationStyle": "custom" }
    },
    {
      "path": "pages/analysis/index",
      "style": { "navigationBarTitleText": "自助分析" }
    },
    {
      "path": "pages/message/index",
      "style": { "navigationBarTitleText": "消息中心" }
    },
    {
      "path": "pages/todo/index",
      "style": { "navigationBarTitleText": "待办审批" }
    },
    {
      "path": "pages/upload/index",
      "style": { "navigationBarTitleText": "数据上传" }
    },
    {
      "path": "pages/profile/index",
      "style": { "navigationBarTitleText": "个人中心" }
    },
    {
      "path": "pages/settings/index",
      "style": { "navigationBarTitleText": "设置" }
    }
  ],
  "tabBar": {
    "color": "#999",
    "selectedColor": "#1890ff",
    "list": [
      {
        "pagePath": "pages/home/index",
        "text": "首页",
        "iconPath": "static/tab/home.png",
        "selectedIconPath": "static/tab/home-active.png"
      },
      {
        "pagePath": "pages/dashboard/index",
        "text": "看板",
        "iconPath": "static/tab/dashboard.png",
        "selectedIconPath": "static/tab/dashboard-active.png"
      },
      {
        "pagePath": "pages/message/index",
        "text": "消息",
        "iconPath": "static/tab/message.png",
        "selectedIconPath": "static/tab/message-active.png"
      },
      {
        "pagePath": "pages/profile/index",
        "text": "我的",
        "iconPath": "static/tab/profile.png",
        "selectedIconPath": "static/tab/profile-active.png"
      }
    ]
  },
  "globalStyle": {
    "navigationBarTextStyle": "black",
    "navigationBarTitleText": "数据可视化",
    "navigationBarBackgroundColor": "#ffffff",
    "backgroundColor": "#f5f5f5",
    "backgroundTextStyle": "dark"
  }
}
```

---

## 六、原生能力集成

### 6.1 manifest.json 权限配置

```json
{
  "app-plus": {
    "distribute": {
      "ios": {
        "UIBackgroundModes": ["remote-notification"],
        "privacyDescription": {
          "NSCameraUsageDescription": "用于拍照上传数据",
          "NSLocationWhenInUseUsageDescription": "用于定位功能",
          "NSMicrophoneUsageDescription": "用于语音问数",
          "NSFaceIDUsageDescription": "用于面容识别登录",
          "NSPhotoLibraryUsageDescription": "用于选择图片上传"
        }
      },
      "android": {
        "permissions": [
          "<uses-permission android:name=\"android.permission.CAMERA\"/>",
          "<uses-permission android:name=\"android.permission.ACCESS_FINE_LOCATION\"/>",
          "<uses-permission android:name=\"android.permission.RECORD_AUDIO\"/>",
          "<uses-permission android:name=\"android.permission.USE_BIOMETRIC\"/>",
          "<uses-permission android:name=\"android.permission.READ_EXTERNAL_STORAGE\"/>",
          "<uses-permission android:name=\"android.permission.WRITE_EXTERNAL_STORAGE\"/>",
          "<uses-permission android:name=\"android.permission.VIBRATE\"/>"
        ]
      }
    },
    "modules": {
      "Push": {},
      "Camera": {},
      "Geolocation": {},
      "Speech": {}
    }
  }
}
```

---

## 七、构建与发布

### 7.1 构建命令

```bash
# 开发 - App
pnpm dev:app

# 构建 - App
pnpm build:app

# 构建 - iOS
pnpm build:app:ios

# 构建 - Android
pnpm build:app:android

# 构建 - HarmonyOS
pnpm build:app:harmony
```

### 7.2 打包发布

| 平台 | 产物 | 发布渠道 |
|------|------|----------|
| iOS | .ipa | App Store / 企业签名 / TestFlight |
| Android | .apk / .aab | 应用宝 / 华为 / 小米 / 官网 |
| HarmonyOS | .app | 华为应用市场 |

---

## 八、功能点清单

| 编号 | 功能 | 优先级 | 状态 |
|------|------|--------|------|
| M01-04-01 | 多平台 | P0 | 待开发 |
| M01-04-02 | 消息推送 | P0 | 待开发 |
| M01-04-03 | 离线包 | P1 | 待开发 |
| M01-04-04 | 生物识别 | P2 | 待开发 |
| M01-04-05 | 拍照上传 | P1 | 待开发 |
| M01-04-06 | 定位服务 | P2 | 待开发 |
| M01-04-07 | 语音交互 | P2 | 待开发 |
| M01-04-08 | 消息中心 | P1 | 待开发 |
