<template>
  <view class="uni-board" :style="{ height: height + 'px' }">
    <view class="uni-board__head" v-if="table" :style="{ color: headColor, fontSize: size + 'px', height: rowHeight + 'px' }">
      <text v-for="(c, i) in table.columns" :key="i" class="uni-board__cell">{{ c }}</text>
    </view>
    <view class="uni-board__viewport" :style="{ height: viewportHeight + 'px' }">
      <view class="uni-board__list" :style="listStyle">
        <view
          v-for="(row, r) in looped"
          :key="r"
          class="uni-board__row"
          :style="{ color, fontSize: size + 'px', height: rowHeight + 'px' }"
        >
          <text v-for="(cell, c) in row" :key="c" class="uni-board__cell">{{ cellText(cell) }}</text>
        </view>
      </view>
    </view>
    <view v-if="!table" class="uni-board__empty">
      <text class="uni-board__empty-text">{{ error || '暂无数据' }}</text>
    </view>
  </view>
</template>

<script setup lang="ts">
  /**
   * 轮播列表（4.2）：用 setInterval 位移而不是 CSS @keyframes ——
   * 小程序端对 keyframes 的动态时长支持不一致，定时器口径三端一致且可随 interval 配置改变。
   */
  import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
  import { CANVAS_INK } from '@dataviz/shared-types'
  import { useComponentData, type DataEnv } from '../core/dataSource'
  import { cellText, toTabular } from '../core/tabular'
  import { scaledFontSize } from '../core/scale'
  import { numOf, strOf, DEFAULT_ENV, type UniRendererProps } from './props'

  const props = defineProps<UniRendererProps>()

  const p = computed(() => props.component.props || {})
  const color = computed(() => strOf(p.value.color, CANVAS_INK.normal))
  const headColor = computed(() => strOf(p.value.headColor, CANVAS_INK.accent))
  const size = computed(() => scaledFontSize(numOf(p.value.fontSize, 14), (props.scale ?? 1)))
  const rowHeight = computed(() => Math.max(18, Math.round(size.value * 2)))
  const viewportHeight = computed(() => Math.max(rowHeight.value, (props.height ?? 0) - (table.value ? rowHeight.value : 0)))
  const visibleCount = computed(() => Math.max(1, Math.floor(viewportHeight.value / rowHeight.value)))

  const { data, error } = useComponentData(
    computed(() => props.component),
    (props.env || DEFAULT_ENV),
  )
  const table = computed(() => toTabular(props.component, data.value))
  const rows = computed(() => table.value?.rows || [])

  /** 偏移一格需要的尾巴行数：把首屏内容再接一份到末尾，归零时视觉上无跳变 */
  const looped = computed(() => {
    const list = rows.value
    if (list.length <= visibleCount.value) return list
    return list.concat(list.slice(0, visibleCount.value))
  })

  const offset = ref(0)
  let timer: ReturnType<typeof setInterval> | null = null

  const listStyle = computed(() => ({
    transform: `translateY(-${offset.value * rowHeight.value}px)`,
    transition: 'transform 0.4s linear',
  }))

  function step(): void {
    const total = rows.value.length
    if (total <= visibleCount.value) return
    offset.value = offset.value >= total ? 0 : offset.value + 1
  }

  function restart(): void {
    if (timer) clearInterval(timer)
    timer = null
    const seconds = Math.max(1, numOf(p.value.interval, 3))
    timer = setInterval(step, seconds * 1000)
  }

  onMounted(restart)
  watch(() => [props.component.props, rows.value.length], restart)
  onBeforeUnmount(() => {
    if (timer) clearInterval(timer)
  })
</script>

<style scoped>
  .uni-board {
    width: 100%;
    overflow: hidden;
  }
  .uni-board__head,
  .uni-board__row {
    display: flex;
    flex-direction: row;
    align-items: center;
  }
  .uni-board__head {
    border-bottom: 1px solid rgba(0, 0, 0, 0.06);
    font-weight: bold;
  }
  .uni-board__viewport {
    overflow: hidden;
  }
  .uni-board__cell {
    flex: 1;
    padding: 0 6px;
    overflow: hidden;
    white-space: nowrap;
    text-overflow: ellipsis;
  }
  .uni-board__empty {
    padding: 12px;
    text-align: center;
  }
  .uni-board__empty-text {
    font-size: 12px;
    color: #909399;
  }
</style>
