<template>
  <div class="dv-page">
    <div class="dv-page-head">
      <div>
        <h2>{{ t('license.title') }}</h2>
        <p class="dv-page-desc">{{ t('license.desc') }}</p>
      </div>
      <div class="dv-page-actions">
        <el-button :loading="loading" @click="load">{{ t('license.refresh') }}</el-button>
      </div>
    </div>

    <div class="license-hero" :class="heroClass" v-loading="loading">
      <div class="hero-main">
        <p class="hero-edition">{{ info?.type || t('license.unknown') }}</p>
        <p class="hero-state">
          <el-icon><component :is="stateIcon" /></el-icon>
          {{ stateText }}
        </p>
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
        <p class="dv-stat-label">{{ t('license.issuedAt') }}</p>
        <p class="dv-stat-value small">{{ info?.issuedAt ?? '-' }}</p>
      </div>
      <div class="dv-stat">
        <p class="dv-stat-label">{{ t('license.expiresAt') }}</p>
        <p class="dv-stat-value small">{{ info?.expiresAt ?? '-' }}</p>
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
    </el-card>
  </div>
</template>

<script setup lang="ts">
  import { computed, onMounted, ref } from 'vue'
  import { ElMessage } from 'element-plus'
  import { CircleCheck, CircleClose } from '@element-plus/icons-vue'
  import type { LicenseInfo } from '@dataviz/shared-types'
  import { activateLicense, getLicenseInfo, validateLicense } from '@dataviz/api-client'
  import { useI18n } from 'vue-i18n'

  const { t } = useI18n()

  const loading = ref(false)
  const activating = ref(false)
  const info = ref<LicenseInfo | null>(null)
  const licenseKey = ref('')

  const heroClass = computed(() => (info.value?.valid ? 'is-valid' : 'is-invalid'))
  const stateIcon = computed(() => (info.value?.valid ? CircleCheck : CircleClose))
  const stateText = computed(() => {
    if (!info.value) return t('license.unknown')
    return info.value.valid ? t('license.valid') : t('license.invalid')
  })

  async function load() {
    loading.value = true
    try {
      info.value = await getLicenseInfo()
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
      const res = await activateLicense(licenseKey.value.trim())
      if (res?.success === false) {
        ElMessage.error(res.message || t('license.activateFailed'))
        return
      }
      ElMessage.success(t('license.activateSuccess'))
      licenseKey.value = ''
      await validateLicense()
      load()
    } catch (e) {
      ElMessage.error((e as Error).message || t('license.activateFailed'))
    } finally {
      activating.value = false
    }
  }

  onMounted(load)
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
