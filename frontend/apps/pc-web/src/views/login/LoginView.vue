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
          <li><el-icon><Odometer /></el-icon>{{ t('login.featureRealtime') }}</li>
          <li><el-icon><MagicStick /></el-icon>{{ t('login.featureDrag') }}</li>
          <li><el-icon><Cellphone /></el-icon>{{ t('login.featureMultiEnd') }}</li>
        </ul>
      </section>

      <section class="form-panel">
        <header class="form-head">
          <div>
            <h2>{{ t('common.appSubtitle') }}</h2>
            <p>{{ t('login.tabPassword') }} · {{ t('login.tabSms') }}</p>
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
                  <el-input v-model="form.captcha" :placeholder="t('login.captcha')" prefix-icon="Key" />
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
              <div class="login-footer">
                <el-checkbox v-model="remember">{{ t('login.remember') }}</el-checkbox>
              </div>
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
  import { Cellphone, MagicStick, Odometer } from '@element-plus/icons-vue'
  import { useUserStore } from '@/stores/user'
  import { getCaptcha, sendSmsCode } from '@dataviz/api-client'
  import { switchLocale, getStoredLocale, type AppLocale } from '@/locales'
  import { useI18n } from 'vue-i18n'

  const { t } = useI18n()
  const router = useRouter()
  const route = useRoute()
  const userStore = useUserStore()

  const locale = ref<AppLocale>(getStoredLocale())
  const localeLabel = computed(() => (locale.value === 'en-US' ? 'English' : '简体中文'))
  function onLocale(cmd: string | number | object) {
    locale.value = cmd as AppLocale
    switchLocale(locale.value)
  }

  const activeTab = ref<'password' | 'sms'>('password')

  const formRef = ref<FormInstance>()
  const loading = ref(false)
  const remember = ref(false)
  const captchaImage = ref('')
  const captchaKey = ref('')

  const form = reactive({
    username: '',
    password: '',
    captcha: '',
  })

  const rules = computed<FormRules>(() => ({
    username: [{ required: true, message: t('login.errUsernameRequired'), trigger: 'blur' }],
    password: [{ required: true, message: t('login.errPasswordRequired'), trigger: 'blur' }],
  }))

  const smsFormRef = ref<FormInstance>()
  const smsLoading = ref(false)
  const countdown = ref(0)
  let countdownTimer: ReturnType<typeof setInterval> | null = null

  const smsForm = reactive({
    phone: '',
    code: '',
  })

  const smsRules = computed<FormRules>(() => ({
    phone: [
      { required: true, message: t('login.errPhoneRequired'), trigger: 'blur' },
      { pattern: /^1[3-9]\d{9}$/, message: t('login.errPhoneFormat'), trigger: 'blur' },
    ],
    code: [{ required: true, message: t('login.errCodeRequired'), trigger: 'blur' }],
  }))

  function goHome() {
    const redirect = (route.query.redirect as string) || '/'
    router.push(redirect)
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
      await userStore.login(form.username, form.password, form.captcha, captchaKey.value)
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
      // 终端标识由后端按白名单校验，它决定这次发码落在哪条闸门上
      const res = await sendSmsCode(smsForm.phone, 'pc-web')
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
      await userStore.smsLogin(smsForm.phone, smsForm.code)
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

<style lang="scss" scoped>
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
    width: min(980px, 100%);
    border-radius: var(--dv-radius-xl);
    overflow: hidden;
    border: 1px solid rgba(140, 180, 255, 0.18);
    box-shadow: 0 30px 80px rgba(3, 8, 20, 0.55);
    backdrop-filter: blur(6px);
  }

  .brand-panel {
    flex: 1 1 46%;
    padding: 44px 38px;
    color: var(--dv-screen-text);
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
      font-weight: 600;
      line-height: 1.35;
    }

    .slogan {
      margin: 0;
      font-size: var(--dv-font-sm);
      line-height: 1.7;
      color: var(--dv-screen-text-dim);
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
        font-size: var(--dv-font-xs);
        color: rgba(206, 220, 245, 0.85);
        border-bottom: 1px dashed rgba(140, 180, 255, 0.16);

        &:last-child {
          border-bottom: none;
        }
        .el-icon {
          color: var(--dv-screen-accent);
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
      font-size: var(--dv-font-xl);
      font-weight: 600;
      color: var(--dv-text-1);
    }
    p {
      margin: 6px 0 0;
      font-size: var(--dv-font-xs);
      color: var(--dv-text-3);
    }
    .locale-switch {
      font-size: var(--dv-font-xs);
      color: var(--dv-text-2);
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
      border-radius: var(--dv-radius-sm);
      cursor: pointer;
      border: 1px solid var(--dv-border);
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

  .login-footer {
    margin-top: var(--dv-space-sm);
    text-align: center;
  }

  .sms-tip {
    margin: 10px 0 0;
    text-align: center;
    font-size: var(--dv-font-xs);
    color: var(--dv-text-3);
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
