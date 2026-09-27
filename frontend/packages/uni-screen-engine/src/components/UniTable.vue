<template>
  <scroll-view class="uni-table" scroll-y :style="{ color, fontSize: size + 'px' }">
    <view class="uni-table__head">
      <text v-for="(c, i) in table?.columns || []" :key="i" class="uni-table__cell uni-table__th">{{ c }}</text>
    </view>
    <view v-for="(row, r) in table?.rows || []" :key="r" class="uni-table__row">
      <text v-for="(cell, c) in row" :key="c" class="uni-table__cell">{{ cellText(cell) }}</text>
    </view>
    <view v-if="!table" class="uni-table__empty">
      <text class="uni-table__empty-text">{{ error || '暂无数据' }}</text>
    </view>
  </scroll-view>
</template>

<script setup lang="ts">
  /** 数据表格（4.2）：整表纵向滚动，列等宽 */
  import { computed } from 'vue'
  import { CANVAS_INK } from '@dataviz/shared-types'
  import { useComponentData, type DataEnv } from '../core/dataSource'
  import { cellText, toTabular } from '../core/tabular'
  import { scaledFontSize } from '../core/scale'
  import { numOf, strOf, DEFAULT_ENV, type UniRendererProps } from './props'

  const props = defineProps<UniRendererProps>()

  const p = computed(() => props.component.props || {})
  const color = computed(() => strOf(p.value.color, CANVAS_INK.normal))
  const size = computed(() => scaledFontSize(numOf(p.value.fontSize, 14), (props.scale ?? 1)))

  const { data, error } = useComponentData(
    computed(() => props.component),
    (props.env || DEFAULT_ENV),
  )
  const table = computed(() => toTabular(props.component, data.value))
</script>

<style scoped>
  .uni-table {
    width: 100%;
    height: 100%;
  }
  .uni-table__head,
  .uni-table__row {
    display: flex;
    flex-direction: row;
    align-items: center;
    border-bottom: 1px solid rgba(0, 0, 0, 0.06);
  }
  .uni-table__head {
    background-color: rgba(0, 0, 0, 0.03);
  }
  .uni-table__cell {
    flex: 1;
    padding: 4px 6px;
    overflow: hidden;
    white-space: nowrap;
    text-overflow: ellipsis;
  }
  .uni-table__th {
    color: #303133;
    font-weight: bold;
  }
  .uni-table__empty {
    padding: 12px;
    text-align: center;
  }
  .uni-table__empty-text {
    font-size: 12px;
    color: #909399;
  }
</style>
