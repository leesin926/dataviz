# 平板 App 端开发文档

> 版本：v1.0  
> 日期：2026-09-17  
> 状态：初稿

---

## 一、概述

### 1.1 定位

平板 App 端面向会议、演示、协作场景，充分利用平板大屏触控优势。提供横竖屏自适应、触控手势、手写批注、分屏、演示模式、投屏、会议协作等功能，是会议室和移动办公的理想选择。

### 1.2 核心能力

- 横竖屏自适应
- 触控手势操作
- 手写批注
- 分屏多任务
- 演示模式
- 投屏（AirPlay/Miracast）
- 会议协作

---

## 二、技术栈

| 类别 | 技术 | 版本 | 说明 |
|------|------|------|------|
| 框架 | uni-app (Vue 3) | 4.x | 跨平台 |
| 语言 | TypeScript | 5.x | 类型安全 |
| UI 库 | uView Plus | 3.x | 移动端组件 |
| 图表 | uCharts + ECharts | - | 图表渲染 |
| 手写 | canvas + 自研批注引擎 | - | 手写批注 |
| 投屏 | AirPlay / Miracast SDK | - | 投屏能力 |
| 状态 | Pinia | 2.x | 状态管理 |

---

## 三、项目结构

```
packages/tablet-app/
├── src/
│   ├── pages/
│   │   ├── splash/               # 启动页
│   │   ├── login/                # 登录
│   │   ├── home/                 # 首页 (横屏优化)
│   │   ├── dashboard/            # 仪表板列表
│   │   ├── dashboard-view/       # 仪表板详情
│   │   ├── screen/               # 大屏列表
│   │   ├── screen-view/          # 大屏展示
│   │   ├── presentation/         # 演示模式
│   │   ├── annotation/           # 批注模式
│   │   ├── meeting/              # 会议协作
│   │   ├── analysis/             # 自助分析
│   │   ├── profile/              # 个人中心
│   │   └── settings/             # 设置
│   ├── components/
│   │   ├── chart/                # 图表组件
│   │   ├── annotation/           # 批注组件
│   │   │   ├── CanvasBoard.vue   # 画布
│   │   │   ├── PenTool.vue       # 画笔工具
│   │   │   ├── TextTool.vue      # 文本工具
│   │   │   └── Toolbar.vue       # 工具栏
│   │   ├── gesture/              # 手势组件
│   │   ├── presentation/         # 演示组件
│   │   ├── split-view/           # 分屏组件
│   │   └── common/
│   ├── composables/
│   │   ├── useOrientation.ts     # 屏幕方向
│   │   ├── useGesture.ts         # 手势
│   │   ├── useAnnotation.ts      # 批注
│   │   ├── usePresentation.ts    # 演示模式
│   │   ├── useScreenCast.ts      # 投屏
│   │   ├── useMeeting.ts         # 会议协作
│   │   └── useSplitView.ts       # 分屏
│   ├── stores/
│   ├── api/
│   ├── utils/
│   ├── static/
│   ├── App.vue
│   ├── main.ts
│   ├── manifest.json
│   ├── pages.json
│   └── uni.scss
├── vite.config.ts
└── package.json
```

---

## 四、核心功能实现

### 4.1 横竖屏适配

```typescript
// composables/useOrientation.ts
export function useOrientation() {
  const orientation = ref<'portrait' | 'landscape'>('portrait')
  const screenWidth = ref(0)
  const screenHeight = ref(0)
  
  function updateOrientation() {
    const info = uni.getSystemInfoSync()
    screenWidth.value = info.screenWidth
    screenHeight.value = info.screenHeight
    
    orientation.value = screenWidth.value > screenHeight.value
      ? 'landscape'
      : 'portrait'
  }
  
  // 监听屏幕旋转
  function watchOrientation() {
    // #ifdef APP-PLUS
    plus.screen.addEventListener('orientationchange', updateOrientation)
    // #endif
    
    // 监听窗口变化
    uni.onWindowResize((res) => {
      screenWidth.value = res.size.windowWidth
      screenHeight.value = res.size.windowHeight
      orientation.value = screenWidth.value > screenHeight.value
        ? 'landscape'
        : 'portrait'
    })
  }
  
  // 强制屏幕方向
  function setOrientation(dir: 'portrait' | 'landscape') {
    // #ifdef APP-PLUS
    plus.screen.lockOrientation(
      dir === 'landscape' ? 'landscape-primary' : 'portrait-primary'
    )
    // #endif
  }
  
  // 响应式布局参数
  const layoutConfig = computed(() => {
    if (orientation.value === 'landscape') {
      return {
        columns: 3,        // 横屏3列
        chartHeight: '45vh',
        sidebarWidth: '280px',
        headerHeight: '56px'
      }
    }
    return {
      columns: 1,          // 竖屏单列
      chartHeight: '300px',
      sidebarWidth: '100%',
      headerHeight: '48px'
    }
  })
  
  return {
    orientation,
    screenWidth,
    screenHeight,
    layoutConfig,
    watchOrientation,
    setOrientation
  }
}
```

### 4.2 触控手势

```typescript
// composables/useGesture.ts
export function useGesture(elementRef: Ref<HTMLElement | null>) {
  const scale = ref(1)
  const rotate = ref(0)
  const translateX = ref(0)
  const translateY = ref(0)
  
  let initialDistance = 0
  let initialAngle = 0
  let initialScale = 1
  let initialRotate = 0
  
  // 双指缩放
  function onPinchStart(event: TouchEvent) {
    if (event.touches.length === 2) {
      initialDistance = getDistance(event.touches[0], event.touches[1])
      initialScale = scale.value
    }
  }
  
  function onPinchMove(event: TouchEvent) {
    if (event.touches.length === 2) {
      const distance = getDistance(event.touches[0], event.touches[1])
      const newScale = initialScale * (distance / initialDistance)
      scale.value = Math.max(0.5, Math.min(3, newScale))
    }
  }
  
  // 双指旋转
  function onRotateStart(event: TouchEvent) {
    if (event.touches.length === 2) {
      initialAngle = getAngle(event.touches[0], event.touches[1])
      initialRotate = rotate.value
    }
  }
  
  function onRotateMove(event: TouchEvent) {
    if (event.touches.length === 2) {
      const angle = getAngle(event.touches[0], event.touches[1])
      rotate.value = initialRotate + (angle - initialAngle)
    }
  }
  
  // 单指拖动
  let startX = 0
  let startY = 0
  
  function onPanStart(event: TouchEvent) {
    if (event.touches.length === 1) {
      startX = event.touches[0].clientX - translateX.value
      startY = event.touches[0].clientY - translateY.value
    }
  }
  
  function onPanMove(event: TouchEvent) {
    if (event.touches.length === 1) {
      translateX.value = event.touches[0].clientX - startX
      translateY.value = event.touches[0].clientY - startY
    }
  }
  
  // 双击重置
  function onDoubleTap() {
    scale.value = 1
    rotate.value = 0
    translateX.value = 0
    translateY.value = 0
  }
  
  // 工具函数
  function getDistance(t1: Touch, t2: Touch) {
    return Math.sqrt(
      Math.pow(t2.clientX - t1.clientX, 2) +
      Math.pow(t2.clientY - t1.clientY, 2)
    )
  }
  
  function getAngle(t1: Touch, t2: Touch) {
    return Math.atan2(
      t2.clientY - t1.clientY,
      t2.clientX - t1.clientX
    ) * 180 / Math.PI
  }
  
  // 绑定事件
  function bindGestures() {
    const el = elementRef.value
    if (!el) return
    
    el.addEventListener('touchstart', (e) => {
      if (e.touches.length === 2) {
        onPinchStart(e)
        onRotateStart(e)
      } else if (e.touches.length === 1) {
        onPanStart(e)
      }
    })
    
    el.addEventListener('touchmove', (e) => {
      if (e.touches.length === 2) {
        onPinchMove(e)
        onRotateMove(e)
      } else if (e.touches.length === 1) {
        onPanMove(e)
      }
    })
    
    // 双击
    let lastTap = 0
    el.addEventListener('touchend', () => {
      const now = Date.now()
      if (now - lastTap < 300) {
        onDoubleTap()
      }
      lastTap = now
    })
  }
  
  // 变换样式
  const transformStyle = computed(() => ({
    transform: `translate(${translateX.value}px, ${translateY.value}px) scale(${scale.value}) rotate(${rotate.value}deg)`
  }))
  
  return {
    scale,
    rotate,
    translateX,
    translateY,
    transformStyle,
    bindGestures,
    onDoubleTap
  }
}
```

### 4.3 手写批注

```vue
<!-- components/annotation/CanvasBoard.vue -->
<template>
  <view class="canvas-board">
    <!-- 底层内容 (仪表板/大屏) -->
    <slot />
    
    <!-- 批注画布 -->
    <canvas
      ref="canvasRef"
      class="annotation-canvas"
      :style="{ display: isAnnotating ? 'block' : 'none' }"
      @touchstart="onTouchStart"
      @touchmove="onTouchMove"
      @touchend="onTouchEnd"
    />
    
    <!-- 工具栏 -->
    <AnnotationToolbar
      v-if="isAnnotating"
      :tool="currentTool"
      :color="currentColor"
      :lineWidth="currentLineWidth"
      @tool-change="currentTool = $event"
      @color-change="currentColor = $event"
      @width-change="currentLineWidth = $event"
      @undo="undo"
      @redo="redo"
      @clear="clear"
      @save="save"
      @close="exitAnnotation"
    />
  </view>
</template>

<script setup lang="ts">
interface Point {
  x: number
  y: number
  pressure?: number
}

interface Stroke {
  points: Point[]
  color: string
  lineWidth: number
  tool: 'pen' | 'highlighter' | 'eraser'
}

const canvasRef = ref<HTMLCanvasElement>()
const isAnnotating = ref(false)
const currentTool = ref<'pen' | 'highlighter' | 'eraser'>('pen')
const currentColor = ref('#ff0000')
const currentLineWidth = ref(3)

const strokes = ref<Stroke[]>([])
const undoneStrokes = ref<Stroke[]>([])
const currentStroke = ref<Stroke | null>(null)

let ctx: CanvasRenderingContext2D | null = null

// 进入批注模式
function enterAnnotation() {
  isAnnotating.value = true
  initCanvas()
}

// 退出批注模式
function exitAnnotation() {
  isAnnotating.value = false
}

// 初始化画布
function initCanvas() {
  const canvas = canvasRef.value
  if (!canvas) return
  
  const info = uni.getSystemInfoSync()
  canvas.width = info.screenWidth * info.pixelRatio
  canvas.height = info.screenHeight * info.pixelRatio
  
  ctx = canvas.getContext('2d')
  if (ctx) {
    ctx.scale(info.pixelRatio, info.pixelRatio)
  }
}

// 触摸开始
function onTouchStart(event: TouchEvent) {
  const touch = event.touches[0]
  const point: Point = {
    x: touch.clientX,
    y: touch.clientY,
    pressure: touch.force || 0.5
  }
  
  currentStroke.value = {
    points: [point],
    color: currentTool.value === 'eraser' ? '#ffffff' : currentColor.value,
    lineWidth: currentTool.value === 'highlighter' ? 20 : currentLineWidth.value,
    tool: currentTool.value
  }
}

// 触摸移动
function onTouchMove(event: TouchEvent) {
  if (!currentStroke.value || !ctx) return
  
  const touch = event.touches[0]
  const point: Point = {
    x: touch.clientX,
    y: touch.clientY,
    pressure: touch.force || 0.5
  }
  
  currentStroke.value.points.push(point)
  
  // 实时绘制
  const points = currentStroke.value.points
  if (points.length >= 2) {
    drawLine(
      ctx,
      points[points.length - 2],
      points[points.length - 1],
      currentStroke.value
    )
  }
}

// 触摸结束
function onTouchEnd() {
  if (currentStroke.value) {
    strokes.value.push(currentStroke.value)
    undoneStrokes.value = []
    currentStroke.value = null
  }
}

// 绘制线段
function drawLine(
  ctx: CanvasRenderingContext2D,
  from: Point,
  to: Point,
  stroke: Stroke
) {
  ctx.beginPath()
  ctx.moveTo(from.x, from.y)
  ctx.lineTo(to.x, to.y)
  ctx.strokeStyle = stroke.color
  ctx.lineWidth = stroke.lineWidth
  ctx.lineCap = 'round'
  ctx.lineJoin = 'round'
  
  if (stroke.tool === 'highlighter') {
    ctx.globalAlpha = 0.3
  }
  
  ctx.stroke()
  ctx.globalAlpha = 1
}

// 重绘所有笔画
function redraw() {
  if (!ctx || !canvasRef.value) return
  
  ctx.clearRect(0, 0, canvasRef.value.width, canvasRef.value.height)
  
  for (const stroke of strokes.value) {
    for (let i = 1; i < stroke.points.length; i++) {
      drawLine(ctx, stroke.points[i - 1], stroke.points[i], stroke)
    }
  }
}

// 撤销
function undo() {
  if (strokes.value.length === 0) return
  const stroke = strokes.value.pop()!
  undoneStrokes.value.push(stroke)
  redraw()
}

// 重做
function redo() {
  if (undoneStrokes.value.length === 0) return
  const stroke = undoneStrokes.value.pop()!
  strokes.value.push(stroke)
  redraw()
}

// 清空
function clear() {
  strokes.value = []
  undoneStrokes.value = []
  if (ctx && canvasRef.value) {
    ctx.clearRect(0, 0, canvasRef.value.width, canvasRef.value.height)
  }
}

// 保存批注
function save() {
  // 导出为图片
  uni.canvasToTempFilePath({
    canvas: canvasRef.value,
    success: (res) => {
      // 上传或保存到本地
      console.log('Saved annotation:', res.tempFilePath)
    }
  })
}
</script>

<style lang="scss">
.canvas-board {
  position: relative;
  width: 100%;
  height: 100%;
  
  .annotation-canvas {
    position: absolute;
    top: 0;
    left: 0;
    width: 100%;
    height: 100%;
    z-index: 100;
    touch-action: none;
  }
}
</style>
```

### 4.4 演示模式

```vue
<!-- pages/presentation/index.vue -->
<template>
  <view class="presentation-mode" :class="{ fullscreen: isFullscreen }">
    <!-- 幻灯片容器 -->
    <swiper
      class="slides"
      :current="currentSlide"
      @change="onSlideChange"
      :circular="false"
    >
      <swiper-item v-for="(slide, index) in slides" :key="slide.id">
        <view class="slide-content">
          <DashboardRenderer
            v-if="slide.type === 'dashboard'"
            :dashboard-id="slide.id"
          />
          <ScreenRenderer
            v-if="slide.type === 'screen'"
            :screen-id="slide.id"
          />
        </view>
      </swiper-item>
    </swiper>
    
    <!-- 演示控制栏 -->
    <view class="control-bar" v-if="showControls">
      <view class="control-left">
        <text class="slide-counter">
          {{ currentSlide + 1 }} / {{ slides.length }}
        </text>
      </view>
      
      <view class="control-center">
        <button @click="prevSlide" :disabled="currentSlide === 0">
          <uni-icons type="left" size="24" />
        </button>
        
        <button @click="toggleAutoplay">
          <uni-icons :type="isAutoplay ? 'pause' : 'play'" size="24" />
        </button>
        
        <button @click="nextSlide" :disabled="currentSlide === slides.length - 1">
          <uni-icons type="right" size="24" />
        </button>
      </view>
      
      <view class="control-right">
        <button @click="toggleAnnotation">
          <uni-icons type="compose" size="20" />
          <text>批注</text>
        </button>
        <button @click="toggleFullscreen">
          <uni-icons type="fullscreen" size="20" />
        </button>
        <button @click="exitPresentation">
          <uni-icons type="close" size="20" />
        </button>
      </view>
    </view>
    
    <!-- 缩略图导航 -->
    <view class="thumbnail-nav" v-if="showThumbnails">
      <scroll-view scroll-x class="thumbnail-list">
        <view
          v-for="(slide, index) in slides"
          :key="slide.id"
          :class="['thumbnail', { active: currentSlide === index }]"
          @click="goToSlide(index)"
        >
          <text>{{ slide.title }}</text>
        </view>
      </scroll-view>
    </view>
  </view>
</template>

<script setup lang="ts">
interface Slide {
  id: string
  type: 'dashboard' | 'screen'
  title: string
  duration?: number  // 自动播放时长 (ms)
}

const slides = ref<Slide[]>([])
const currentSlide = ref(0)
const isAutoplay = ref(false)
const showControls = ref(true)
const showThumbnails = ref(false)
const isFullscreen = ref(false)

let autoplayTimer: ReturnType<typeof setInterval> | null = null

// 进入演示模式
onLoad((options) => {
  loadSlides(options?.presentationId || '')
  
  // 隐藏状态栏
  uni.hideStatusBar()
  
  // 横屏
  // #ifdef APP-PLUS
  plus.screen.lockOrientation('landscape-primary')
  // #endif
})

// 自动播放
function toggleAutoplay() {
  isAutoplay.value = !isAutoplay.value
  
  if (isAutoplay.value) {
    startAutoplay()
  } else {
    stopAutoplay()
  }
}

function startAutoplay() {
  const duration = slides.value[currentSlide.value]?.duration || 10000
  autoplayTimer = setInterval(() => {
    if (currentSlide.value < slides.value.length - 1) {
      currentSlide.value++
    } else {
      currentSlide.value = 0  // 循环
    }
  }, duration)
}

function stopAutoplay() {
  if (autoplayTimer) {
    clearInterval(autoplayTimer)
    autoplayTimer = null
  }
}

// 切换幻灯片
function prevSlide() {
  if (currentSlide.value > 0) {
    currentSlide.value--
  }
}

function nextSlide() {
  if (currentSlide.value < slides.value.length - 1) {
    currentSlide.value++
  }
}

function goToSlide(index: number) {
  currentSlide.value = index
}

// 全屏
function toggleFullscreen() {
  isFullscreen.value = !isFullscreen.value
  showControls.value = !isFullscreen.value
}

// 退出演示
function exitPresentation() {
  stopAutoplay()
  
  // 恢复竖屏
  // #ifdef APP-PLUS
  plus.screen.lockOrientation('portrait-primary')
  // #endif
  
  uni.navigateBack()
}

// 5秒无操作隐藏控制栏
let hideTimer: ReturnType<typeof setTimeout> | null = null

function resetHideTimer() {
  showControls.value = true
  if (hideTimer) clearTimeout(hideTimer)
  hideTimer = setTimeout(() => {
    showControls.value = false
  }, 5000)
}
</script>
```

### 4.5 投屏

```typescript
// composables/useScreenCast.ts
export function useScreenCast() {
  const isCasting = ref(false)
  const availableDevices = ref<CastDevice[]>([])
  const connectedDevice = ref<CastDevice | null>(null)
  
  // 扫描可用设备
  async function scanDevices(): Promise<CastDevice[]> {
    // #ifdef APP-PLUS
    // iOS: AirPlay
    if (uni.getSystemInfoSync().platform === 'ios') {
      return scanAirPlayDevices()
    }
    
    // Android: Miracast / Chromecast
    return scanMiracastDevices()
    // #endif
    
    return []
  }
  
  // 连接到设备
  async function connectToDevice(device: CastDevice): Promise<boolean> {
    // #ifdef APP-PLUS
    try {
      // 开始投屏
      const success = await startCast(device)
      if (success) {
        isCasting.value = true
        connectedDevice.value = device
      }
      return success
    } catch (error) {
      console.error('Cast failed:', error)
      return false
    }
    // #endif
    
    return false
  }
  
  // 断开投屏
  function disconnect() {
    // #ifdef APP-PLUS
    stopCast()
    // #endif
    isCasting.value = false
    connectedDevice.value = null
  }
  
  // 投屏内容
  async function castContent(content: CastContent) {
    if (!isCasting.value || !connectedDevice.value) return
    
    // #ifdef APP-PLUS
    // 发送内容到投屏设备
    await sendToCastDevice(connectedDevice.value, content)
    // #endif
  }
  
  // 投屏仪表板
  async function castDashboard(dashboardId: string) {
    await castContent({
      type: 'dashboard',
      id: dashboardId
    })
  }
  
  // 投屏大屏
  async function castScreen(screenId: string) {
    await castContent({
      type: 'screen',
      id: screenId
    })
  }
  
  return {
    isCasting,
    availableDevices,
    connectedDevice,
    scanDevices,
    connectToDevice,
    disconnect,
    castDashboard,
    castScreen
  }
}
```

### 4.6 会议协作

```typescript
// composables/useMeeting.ts
export function useMeeting(meetingId: string) {
  const ws = ref<UniApp.SocketTask>()
  const participants = ref<Participant[]>([])
  const annotations = ref<Annotation[]>([])
  const isHost = ref(false)
  
  // 加入会议
  function joinMeeting() {
    ws.value = uni.connectSocket({
      url: `${WS_URL}/meeting/${meetingId}`
    })
    
    ws.value?.onMessage((res) => {
      const data = JSON.parse(res.data)
      handleMeetingMessage(data)
    })
  }
  
  // 处理会议消息
  function handleMeetingMessage(data: MeetingMessage) {
    switch (data.type) {
      case 'participant_join':
        participants.value.push(data.participant)
        break
      case 'participant_leave':
        participants.value = participants.value.filter(
          p => p.id !== data.participantId
        )
        break
      case 'annotation':
        annotations.value.push(data.annotation)
        break
      case 'slide_change':
        // 同步幻灯片切换
        break
      case 'cursor':
        // 同步鼠标位置
        break
    }
  }
  
  // 共享批注
  function shareAnnotation(annotation: Annotation) {
    ws.value?.send({
      data: JSON.stringify({
        type: 'annotation',
        annotation
      })
    })
    annotations.value.push(annotation)
  }
  
  // 控制幻灯片 (仅主持人)
  function controlSlide(action: 'prev' | 'next' | 'goto', slideIndex?: number) {
    if (!isHost.value) return
    
    ws.value?.send({
      data: JSON.stringify({
        type: 'slide_change',
        action,
        slideIndex
      })
    })
  }
  
  // 离开会议
  function leaveMeeting() {
    ws.value?.close({})
  }
  
  return {
    joinMeeting,
    leaveMeeting,
    participants,
    annotations,
    isHost,
    shareAnnotation,
    controlSlide
  }
}
```

### 4.7 分屏模式

```vue
<!-- components/split-view/SplitView.vue -->
<template>
  <view class="split-view" :class="orientation">
    <!-- 左侧/上方面板 -->
    <view
      class="panel panel-left"
      :style="{ [sizeProp]: leftSize + '%' }"
    >
      <slot name="left" />
    </view>
    
    <!-- 分隔条 -->
    <view
      class="divider"
      @touchstart="onDividerStart"
      @touchmove="onDividerMove"
    >
      <view class="divider-handle" />
    </view>
    
    <!-- 右侧/下面板 -->
    <view
      class="panel panel-right"
      :style="{ [sizeProp]: (100 - leftSize) + '%' }"
    >
      <slot name="right" />
    </view>
  </view>
</template>

<script setup lang="ts">
interface Props {
  orientation?: 'horizontal' | 'vertical'
  defaultSplit?: number
}

const props = withDefaults(defineProps<Props>(), {
  orientation: 'horizontal',
  defaultSplit: 50
})

const leftSize = ref(props.defaultSplit)
const sizeProp = computed(() =>
  props.orientation === 'horizontal' ? 'width' : 'height'
)

let startX = 0
let startY = 0
let startSize = 0

function onDividerStart(event: TouchEvent) {
  startX = event.touches[0].clientX
  startY = event.touches[0].clientY
  startSize = leftSize.value
}

function onDividerMove(event: TouchEvent) {
  const container = event.currentTarget.parentElement
  if (!container) return
  
  const rect = container.getBoundingClientRect()
  
  if (props.orientation === 'horizontal') {
    const deltaX = event.touches[0].clientX - startX
    const deltaPercent = (deltaX / rect.width) * 100
    leftSize.value = Math.max(20, Math.min(80, startSize + deltaPercent))
  } else {
    const deltaY = event.touches[0].clientY - startY
    const deltaPercent = (deltaY / rect.height) * 100
    leftSize.value = Math.max(20, Math.min(80, startSize + deltaPercent))
  }
}
</script>

<style lang="scss">
.split-view {
  display: flex;
  width: 100%;
  height: 100%;
  
  &.horizontal {
    flex-direction: row;
    
    .divider {
      width: 8px;
      cursor: col-resize;
    }
  }
  
  &.vertical {
    flex-direction: column;
    
    .divider {
      height: 8px;
      cursor: row-resize;
    }
  }
  
  .panel {
    overflow: auto;
  }
  
  .divider {
    background: #e8e8e8;
    display: flex;
    align-items: center;
    justify-content: center;
    
    .divider-handle {
      width: 4px;
      height: 30px;
      background: #bbb;
      border-radius: 2px;
    }
  }
}
</style>
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
      "path": "pages/presentation/index",
      "style": { "navigationStyle": "custom" }
    },
    {
      "path": "pages/annotation/index",
      "style": { "navigationStyle": "custom" }
    },
    {
      "path": "pages/meeting/index",
      "style": { "navigationBarTitleText": "会议协作" }
    },
    {
      "path": "pages/analysis/index",
      "style": { "navigationBarTitleText": "自助分析" }
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
        "pagePath": "pages/screen/index",
        "text": "大屏",
        "iconPath": "static/tab/screen.png",
        "selectedIconPath": "static/tab/screen-active.png"
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
    "backgroundColor": "#f5f5f5"
  }
}
```

---

## 六、功能点清单

| 编号 | 功能 | 优先级 | 状态 |
|------|------|--------|------|
| M01-05-01 | 横竖屏 | P0 | 待开发 |
| M01-05-02 | 触控手势 | P1 | 待开发 |
| M01-05-03 | 手写批注 | P2 | 待开发 |
| M01-05-04 | 分屏模式 | P1 | 待开发 |
| M01-05-05 | 演示模式 | P1 | 待开发 |
| M01-05-06 | 投屏 | P2 | 待开发 |
| M01-05-07 | 会议协作 | P2 | 待开发 |
