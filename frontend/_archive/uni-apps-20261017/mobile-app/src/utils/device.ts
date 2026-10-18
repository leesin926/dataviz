/**
 * 设备与布局适配。三端只有 APP_LAYOUT / SCREEN_PLATFORM 两个常量不同，其余代码一致（5.3）。
 *
 * - phone 布局：页面用 rpx，随屏宽等比缩放，宽屏/横屏时限宽居中
 * - tablet 布局：px 定尺寸 + 内容最大宽度居中
 */

import type { ScreenPlatform } from '@dataviz/shared-types'

export type DeviceLayout = 'phone' | 'tablet'

/** 本应用的目标布局：mobile-app/mini-program 为 'phone'，tablet-app 为 'tablet' */
const APP_LAYOUT: DeviceLayout = 'phone'

/** 本端在大屏配置里的身份：决定后端按哪个 variant 展平、以及组件投放端过滤 */
export const SCREEN_PLATFORM: ScreenPlatform = 'mobile'

/** 绑定到页面根节点的类名，样式按此区分布局 */
export const layoutClass = `layout-${APP_LAYOUT}`

/**
 * 本端在短信验证码状态机里的身份（后端按"手机号+终端"隔离重发闸门与码值）。
 * 值必须在后端 SmsTerminalConstant.PATTERN 白名单内，三端各自不同。
 */
export const SMS_TERMINAL = 'mobile-app'
