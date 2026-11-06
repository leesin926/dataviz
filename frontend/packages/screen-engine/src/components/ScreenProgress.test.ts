import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import type { ScreenComponent } from '@dataviz/shared-types'
import ScreenProgress from './ScreenProgress.vue'

/**
 * 上一轮把「显示的数字」和「条宽」解耦：条宽封顶 100%，但达成率类指标必须如实显示 118%。
 * 这组用例钉的就是这个数字不再跟着条一起被钳掉。
 */
function makeComponent(props: Record<string, unknown>): ScreenComponent {
  return {
    id: 'progress-1',
    type: 'progress',
    name: '进度',
    x: 0,
    y: 0,
    w: 240,
    h: 48,
    zIndex: 1,
    visible: true,
    locked: false,
    props,
  }
}

function mountProgress(props: Record<string, unknown>) {
  return mount(ScreenProgress, { props: { component: makeComponent(props) } })
}

describe('ScreenProgress —— 显示数字与条宽解耦', () => {
  it('0 显示 0%，条宽也是 0%', () => {
    const wrapper = mountProgress({ percent: 0 })

    expect(wrapper.find('.sp-pct').text()).toBe('0%')
    expect(wrapper.find('.sp-fill').attributes('style')).toContain('width: 0%')
  })

  it('区间内的 55 原样显示，条宽同步 55%', () => {
    const wrapper = mountProgress({ percent: 55 })

    expect(wrapper.find('.sp-pct').text()).toBe('55%')
    expect(wrapper.find('.sp-fill').attributes('style')).toContain('width: 55%')
  })

  it('118% 显示 118% 而不是被钳成 100%，同时条宽封顶 100%（本轮解耦的核心）', () => {
    const wrapper = mountProgress({ percent: 118 })

    expect(wrapper.find('.sp-pct').text()).toBe('118%')
    expect(wrapper.find('.sp-fill').attributes('style')).toContain('width: 100%')
  })

  it('负数下钳到 0%（数字与条宽都不为负），-20 不会显示 -20%', () => {
    const wrapper = mountProgress({ percent: -20 })

    expect(wrapper.find('.sp-pct').text()).toBe('0%')
    expect(wrapper.find('.sp-fill').attributes('style')).toContain('width: 0%')
  })

  it('小数按原值显示，只跟着小数位数：66.7 → "66.7%"', () => {
    const wrapper = mountProgress({ percent: 66.7 })

    expect(wrapper.find('.sp-pct').text()).toBe('66.7%')
    expect(wrapper.find('.sp-fill').attributes('style')).toContain('width: 66.7%')
  })

  it('数字字符串走 Number 转换："60" 显示 60%', () => {
    const wrapper = mountProgress({ percent: '60' })

    expect(wrapper.find('.sp-pct').text()).toBe('60%')
  })

  it('非数字字符串（"abc"）回落 0%，不出现 NaN%', () => {
    const wrapper = mountProgress({ percent: 'abc' })

    expect(wrapper.find('.sp-pct').text()).toBe('0%')
    expect(wrapper.find('.sp-fill').attributes('style')).toContain('width: 0%')
  })

  it('null 与 undefined 都回落 0%（null 被 Number(null) 转成 0，undefined 走兜底）', () => {
    expect(mountProgress({ percent: null }).find('.sp-pct').text()).toBe('0%')
    expect(mountProgress({ percent: undefined }).find('.sp-pct').text()).toBe('0%')
  })

  it('percent 整个键缺失时同样是 0%，不出现 "NaN%"', () => {
    const wrapper = mountProgress({ label: '达成率' })

    expect(wrapper.find('.sp-pct').text()).toBe('0%')
    expect(wrapper.find('.sp-label').text()).toBe('达成率')
  })

  it('showValue=false 只隐藏数字，条宽不受影响', () => {
    const wrapper = mountProgress({ percent: 118, showValue: false })

    expect(wrapper.find('.sp-pct').exists()).toBe(false)
    expect(wrapper.find('.sp-fill').attributes('style')).toContain('width: 100%')
  })

  it('label 缺省为 "进度"，轨道高度缺省 12px', () => {
    const wrapper = mountProgress({ percent: 30 })

    expect(wrapper.find('.sp-label').text()).toBe('进度')
    expect(wrapper.find('.sp-track').attributes('style')).toContain('height: 12px')
  })
})
