<template>
  <div class="dv-page">
    <div class="dv-page-head">
      <div>
        <h2>{{ t('license.title') }}</h2>
        <p class="dv-page-desc">{{ t('license.desc') }}</p>
      </div>
      <div class="dv-page-actions">
        <el-button :loading="loading" @click="load('validate')">{{ t('license.refresh') }}</el-button>
      </div>
    </div>

    <div class="license-hero" :class="heroClass" v-loading="loading">
      <div class="hero-main">
        <p class="hero-edition">{{ info?.activated ? info?.type || t('license.unknown') : t('license.notActivated') }}</p>
        <p class="hero-state">
          <el-icon><component :is="stateIcon" /></el-icon>
          {{ stateText }}
        </p>
        <p class="hero-message" v-if="info?.message">{{ info.message }}</p>
      </div>
      <div class="hero-side">
        <span class="dv-mono">{{ info?.issuedTo || '-' }}</span>
        <span>{{ info?.issuedAt }} → {{ info?.expiresAt }}</span>
      </div>
    </div>

    <div class="dv-grid quota">
      <div class="dv-stat">
        <p class="dv-stat-label">{{ t('license.maxUsers') }}</p>
        <p class="dv-stat-value">{{ info?.maxUsers ?? '-' }}</p>
      </div>
      <div class="dv-stat">
        <p class="dv-stat-label">{{ t('license.maxNodes') }}</p>
        <p class="dv-stat-value">{{ info?.maxNodes ?? '-' }}</p>
      </div>
      <div class="dv-stat">
        <p class="dv-stat-label">{{ t('license.daysLeft') }}</p>
        <p class="dv-stat-value">{{ info?.daysLeft ?? '-' }}</p>
      </div>
      <div class="dv-stat">
        <p class="dv-stat-label">{{ t('license.issuedAt') }}</p>
        <p class="dv-stat-value small">{{ info?.issuedAt ?? '-' }}</p>
      </div>
      <div class="dv-stat">
        <p class="dv-stat-label">{{ t('license.expiresAt') }}</p>
        <p class="dv-stat-value small">{{ info?.expiresAt ?? '-' }}</p>
      </div>
    </div>

    <div class="dv-grid audit" v-if="info?.activated">
      <div class="dv-stat">
        <p class="dv-stat-label">{{ t('license.activateTime') }}</p>
        <p class="dv-stat-value small">{{ info.activateTime || '-' }}</p>
      </div>
      <div class="dv-stat">
        <p class="dv-stat-label">{{ t('license.activatedBy') }}</p>
        <p class="dv-stat-value small">{{ info.activatedBy || '-' }}</p>
      </div>
      <div class="dv-stat">
        <p class="dv-stat-label">{{ t('license.fingerprint') }}</p>
        <p class="dv-stat-value small dv-mono">{{ info.fingerprint || '-' }}</p>
      </div>
    </div>

    <el-card v-permission="'platform:write'" shadow="never" class="activate-card">
      <template #header>{{ t('license.activate') }}</template>
      <div class="activate-row">
        <el-input
          v-model="licenseKey"
          type="textarea"
          :rows="2"
          :placeholder="t('license.keyPlaceholder')"
          class="dv-mono"
        />
        <el-button type="primary" :loading="activating" :disabled="!licenseKey.trim()" @click="onActivate">
          {{ t('license.activate') }}
        </el-button>
      </div>
      <p class="dv-page-desc">{{ t('license.activateTip') }}</p>
      <el-alert
        v-if="activateHint"
        :type="info?.activated ? 'warning' : 'info'"
        :closable="false"
        show-icon
        :title="activateHint"
      />
    </el-card>
  </div>
</template>

<script setup lang="ts">
  import { computed, onMounted, ref } from 'vue'
  import { ElMessage } from 'element-plus'
  import { CircleCheck, CircleClose, QuestionFilled } from '@element-plus/icons-vue'
  import type { LicenseInfo } from '@dataviz/shared-types'
  import { activateLicense, getLicenseInfo, validateLicense } from '@dataviz/api-client'
  import { useI18n } from 'vue-i18n'

  const { t } = useI18n()

  const loading = ref(false)
  const activating = ref(false)
  const info = ref<LicenseInfo | null>(null)
  const licenseKey = ref('')

  const heroClass = computed(() => {
    if (!info.value?.activated) return 'is-none'
    return info.value.valid ? 'is-valid' : 'is-invalid'
  })
  const stateIcon = computed(() => {
    if (!info.value?.activated) return QuestionFilled
    return info.value.valid ? CircleCheck : CircleClose
  })
  const stateText = computed(() => {
    if (!info.value) return t('license.unknown')
    if (!info.value.activated) return t('license.notActivated')
    return info.value.valid ? t('license.valid') : t('license.invalid')
  })
  /**
   * 未激活时给"下一步做什么"，已激活但失效时给"这是过期不是坏了"。
   * 后端消息本身已经说明了原因，这里只补它没法说的那半句：签发密钥在环境变量里。
   */
  const activateHint = computed(() => {
    if (!info.value) return ''
    if (!info.value.activated) return t('license.notActivatedTip')
    if (!info.value.valid) return t('license.expiredTip')
    return ''
  })

  async function load(source: 'info' | 'validate' = 'info') {
    loading.value = true
    try {
      info.value = source === 'validate' ? await validateLicense() : await getLicenseInfo()
    } catch (e) {
      info.value = null
      ElMessage.error((e as Error).message)
    } finally {
      loading.value = false
    }
  }

  async function onActivate() {
    activating.value = true
    try {
      // 验签失败时后端抛业务错误，request 拦截器会 reject；这里的 catch 就是"密钥不对"
      info.value = await activateLicense(licenseKey.value.trim())
      ElMessage.success(t('license.activateSuccess'))
      licenseKey.value = ''
    } catch (e) {
      ElMessage.error((e as Error).message || t('license.activateFailed'))
    } finally {
      activating.value = false
    }
  }

  onMounted(() => load())
</script>

<style lang="scss" scoped>
  .license-hero {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: var(--dv-space-lg);
    padding: var(--dv-space-xl);
    margin-bottom: var(--dv-space-lg);
    border-radius: var(--dv-radius-lg);
    border: 1px solid var(--dv-border-soft);
    background: var(--dv-surface-1);
    box-shadow: var(--dv-shadow-md);

    &.is-valid {
      border-color: rgba(52, 211, 153, 0.45);
      background: linear-gradient(135deg, rgba(47, 124, 255, 0.16), rgba(52, 211, 153, 0.14));
    }
    &.is-invalid {
      border-color: rgba(248, 113, 113, 0.45);
      background: linear-gradient(135deg, rgba(248, 113, 113, 0.16), rgba(251, 191, 36, 0.12));
    }
    /* 未激活不是错误状态：给它中性底色，避免把"还没配钥匙"看成"许可证坏了" */
    &.is-none {
      border-color: var(--dv-border-soft);
      background: var(--dv-surface-1);
    }
  }

  .hero-edition {
    margin: 0;
    font-size: 26px;
    font-weight: 680;
    letter-spacing: 0.4px;
    background: var(--dv-grad-brand);
    -webkit-background-clip: text;
    background-clip: text;
    color: transparent;
  }

  .hero-state {
    display: flex;
    align-items: center;
    gap: 6px;
    margin: 8px 0 0;
    font-size: var(--dv-font-sm);
    color: var(--dv-text-2);
  }

  .hero-message {
    margin: 10px 0 0;
    max-width: 60ch;
    font-size: var(--dv-font-sm);
    color: var(--dv-text-3);
  }

  .hero-side {
    display: flex;
    flex-direction: column;
    align-items: flex-end;
    gap: 4px;
    font-size: var(--dv-font-xs);
    color: var(--dv-text-3);
  }

  .quota {
    grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
    margin-bottom: var(--dv-space-lg);
  }

  .audit {
    grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
    margin-bottom: var(--dv-space-lg);
  }

  .dv-stat-value.small {
    font-size: var(--dv-font-lg);
  }

  .activate-row {
    display: flex;
    align-items: flex-start;
    gap: var(--dv-space-md);
  }

  .activate-card :deep(.el-textarea__inner) {
    font-size: 12px;
  }
</style>
