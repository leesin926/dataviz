<template>
  <div class="dv-page">
    <div class="dv-page-head">
      <div>
        <h2>{{ t('dashboard.title') }}</h2>
        <p class="dv-page-desc">{{ t('dashboard.desc') }}</p>
      </div>
      <div class="dv-page-actions">
        <el-button :loading="loading" @click="load">{{ t('common.refresh') }}</el-button>
      </div>
    </div>

    <div class="dv-grid stats">
      <div class="dv-stat">
        <p class="dv-stat-label">{{ t('dashboard.tenantCount') }}</p>
        <p class="dv-stat-value">{{ tenants ?? '-' }}</p>
      </div>
      <div class="dv-stat">
        <p class="dv-stat-label">{{ t('dashboard.userCount') }}</p>
        <p class="dv-stat-value">{{ users ?? '-' }}</p>
      </div>
      <div class="dv-stat">
        <p class="dv-stat-label">{{ t('dashboard.screenCount') }}</p>
        <p class="dv-stat-value">{{ screens ?? '-' }}</p>
      </div>
      <div class="dv-stat">
        <p class="dv-stat-label">{{ t('dashboard.auditCount') }}</p>
        <p class="dv-stat-value">{{ audits ?? '-' }}</p>
      </div>
    </div>

    <el-row :gutter="16">
      <el-col :span="16">
        <el-card shadow="never">
          <template #header>
            <div class="card-head">
              <span>{{ t('dashboard.recentAudit') }}</span>
              <el-button link type="primary" @click="router.push('/audit')">{{ t('dashboard.viewAudit') }}</el-button>
            </div>
          </template>
          <el-table :data="recent" v-loading="loading" size="small">
            <el-table-column prop="username" :label="t('audit.username')" width="120" />
            <el-table-column prop="module" :label="t('audit.module')" width="110" />
            <el-table-column prop="action" :label="t('audit.action')" width="110" />
            <el-table-column prop="requestUrl" :label="t('audit.requestUrl')" min-width="200" show-overflow-tooltip />
            <el-table-column prop="createTime" :label="t('audit.time')" width="170" />
            <template #empty>
              <span class="dv-text-3">{{ t('common.empty') }}</span>
            </template>
          </el-table>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="never" class="side-card">
          <template #header>{{ t('dashboard.licenseTitle') }}</template>
          <p class="license-line">
            <span>{{ license?.type || t('license.unknown') }}</span>
            <el-tag :type="license?.valid ? 'success' : 'danger'" size="small" disable-transitions>
              {{ license?.valid ? t('dashboard.valid') : t('dashboard.expired') }}
            </el-tag>
          </p>
          <ul class="license-list">
            <li><span>{{ t('license.issuedTo') }}</span><b class="dv-mono">{{ license?.issuedTo || '-' }}</b></li>
            <li><span>{{ t('license.expiresAt') }}</span><b>{{ license?.expiresAt || '-' }}</b></li>
            <li>
              <span>{{ t('license.maxUsers') }}</span><b>{{ license?.maxUsers ?? '-' }}</b>
            </li>
            <li><span>{{ t('license.maxNodes') }}</span><b>{{ license?.maxNodes ?? '-' }}</b></li>
          </ul>
        </el-card>

        <el-card shadow="never" class="side-card">
          <template #header>{{ t('dashboard.quickLinks') }}</template>
          <div class="quick-links">
            <el-button text @click="router.push('/tenant')">{{ t('dashboard.manageTenant') }}</el-button>
            <el-button text @click="router.push('/users')">{{ t('dashboard.manageUser') }}</el-button>
            <el-button text @click="router.push('/config')">{{ t('nav.config') }}</el-button>
            <el-button text @click="router.push('/roles')">{{ t('nav.roles') }}</el-button>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <p v-if="failed.length" class="dv-page-desc partial">{{ t('dashboard.loadFailed') }}: {{ failed.join(', ') }}</p>
  </div>
</template>

<script setup lang="ts">
  import { onMounted, ref } from 'vue'
  import { useRouter } from 'vue-router'
  import { useI18n } from 'vue-i18n'
  import type { AuditLog, LicenseInfo } from '@dataviz/shared-types'
  import { rowsOf, totalOf } from '@dataviz/shared-types'
  import { getLicenseInfo, listScreens, pageAuditLogs, pageTenants, listUsers } from '@dataviz/api-client'

  const { t } = useI18n()
  const router = useRouter()

  const loading = ref(false)
  const tenants = ref<number | null>(null)
  const users = ref<number | null>(null)
  const screens = ref<number | null>(null)
  const audits = ref<number | null>(null)
  const recent = ref<AuditLog[]>([])
  const license = ref<LicenseInfo | null>(null)
  const failed = ref<string[]>([])

  async function safe<T>(label: string, fn: () => Promise<T>): Promise<T | null> {
    try {
      return await fn()
    } catch {
      failed.value.push(t(label))
      return null
    }
  }

  async function load() {
    loading.value = true
    failed.value = []
    const [tenantRes, userRes, screenRes, auditRes, licenseRes] = await Promise.all([
      safe('nav.tenant', () => pageTenants({ pageNum: 1, pageSize: 1 })),
      safe('nav.users', () => listUsers({ pageNum: 1, pageSize: 1 })),
      safe('dashboard.screenCount', () => listScreens({ pageNum: 1, pageSize: 1 })),
      safe('nav.audit', () => pageAuditLogs({ pageNum: 1, pageSize: 8 })),
      safe('license.title', () => getLicenseInfo()),
    ])
    tenants.value = tenantRes ? totalOf(tenantRes) : null
    users.value = userRes ? totalOf(userRes) : null
    screens.value = screenRes ? totalOf(screenRes) : null
    audits.value = auditRes ? totalOf(auditRes) : null
    recent.value = auditRes ? rowsOf(auditRes) : []
    license.value = licenseRes
    loading.value = false
  }

  onMounted(load)
</script>

<style lang="scss" scoped>
  .stats {
    grid-template-columns: repeat(auto-fit, minmax(190px, 1fr));
    margin-bottom: var(--dv-space-lg);
  }

  .card-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
  }

  .side-card {
    margin-bottom: var(--dv-space-lg);
  }

  .license-line {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin: 0 0 var(--dv-space-md);
    font-size: var(--dv-font-lg);
    font-weight: 620;
  }

  .license-list {
    margin: 0;
    padding: 0;
    list-style: none;
    font-size: var(--dv-font-sm);

    li {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 6px 0;
      border-bottom: 1px dashed var(--dv-border-soft);

      &:last-child {
        border-bottom: none;
      }
      span {
        color: var(--dv-text-3);
      }
    }
  }

  .quick-links {
    display: flex;
    flex-wrap: wrap;
    gap: var(--dv-space-sm);
  }

  .partial {
    margin-top: var(--dv-space-md);
  }
</style>
