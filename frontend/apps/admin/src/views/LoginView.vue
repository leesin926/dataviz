<template>
  <div class="login-view">
    <div class="login-bg" aria-hidden="true">
      <span class="glow glow-a"></span>
      <span class="glow glow-b"></span>
      <span class="grid-lines"></span>
    </div>

    <div class="login-panels">
      <section class="brand-panel">
        <div class="brand-mark">
          <span class="mark">DA</span>
          <span class="name">DataViz</span>
        </div>
        <h1>{{ t('common.appName') }}</h1>
        <p class="slogan">{{ t('login.slogan') }}</p>
        <ul class="features">
          <li><el-icon><Grid /></el-icon>{{ t('login.feature1') }}</li>
          <li><el-icon><Key /></el-icon>{{ t('login.feature2') }}</li>
          <li><el-icon><Tickets /></el-icon>{{ t('login.feature3') }}</li>
        </ul>
      </section>

      <section class="form-panel">
        <header class="form-head">
          <div>
            <h2>{{ t('common.appSubtitle') }}</h2>
            <p>{{ t('login.username') }} · {{ t('login.tabSms') }}</p>
          </div>
          <el-dropdown trigger="click" @command="onLocale">
            <span class="locale-switch">{{ localeLabel }} ▾</span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="zh-CN">简体中文</el-dropdown-item>
                <el-dropdown-item command="en-US">English</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </header>

        <el-tabs v-model="activeTab" stretch>
          <el-tab-pane :label="t('login.tabPassword')" name="password">
            <el-form ref="formRef" :model="form" :rules="rules" size="large" @submit.prevent="handleLogin">
              <el-form-item prop="username">
                <el-input v-model="form.username" :placeholder="t('login.username')" prefix-icon="User" />
              </el-form-item>
              <el-form-item prop="password">
                <el-input
                  v-model="form.password"
                  type="password"
                  :placeholder="t('login.password')"
                  prefix-icon="Lock"
                  show-password
                />
              </el-form-item>
              <el-form-item prop="captcha">
                <div class="captcha-row">
                  <el-input v-model="form.captcha" :placeholder="t('login.captcha')" prefix-icon="ChatLineSquare" />
                  <img
                    v-if="captchaImage"
                    :src="captchaImage"
                    class="captcha-img"
                    alt="captcha"
                    @click="refreshCaptcha"
                  />
                </div>
              </el-form-item>
              <el-button type="primary" class="submit" :loading="loading" @click="handleLogin">
                {{ t('login.login') }}
              </el-button>
            </el-form>
          </el-tab-pane>

          <el-tab-pane :label="t('login.tabSms')" name="sms">
            <el-form ref="smsFormRef" :model="smsForm" :rules="smsRules" size="large" @submit.prevent="handleSmsLogin">
              <el-form-item prop="phone">
                <el-input v-model="smsForm.phone" :placeholder="t('login.phone')" prefix-icon="Iphone" maxlength="11" />
              </el-form-item>
              <el-form-item prop="code">
                <div class="captcha-row">
                  <el-input v-model="smsForm.code" :placeholder="t('login.smsCode')" prefix-icon="Message" />
                  <el-button class="sms-btn" :disabled="countdown > 0" @click="handleSendCode">
                    {{ countdown > 0 ? t('login.retryAfter', { sec: countdown }) : t('login.getCode') }}
                  </el-button>
                </div>
              </el-form-item>
              <el-button type="primary" class="submit" :loading="smsLoading" @click="handleSmsLogin">
                {{ t('login.login') }}
              </el-button>
            </el-form>
            <p class="sms-tip">{{ t('login.smsTip') }}</p>
          </el-tab-pane>
        </el-tabs>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Grid, Key, Tickets } from '@element-plus/icons-vue'
import type { LoginResult } from '@dataviz/shared-types'
import { getCaptcha, login, sendSmsCode, setToken, smsLogin } from '@dataviz/api-client'
import { setLocal } from '@dataviz/shared-utils'
import { switchLocale, getStoredLocale, type AppLocale } from '@/locales'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
const router = useRouter()
const route = useRoute()

const locale = ref<AppLocale>(getStoredLocale())
const localeLabel = computed(() => (locale.value === 'en-US' ? 'English' : '简体中文'))
function onLocale(cmd: string | number | object) {
  locale.value = cmd as AppLocale
  switchLocale(locale.value)
}

const activeTab = ref<'password' | 'sms'>('password')
const formRef = ref<FormInstance>()
const loading = ref(false)
const captchaImage = ref('')
const captchaKey = ref('')

const form = reactive({ username: '', password: '', captcha: '' })

const rules = computed<FormRules>(() => ({
  username: [{ required: true, message: t('login.errUsernameRequired'), trigger: 'blur' }],
  password: [{ required: true, message: t('login.errPasswordRequired'), trigger: 'blur' }],
}))

const smsFormRef = ref<FormInstance>()
const smsLoading = ref(false)
const countdown = ref(0)
let countdownTimer: ReturnType<typeof setInterval> | null = null

const smsForm = reactive({ phone: '', code: '' })

const smsRules = computed<FormRules>(() => ({
  phone: [
    { required: true, message: t('login.errPhoneRequired'), trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: t('login.errPhoneFormat'), trigger: 'blur' },
  ],
  code: [{ required: true, message: t('login.errCodeRequired'), trigger: 'blur' }],
}))

function saveSession(result: LoginResult) {
  setToken(result.accessToken, result.refreshToken)
  const permissions = result.roles.includes('super_admin') ? ['*'] : result.permissions
  setLocal('permissions', permissions)
  setLocal('user', {
    id: result.userId,
    username: result.username,
    nickname: result.username,
    roles: result.roles,
    permissions,
  })
}

function goHome() {
  router.push((route.query.redirect as string) || '/dashboard')
}

async function refreshCaptcha() {
  try {
    const res = await getCaptcha()
    captchaImage.value = res.captchaImage
    captchaKey.value = res.captchaKey
  } catch (e) {
    console.error('captcha failed', e)
  }
}

async function handleLogin() {
  const valid = await formRef.value?.validate().catch(() => false)
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
}

async function handleSendCode() {
  const valid = await smsFormRef.value?.validateField('phone').catch(() => false)
  if (!valid) return
  try {
    // 终端标识：后端按"手机号+终端"隔离重发闸门与码值，值须在 SmsTerminalConstant 白名单内
    const res = await sendSmsCode(smsForm.phone, 'admin')
    ElMessage.success(t('login.codeSent', { phone: smsForm.phone }))
    // 倒计时秒数听服务端的，别在前端再写死一个 60
    startCountdown(res.resendAfterSeconds)
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
  const valid = await smsFormRef.value?.validate().catch(() => false)
  if (!valid) return
  smsLoading.value = true
  try {
    const result = await smsLogin(smsForm.phone, smsForm.code, 'admin')
    saveSession(result)
    ElMessage.success(t('login.loginSuccess'))
    goHome()
  } catch (e) {
    ElMessage.error((e as Error).message || t('login.loginFailed'))
  } finally {
    smsLoading.value = false
  }
}

onMounted(refreshCaptcha)

onUnmounted(() => {
  if (countdownTimer) clearInterval(countdownTimer)
})
</script>

<style scoped lang="scss">
.login-view {
  position: relative;
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--dv-space-xl);
  overflow: hidden;
  background: radial-gradient(1200px 600px at 12% 10%, #16233f 0%, #0a1120 55%, #060a14 100%);
}

.login-bg {
  position: absolute;
  inset: 0;
  pointer-events: none;

  .glow {
    position: absolute;
    border-radius: 50%;
    filter: blur(70px);
    opacity: 0.55;
  }
  .glow-a {
    width: 420px;
    height: 420px;
    left: -80px;
    top: -60px;
    background: rgba(47, 124, 255, 0.55);
  }
  .glow-b {
    width: 360px;
    height: 360px;
    right: -60px;
    bottom: -80px;
    background: rgba(34, 211, 238, 0.35);
  }
  .grid-lines {
    position: absolute;
    inset: 0;
    background-image: linear-gradient(rgba(120, 170, 255, 0.07) 1px, transparent 1px),
      linear-gradient(90deg, rgba(120, 170, 255, 0.07) 1px, transparent 1px);
    background-size: 40px 40px;
    mask-image: radial-gradient(ellipse at 50% 40%, #000 35%, transparent 75%);
  }
}

.login-panels {
  position: relative;
  display: flex;
  align-items: stretch;
  gap: 0;
  width: min(980px, 100%);
  border-radius: var(--dv-radius-xl, 20px);
  overflow: hidden;
  border: 1px solid rgba(140, 180, 255, 0.18);
  box-shadow: 0 30px 80px rgba(3, 8, 20, 0.55);
  backdrop-filter: blur(6px);
}

.brand-panel {
  flex: 1 1 46%;
  padding: 44px 38px;
  color: #eaf1ff;
  background: linear-gradient(160deg, rgba(24, 42, 78, 0.92) 0%, rgba(11, 18, 34, 0.92) 100%);

  .brand-mark {
    display: flex;
    align-items: center;
    gap: 10px;
    .mark {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      width: 34px;
      height: 34px;
      border-radius: 10px;
      font-size: 13px;
      font-weight: 700;
      letter-spacing: 0.5px;
      color: #04101f;
      background: var(--dv-grad-brand);
      box-shadow: 0 0 18px rgba(47, 124, 255, 0.55);
    }
    .name {
      font-size: 15px;
      font-weight: 600;
      letter-spacing: 3px;
      color: rgba(226, 236, 255, 0.75);
    }
  }

  h1 {
    margin: 30px 0 10px;
    font-size: 28px;
    font-weight: 660;
    line-height: 1.35;
  }

  .slogan {
    margin: 0;
    font-size: var(--dv-font-sm);
    line-height: 1.7;
    color: rgba(206, 220, 245, 0.72);
  }

  .features {
    margin: 34px 0 0;
    padding: 0;
    list-style: none;

    li {
      display: flex;
      align-items: center;
      gap: 10px;
      padding: 10px 0;
      font-size: 13px;
      color: rgba(206, 220, 245, 0.85);
      border-bottom: 1px dashed rgba(140, 180, 255, 0.16);

      &:last-child {
        border-bottom: none;
      }
      .el-icon {
        color: #38bdf8;
      }
    }
  }
}

.form-panel {
  flex: 1 1 54%;
  padding: 40px 38px;
  background: rgba(255, 255, 255, 0.94);
}

.form-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: var(--dv-space-lg);

  h2 {
    margin: 0;
    font-size: 21px;
    font-weight: 650;
    color: #101828;
  }
  p {
    margin: 6px 0 0;
    font-size: 12px;
    color: #98a2b3;
  }
  .locale-switch {
    font-size: 12px;
    color: #667085;
    cursor: pointer;
    white-space: nowrap;
  }
}

.captcha-row {
  display: flex;
  gap: 10px;
  width: 100%;

  .captcha-img {
    height: 40px;
    border-radius: 6px;
    cursor: pointer;
    border: 1px solid #dcdfe6;
  }
  .sms-btn {
    min-width: 118px;
  }
}

.submit {
  width: 100%;
  letter-spacing: 4px;
  background: var(--dv-grad-brand);
  border: none;
}

.sms-tip {
  margin: 10px 0 0;
  text-align: center;
  font-size: 12px;
  color: #98a2b3;
}

@media (max-width: 860px) {
  .login-panels {
    flex-direction: column;
  }
  .brand-panel {
    padding: 30px 26px;
  }
  .form-panel {
    padding: 28px 26px;
  }
}
</style>
