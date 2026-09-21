<template>
  <div class="login-view">
    <div class="login-card">
      <div class="login-header">
        <h1>DataViz 桌面端</h1>
        <p>{{ t('common.appSubtitle') }}</p>
      
<el-dropdown trigger="click" @command="onLocale">
          <span class="locale-switch">{{ localeLabel }} ▾</span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="zh-CN">简体中文</el-dropdown-item>
              <el-dropdown-item command="en-US">English</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown></div>
      <el-tabs v-model="activeTab" stretch>
        <el-tab-pane :label="t('login.tabPassword')" name="password">
          <el-form ref="formRef" :model="form" :rules="rules" @submit.prevent="handleLogin">
            <el-form-item prop="username">
              <el-input v-model="form.username" :placeholder="t('login.username')" prefix-icon="User" size="large" />
            </el-form-item>
            <el-form-item prop="password">
              <el-input
                v-model="form.password"
                type="password"
                :placeholder="t('login.password')"
                prefix-icon="Lock"
                size="large"
                show-password
              />
            </el-form-item>
            <el-form-item prop="captcha">
              <div class="captcha-row">
                <el-input v-model="form.captcha" :placeholder="t('login.captcha')" prefix-icon="Key" size="large" />
                <img
                  v-if="captchaImage"
                  :src="captchaImage"
                  class="captcha-img"
                  @click="refreshCaptcha"
                  alt="captcha"
                />
              </div>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" size="large" :loading="loading" class="login-btn" @click="handleLogin">
                登录
              </el-button>
            </el-form-item>
          </el-form>
        </el-tab-pane>
        <el-tab-pane :label="t('login.tabSms')" name="sms">
          <el-form ref="smsFormRef" :model="smsForm" :rules="smsRules" @submit.prevent="handleSmsLogin">
            <el-form-item prop="phone">
              <el-input v-model="smsForm.phone" :placeholder="t('login.phone')" prefix-icon="Iphone" size="large" maxlength="11" />
            </el-form-item>
            <el-form-item prop="code">
              <div class="captcha-row">
                <el-input v-model="smsForm.code" :placeholder="t('login.smsCode')" prefix-icon="Message" size="large" />
                <el-button size="large" :disabled="countdown > 0" class="sms-btn" @click="handleSendCode">
                  {{ countdown > 0 ? t('login.retryAfter', { sec: countdown }) : t('login.getCode') }}
                </el-button>
              </div>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" size="large" :loading="smsLoading" class="login-btn" @click="handleSmsLogin">
                登录
              </el-button>
            </el-form-item>
          </el-form>
          <div class="sms-tip">{{ t('login.smsMockTip') }}</div>
        </el-tab-pane>
      </el-tabs>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import type { LoginResult } from '@dataviz/shared-types'
import { login, getCaptcha, sendSmsCode, smsLogin, setToken } from '@dataviz/api-client'
import { setLocal } from '@dataviz/shared-utils'
import { t, switchLocale, getStoredLocale, type AppLocale } from '@/locales'

const router = useRouter()
const route = useRoute()

const locale = ref<AppLocale>(getStoredLocale())
const localeLabel = computed(() => (locale.value === 'en-US' ? 'English' : '简体中文'))
function onLocale(cmd: string | number | object) {
  const l = cmd as AppLocale
  locale.value = l
  switchLocale(l)
}

const activeTab = ref<'password' | 'sms'>('password')
const formRef = ref<FormInstance>()
const loading = ref(false)
const captchaImage = ref('')
const captchaKey = ref('')

const form = reactive({ username: '', password: '', captcha: '' })

const rules: FormRules = {
  username: [{ required: true, message: t('login.errUsernameRequired'), trigger: 'blur' }],
  password: [{ required: true, message: t('login.errPasswordRequired'), trigger: 'blur' }],
}

const smsFormRef = ref<FormInstance>()
const smsLoading = ref(false)
const countdown = ref(0)
let countdownTimer: ReturnType<typeof setInterval> | null = null

const smsForm = reactive({ phone: '', code: '' })

const smsRules: FormRules = {
  phone: [
    { required: true, message: t('login.errPhoneRequired'), trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: t('login.errPhoneFormat'), trigger: 'blur' },
  ],
  code: [{ required: true, message: t('login.errCodeRequired'), trigger: 'blur' }],
}

function saveSession(result: LoginResult) {
  setToken(result.accessToken, result.refreshToken)
  setLocal('permissions', result.permissions)
  setLocal('user', {
    id: result.userId,
    username: result.username,
    nickname: result.username,
    roles: result.roles,
    permissions: result.permissions,
  })
}

function goHome() {
  router.push((route.query.redirect as string) || '/')
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
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    loading.value = true
    try {
      const result = await login({
        username: form.username,
        password: form.password,
        captchaCode: form.captcha,
        captchaKey: captchaKey.value,
      })
      saveSession(result)
      ElMessage.success(t('login.loginSuccess'))
      goHome()
    } catch (e) {
      ElMessage.error((e as Error).message || t('login.loginFailed'))
      refreshCaptcha()
    } finally {
      loading.value = false
    }
  })
}

async function handleSendCode() {
  try {
    await smsFormRef.value?.validateField('phone')
  } catch {
    return
  }
  try {
    await sendSmsCode(smsForm.phone)
    ElMessage.success(t('login.codeSent', { phone: smsForm.phone }))
    startCountdown(60)
  } catch (e) {
    ElMessage.error((e as Error).message || t('login.sendFailed'))
  }
}

function startCountdown(seconds: number) {
  countdown.value = seconds
  if (countdownTimer) clearInterval(countdownTimer)
  countdownTimer = setInterval(() => {
    countdown.value -= 1
    if (countdown.value <= 0 && countdownTimer) {
      clearInterval(countdownTimer)
      countdownTimer = null
    }
  }, 1000)
}

async function handleSmsLogin() {
  if (!smsFormRef.value) return
  await smsFormRef.value.validate(async (valid) => {
    if (!valid) return
    smsLoading.value = true
    try {
      const result = await smsLogin(smsForm.phone, smsForm.code)
      saveSession(result)
      ElMessage.success(t('login.loginSuccess'))
      goHome()
    } catch (e) {
      ElMessage.error((e as Error).message || t('login.loginFailed'))
    } finally {
      smsLoading.value = false
    }
  })
}

onMounted(() => {
  refreshCaptcha()
})

onUnmounted(() => {
  if (countdownTimer) clearInterval(countdownTimer)
})
</script>

<style scoped lang="scss">
.login-view {
  width: 100%;
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}
.login-card {
  width: 420px;
  padding: 40px;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 20px 40px rgba(0, 0, 0, 0.2);
}
.login-header {
    .locale-switch {
      display: inline-block;
      margin-top: 6px;
      font-size: 12px;
      color: #909399;
      cursor: pointer;
    }
  text-align: center;
  margin-bottom: 24px;
  h1 {
    font-size: 24px;
    color: #303133;
    margin: 0 0 8px;
  }
  p {
    color: #909399;
    font-size: 13px;
    margin: 0;
  }
}
.login-btn {
  width: 100%;
}
.captcha-row {
  display: flex;
  gap: 12px;
  width: 100%;
  .captcha-img {
    height: 40px;
    border-radius: 4px;
    cursor: pointer;
    border: 1px solid #dcdfe6;
  }
  .sms-btn {
    min-width: 110px;
  }
}
.sms-tip {
  text-align: center;
  color: #909399;
  font-size: 12px;
}
</style>
