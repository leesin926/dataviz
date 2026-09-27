<template>
  <view class="uni-border" :class="`bv-${variant}`" :style="borderStyle">
    <template v-if="variant === 'corner'">
      <view v-for="c in corners" :key="c" class="uni-border__corner" :class="c" :style="cornerStyle" />
    </template>
  </view>
</template>

<script setup lang="ts">
  /** 边框装饰（4.2）：与 PC 端 ScreenBorder 同一套 props，glow 的阴影在小程序端退化为描边 */
  import { computed } from 'vue'
  import { CANVAS_INK } from '@dataviz/shared-types'
  import { numOf, strOf, type UniRendererProps } from './props'

  const props = defineProps<UniRendererProps>()

  const corners = ['bv-tl', 'bv-tr', 'bv-bl', 'bv-br']
  const p = computed(() => props.component.props || {})
  const variant = computed(() => strOf(p.value.variant, 'glow'))
  const color = computed(() => strOf(p.value.borderColor, CANVAS_INK.accent))
  const width = computed(() => Math.max(1, Math.round(numOf(p.value.borderWidth, 2))))
  const radius = computed(() => Math.max(0, Math.round(numOf(p.value.radius ?? p.value.borderRadius, 8))))

  const borderStyle = computed<Record<string, string>>(() => {
    const base: Record<string, string> = {
      width: '100%',
      height: '100%',
      borderRadius: `${radius.value}px`,
    }
    if (variant.value === 'line') base.border = `${width.value}px solid ${color.value}`
    else if (variant.value === 'dash') base.border = `${width.value}px dashed ${color.value}`
    else if (variant.value === 'glow') {
      base.border = `${width.value}px solid ${color.value}66`
      base.boxShadow = `inset 0 0 12px ${color.value}33`
    } else base.border = `1px solid ${color.value}22`
    return base
  })

  const cornerLen = computed(() => 14)
  const cornerStyle = computed<Record<string, string>>(() => ({
    width: `${cornerLen.value}px`,
    height: `${cornerLen.value}px`,
    borderColor: color.value,
    borderWidth: `${width.value}px`,
  }))
</script>

<style scoped>
  .uni-border {
    position: relative;
    box-sizing: border-box;
  }
  .uni-border__corner {
    position: absolute;
    box-sizing: border-box;
  }
  .bv-tl {
    top: -1px;
    left: -1px;
    border-style: solid none none solid;
  }
  .bv-tr {
    top: -1px;
    right: -1px;
    border-style: solid solid none none;
  }
  .bv-bl {
    bottom: -1px;
    left: -1px;
    border-style: none none solid solid;
  }
  .bv-br {
    bottom: -1px;
    right: -1px;
    border-style: none solid solid none;
  }
</style>
