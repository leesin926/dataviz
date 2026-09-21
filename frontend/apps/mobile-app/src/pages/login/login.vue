<template>
  <view class="login-page" :class="layoutClass">
    <view class="login-header">
      <text class="app-title">DataViz</text>
      <text class="app-subtitle">数据可视化平台</text>
    </view>

    <view class="login-card">
      <view class="tab-row">
        <text :class="['tab', activeTab === 'password' ? 'tab-active' : '']" @click="activeTab = 'password'">
          账号登录
        </text>
        <text :class="['tab', activeTab === 'sms' ? 'tab-active' : '']" @click="activeTab = 'sms'">
          短信登录
        </text>
      </view>

      <view v-if="activeTab === 'password'" class="form">
        <input v-model="form.username" class="input" placeholder="用户名" />
        <input v-model="form.password" class="input" password placeholder="密码" />
        <view class="captcha-row">
          <input v-model="form.captcha" class="input captcha-input" placeholder="验证码" />
          <image v-if="captchaImage" class="captcha-img" :src="captchaImage" @click="refreshCaptcha" mode="aspectFit" />
        </view>
        <button class="login-btn" :loading="loading" @click="handleLogin">登录</button>
      </view>

      <view v-else class="form">
        <input v-model="smsForm.phone" class="input" type="number" maxlength="11" placeholder="手机号" />
        <view class="captcha-row">
          <input v-model="smsForm.code" class="input captcha-input" type="number" maxlength="8" placeholder="短信验证码" />
          <button class="sms-btn" :disabled="countdown > 0" size="mini" @click="handleSendCode">
            {{ countdown > 0 ? countdown + 's 后重发' : '获取验证码' }}
          </button>
        </view>
        <button class="login-btn" :loading="smsLoading" @click="handleSmsLogin">登录</button>
        <text class="sms-tip">短信登录为前端模拟功能，任意验证码均可登录</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onUnmounted } from 'vue'
import { layoutClass } from '../../utils/device'
import {
  getCaptcha,
  passwordLogin,
  sendSmsCode,
  smsLogin,
} from '../../utils/auth'

const activeTab = ref<'password' | 'sms'>('password')
const loading = ref(false)
const captchaImage = ref('')
const captchaKey = ref('')

const form = reactive({ username: '', password: '', captcha: '' })

const smsLoading = ref(false)
const countdown = ref(0)
let countdownTimer: ReturnType<typeof setInterval> | null = null
const smsForm = reactive({ phone: '', code: '' })

function goHome() {
  uni.reLaunch({ url: '/pages/index/index' })
}

async function refreshCaptcha() {
  try {
    const res = await getCaptcha()
    captchaImage.value = res.captchaImage
    captchaKey.value = res.captchaKey
  } catch (e) {
    console.error('获取验证码失败', e)
  }
}

async function handleLogin() {
  if (!form.username || !form.password) {
    uni.showToast({ title: '请输入用户名和密码', icon: 'none' })
    return
  }
  loading.value = true
  try {
    await passwordLogin(form.username, form.password, form.captcha, captchaKey.value)
    uni.showToast({ title: '登录成功', icon: 'success' })
    goHome()
  } catch (e) {
    uni.showToast({ title: (e as Error).message || '登录失败', icon: 'none' })
    refreshCaptcha()
  } finally {
    loading.value = false
  }
}

async function handleSendCode() {
  try {
    await sendSmsCode(smsForm.phone)
    uni.showToast({ title: `验证码已发送至 ${smsForm.phone}`, icon: 'none' })
    countdown.value = 60
    if (countdownTimer) clearInterval(countdownTimer)
    countdownTimer = setInterval(() => {
      countdown.value -= 1
      if (countdown.value <= 0 && countdownTimer) {
        clearInterval(countdownTimer)
        countdownTimer = null
      }
    }, 1000)
  } catch (e) {
    uni.showToast({ title: (e as Error).message || '发送失败', icon: 'none' })
  }
}

async function handleSmsLogin() {
  smsLoading.value = true
  try {
    await smsLogin(smsForm.phone, smsForm.code)
    uni.showToast({ title: '登录成功', icon: 'success' })
    goHome()
  } catch (e) {
    uni.showToast({ title: (e as Error).message || '登录失败', icon: 'none' })
  } finally {
    smsLoading.value = false
  }
}

onMounted(() => {
  refreshCaptcha()
})

onUnmounted(() => {
  if (countdownTimer) clearInterval(countdownTimer)
})
</script>

<style lang="scss" scoped>
.login-page {
  min-height: 100vh;
  padding: 80rpx 48rpx 48rpx;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  box-sizing: border-box;
}
.login-header {
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-bottom: 64rpx;
  .app-title {
    font-size: 56rpx;
    font-weight: bold;
    color: #fff;
  }
  .app-subtitle {
    margin-top: 12rpx;
    font-size: 26rpx;
    color: rgba(255, 255, 255, 0.85);
  }
}
.login-card {
  background: #fff;
  border-radius: 24rpx;
  padding: 40rpx 32rpx;
  box-shadow: 0 16rpx 48rpx rgba(0, 0, 0, 0.15);
}
.tab-row {
  display: flex;
  margin-bottom: 40rpx;
  .tab {
    flex: 1;
    text-align: center;
    padding: 16rpx 0;
    font-size: 30rpx;
    color: #909399;
    border-bottom: 4rpx solid transparent;
  }
  .tab-active {
    color: #667eea;
    font-weight: bold;
    border-bottom-color: #667eea;
  }
}
.form {
  display: flex;
  flex-direction: column;
}
.input {
  height: 88rpx;
  margin-bottom: 28rpx;
  padding: 0 24rpx;
  border: 2rpx solid #dcdfe6;
  border-radius: 12rpx;
  font-size: 28rpx;
}
.captcha-row {
  display: flex;
  align-items: center;
  gap: 20rpx;
  .captcha-input {
    flex: 1;
  }
  .captcha-img {
    width: 200rpx;
    height: 88rpx;
    border: 2rpx solid #dcdfe6;
    border-radius: 12rpx;
  }
  .sms-btn {
    min-width: 180rpx;
    margin-bottom: 28rpx;
  }
}
.login-btn {
  margin-top: 12rpx;
  background: #667eea;
  color: #fff;
  border-radius: 12rpx;
  font-size: 32rpx;
}
.sms-tip {
  margin-top: 20rpx;
  text-align: center;
  font-size: 22rpx;
  color: #909399;
}

/* ---------- 手机布局：横屏/宽屏限宽居中 ---------- */
@media screen and (orientation: landscape) and (max-height: 520px) {
  .layout-phone .login-card {
    max-width: 60%;
    margin: 0 auto;
  }
}
@media screen and (min-width: 768px) {
  .layout-phone .login-card {
    max-width: 420px;
    margin: 0 auto;
  }
}

/* ---------- 平板布局：px 定尺寸 + 居中限宽 ---------- */
.layout-tablet.login-page {
  padding: 60px 24px 40px;
}
.layout-tablet .login-header {
  margin-bottom: 40px;
  .app-title {
    font-size: 32px;
  }
  .app-subtitle {
    margin-top: 8px;
    font-size: 14px;
  }
}
.layout-tablet .login-card {
  max-width: 440px;
  margin: 0 auto;
  border-radius: 12px;
  padding: 28px 24px;
}
.layout-tablet .tab {
  font-size: 15px;
  padding: 10px 0;
  border-bottom-width: 2px;
}
.layout-tablet .tab-row {
  margin-bottom: 24px;
}
.layout-tablet .input {
  height: 44px;
  margin-bottom: 16px;
  padding: 0 14px;
  font-size: 14px;
  border-width: 1px;
  border-radius: 8px;
}
.layout-tablet .captcha-row {
  gap: 12px;
  .captcha-img {
    width: 120px;
    height: 44px;
  }
  .sms-btn {
    min-width: 100px;
    margin-bottom: 16px;
    font-size: 13px;
  }
}
.layout-tablet .login-btn {
  margin-top: 6px;
  height: 44px;
  line-height: 44px;
  font-size: 16px;
  border-radius: 8px;
}
.layout-tablet .sms-tip {
  margin-top: 12px;
  font-size: 12px;
}
</style>
