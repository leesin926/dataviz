<template>
  <div class="dv-page">
    <div class="dv-page-head">
      <div>
        <h2>{{ t('openPlatform.title') }}</h2>
        <p class="dv-page-desc">{{ t('openPlatform.desc') }}</p>
      </div>
    </div>

    <el-tabs v-model="tab">
      <!-- ═══════════ API 客户端 ═══════════ -->
      <el-tab-pane :label="t('openPlatform.tabClients')" name="clients">
        <div class="dv-toolbar">
          <el-button v-permission="'openapi:write'" type="primary" @click="openKeyCreate">
            {{ t('openPlatform.createClient') }}
          </el-button>
          <el-button :loading="clientsLoading" @click="loadClients">{{ t('common.refresh') }}</el-button>
        </div>
        <el-alert class="notice" type="info" :closable="false" :title="t('openPlatform.secretOnceHint')" show-icon />
        <el-table :data="clients" v-loading="clientsLoading" stripe>
          <el-table-column prop="appName" :label="t('openPlatform.appName')" min-width="140" show-overflow-tooltip />
          <el-table-column :label="t('openPlatform.appKey')" min-width="230">
            <template #default="{ row }"><span class="mono">{{ row.appKey }}</span></template>
          </el-table-column>
          <el-table-column :label="t('common.status')" width="110">
            <template #default="{ row }">
              <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'" size="small" disable-transitions>
                {{ clientStatusText(row.status) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column :label="t('openPlatform.rateLimit')" width="110">
            <template #default="{ row }">{{ row.rateLimit ?? '—' }}</template>
          </el-table-column>
          <el-table-column :label="t('openPlatform.allowedIps')" min-width="160">
            <template #default="{ row }">
              <span v-if="row.allowedIps" class="mono">{{ row.allowedIps }}</span>
              <span v-else class="dv-text-3">{{ t('openPlatform.ipAny') }}</span>
            </template>
          </el-table-column>
          <el-table-column :label="t('openPlatform.expireTime')" width="170">
            <template #default="{ row }">{{ row.expireTime || t('openPlatform.noExpire') }}</template>
          </el-table-column>
          <el-table-column :label="t('common.operations')" width="180" fixed="right">
            <template #default="{ row }">
              <el-button v-permission="'openapi:write'" link type="primary" @click="onResetSecret(row)">
                {{ t('openPlatform.resetSecret') }}
              </el-button>
              <el-button
                v-if="row.status === 'ACTIVE'"
                v-permission="'openapi:write'"
                link
                type="danger"
                @click="onDisable(row)"
              >
                {{ t('openPlatform.disable') }}
              </el-button>
              <span v-else class="dv-text-3">{{ t('openPlatform.disabledNoEnable') }}</span>
            </template>
          </el-table-column>
          <template #empty><span class="dv-text-3">{{ t('common.empty') }}</span></template>
        </el-table>
      </el-tab-pane>

      <!-- ═══════════ 开放应用 ═══════════ -->
      <el-tab-pane :label="t('openPlatform.tabApps')" name="apps">
        <div class="dv-toolbar">
          <el-button v-permission="'openapi:write'" type="primary" @click="openAppCreate">
            {{ t('openPlatform.createApp') }}
          </el-button>
          <el-button :loading="appsLoading" @click="loadApps">{{ t('common.refresh') }}</el-button>
        </div>
        <el-alert class="notice" type="warning" :closable="false" :title="t('openPlatform.appSecretExposedTitle')" :description="t('openPlatform.appSecretExposedDesc')" show-icon />
        <el-table :data="apps" v-loading="appsLoading" stripe>
          <el-table-column prop="appName" :label="t('openPlatform.appName')" min-width="140" show-overflow-tooltip />
          <el-table-column :label="t('openPlatform.appKey')" min-width="230">
            <template #default="{ row }"><span class="mono">{{ row.appKey }}</span></template>
          </el-table-column>
          <el-table-column :label="t('openPlatform.appSecret')" min-width="230">
            <template #default="{ row }">
              <span class="mono">{{ row.id === revealedAppId ? row.appSecret : mask(row.appSecret) }}</span>
              <el-button v-if="row.appSecret" link type="primary" @click="toggleReveal(row)">
                {{ row.id === revealedAppId ? t('openPlatform.hide') : t('openPlatform.show') }}
              </el-button>
            </template>
          </el-table-column>
          <el-table-column :label="t('openPlatform.permissions')" min-width="150">
            <template #default="{ row }">
              <span v-if="row.permissions" class="mono">{{ row.permissions }}</span>
              <span v-else class="dv-text-3">—</span>
            </template>
          </el-table-column>
          <el-table-column :label="t('openPlatform.rateLimit')" width="100">
            <template #default="{ row }">{{ row.rateLimit ?? '—' }}</template>
          </el-table-column>
          <el-table-column :label="t('openPlatform.ipWhitelist')" min-width="150">
            <template #default="{ row }">
              <span v-if="row.ipWhitelist" class="mono">{{ row.ipWhitelist }}</span>
              <span v-else class="dv-text-3">{{ t('openPlatform.ipAny') }}</span>
            </template>
          </el-table-column>
          <el-table-column :label="t('common.operations')" width="230" fixed="right">
            <template #default="{ row }">
              <el-button v-permission="'openapi:write'" link type="primary" @click="openWebhooks(row)">
                {{ t('openPlatform.webhooks') }}
              </el-button>
              <el-button v-permission="'openapi:write'" link type="primary" @click="onRegenerateApp(row)">
                {{ t('openPlatform.resetSecret') }}
              </el-button>
              <el-button v-permission="'openapi:write'" link type="danger" @click="onDeleteApp(row)">
                {{ t('common.delete') }}
              </el-button>
            </template>
          </el-table-column>
          <template #empty><span class="dv-text-3">{{ t('common.empty') }}</span></template>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <!-- 铸造/重置密钥：口令只在这一次出现，所以单独弹窗、必须当场复制 -->
    <el-dialog v-model="secretVisible" :title="t('openPlatform.secretTitle')" width="560px" :close-on-click-modal="false">
      <el-alert class="notice" type="error" :closable="false" :title="t('openPlatform.secretOnceWarning')" show-icon />
      <el-form label-width="90px">
        <el-form-item :label="t('openPlatform.appName')">
          <span>{{ secretResult?.appName || secretResult?.appKey }}</span>
        </el-form-item>
        <el-form-item :label="t('openPlatform.appKey')">
          <div class="copy-row">
            <span class="mono">{{ secretResult?.appKey }}</span>
            <el-button link type="primary" @click="copy(secretResult?.appKey)">{{ t('openPlatform.copy') }}</el-button>
          </div>
        </el-form-item>
        <el-form-item :label="t('openPlatform.appSecret')">
          <div class="copy-row">
            <span class="mono">{{ secretResult?.appSecret }}</span>
            <el-button link type="primary" @click="copy(secretResult?.appSecret)">{{ t('openPlatform.copy') }}</el-button>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button type="primary" @click="secretVisible = false">{{ t('openPlatform.secretSavedConfirm') }}</el-button>
      </template>
    </el-dialog>

    <!-- 新建客户端 -->
    <el-dialog v-model="keyDialogVisible" :title="t('openPlatform.createClient')" width="560px">
      <el-form ref="keyFormRef" :model="keyForm" :rules="keyRules" label-width="110px">
        <el-form-item :label="t('openPlatform.appName')" prop="appName">
          <el-input v-model="keyForm.appName" maxlength="128" show-word-limit />
        </el-form-item>
        <el-form-item :label="t('openPlatform.rateLimit')">
          <el-input-number v-model="keyForm.rateLimit" :min="1" :max="100000" />
          <span class="hint dv-text-3">{{ t('openPlatform.rateLimitHint') }}</span>
        </el-form-item>
        <el-form-item :label="t('openPlatform.allowedIps')">
          <el-input
            v-model="keyForm.allowedIpsText"
            type="textarea"
            :rows="2"
            :placeholder="t('openPlatform.allowedIpsPlaceholder')"
          />
          <span class="hint dv-text-3">{{ t('openPlatform.allowedIpsHint') }}</span>
        </el-form-item>
        <el-form-item :label="t('openPlatform.expireTime')">
          <el-date-picker v-model="keyForm.expireTime" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" />
          <span class="hint dv-text-3">{{ t('openPlatform.expireHint') }}</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="keyDialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="saving" @click="onCreateClient">{{ t('common.save') }}</el-button>
      </template>
    </el-dialog>

    <!-- 新建开放应用 -->
    <el-dialog v-model="appDialogVisible" :title="t('openPlatform.createApp')" width="560px">
      <el-form ref="appFormRef" :model="appForm" :rules="appRules" label-width="110px">
        <el-form-item :label="t('openPlatform.appName')" prop="appName">
          <el-input v-model="appForm.appName" maxlength="128" show-word-limit />
        </el-form-item>
        <el-form-item :label="t('openPlatform.permissions')">
          <el-input v-model="appForm.permissions" :placeholder="t('openPlatform.permissionsPlaceholder')" />
          <span class="hint dv-text-3">{{ t('openPlatform.permissionsHint') }}</span>
        </el-form-item>
        <el-form-item :label="t('openPlatform.rateLimit')">
          <el-input-number v-model="appForm.rateLimit" :min="1" :max="100000" />
        </el-form-item>
        <el-form-item :label="t('openPlatform.ipWhitelist')">
          <el-input v-model="appForm.ipWhitelist" :placeholder="t('openPlatform.ipWhitelistPlaceholder')" />
          <span class="hint dv-text-3">{{ t('openPlatform.ipWhitelistHint') }}</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="appDialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="saving" @click="onCreateApp">{{ t('common.save') }}</el-button>
      </template>
    </el-dialog>

    <!-- 回调地址：挂在某个应用下 -->
    <el-drawer v-model="webhookVisible" :title="t('openPlatform.webhookTitle', { name: webhookAppName })" size="680px">
      <div class="dv-toolbar">
        <el-button v-permission="'openapi:write'" type="primary" @click="openWebhookCreate">
          {{ t('openPlatform.createWebhook') }}
        </el-button>
        <el-button :loading="webhookLoading" @click="loadWebhooks">{{ t('common.refresh') }}</el-button>
      </div>
      <el-table :data="webhooks" v-loading="webhookLoading" stripe>
        <el-table-column prop="eventType" :label="t('openPlatform.eventType')" width="160" />
        <el-table-column prop="url" :label="t('openPlatform.url')" min-width="220" show-overflow-tooltip>
          <template #default="{ row }"><span class="mono">{{ row.url }}</span></template>
        </el-table-column>
        <el-table-column :label="t('common.operations')" width="150" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="'openapi:write'" link type="primary" @click="openWebhookEdit(row)">
              {{ t('common.edit') }}
            </el-button>
            <el-button v-permission="'openapi:write'" link type="danger" @click="onDeleteWebhook(row)">
              {{ t('common.delete') }}
            </el-button>
          </template>
        </el-table-column>
        <template #empty><span class="dv-text-3">{{ t('common.empty') }}</span></template>
      </el-table>
      <p class="dv-page-desc">{{ t('openPlatform.webhookNote') }}</p>

      <el-form ref="webhookFormRef" :model="webhookForm" :rules="webhookRules" label-width="110px" class="webhook-form">
        <el-form-item :label="t('openPlatform.eventType')" prop="eventType">
          <el-input v-model="webhookForm.eventType" :placeholder="t('openPlatform.eventTypePlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('openPlatform.url')" prop="url">
          <el-input v-model="webhookForm.url" class="mono" placeholder="https://..." />
        </el-form-item>
        <el-form-item :label="t('openPlatform.signSecret')">
          <el-input v-model="webhookForm.secret" :placeholder="t('openPlatform.signSecretPlaceholder')" />
          <span class="hint dv-text-3">{{ t('openPlatform.signSecretHint') }}</span>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="saving" @click="onSubmitWebhook">
            {{ webhookForm.id ? t('common.save') : t('openPlatform.createWebhook') }}
          </el-button>
          <el-button v-if="webhookForm.id" @click="resetWebhookForm">{{ t('common.cancel') }}</el-button>
        </el-form-item>
      </el-form>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
  import { computed, onMounted, reactive, ref } from 'vue'
  import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
  import type {
    OpenApiClient,
    OpenApiApp,
    OpenApiKeyResult,
    OpenApiWebhook,
  } from '@dataviz/shared-types'
  import {
    createApp,
    createClient,
    createWebhook,
    deleteApp,
    deleteWebhook,
    disableClient,
    listApps,
    listClients,
    listWebhooks,
    regenerateAppSecret,
    resetClientSecret,
    updateWebhook,
  } from '@dataviz/api-client'
  import { useI18n } from 'vue-i18n'

  const { t } = useI18n()

  const tab = ref<'clients' | 'apps'>('clients')
  const saving = ref(false)

  const clients = ref<OpenApiClient[]>([])
  const clientsLoading = ref(false)
  const apps = ref<OpenApiApp[]>([])
  const appsLoading = ref(false)
  const revealedAppId = ref<string | number>('')

  const secretVisible = ref(false)
  const secretResult = ref<OpenApiKeyResult | null>(null)

  const keyDialogVisible = ref(false)
  const keyFormRef = ref<FormInstance>()
  const keyForm = reactive({ appName: '', rateLimit: 60, allowedIpsText: '', expireTime: '' })
  const keyRules = computed<FormRules>(() => ({
    appName: [{ required: true, message: t('openPlatform.appNameRequired'), trigger: 'blur' }],
  }))

  const appDialogVisible = ref(false)
  const appFormRef = ref<FormInstance>()
  const appForm = reactive({ appName: '', permissions: '', rateLimit: 60, ipWhitelist: '' })
  const appRules = computed<FormRules>(() => ({
    appName: [{ required: true, message: t('openPlatform.appNameRequired'), trigger: 'blur' }],
  }))

  const webhookVisible = ref(false)
  const webhookLoading = ref(false)
  const webhookAppId = ref<string | number>('')
  const webhookAppName = ref('')
  const webhooks = ref<OpenApiWebhook[]>([])
  const webhookFormRef = ref<FormInstance>()
  const webhookForm = reactive<{ id: string | number; eventType: string; url: string; secret: string }>({
    id: '',
    eventType: '',
    url: '',
    secret: '',
  })
  const webhookRules = computed<FormRules>(() => ({
    eventType: [{ required: true, message: t('openPlatform.eventTypeRequired'), trigger: 'blur' }],
    url: [
      { required: true, message: t('openPlatform.urlRequired'), trigger: 'blur' },
      { pattern: /^https?:\/\/\S+$/, message: t('openPlatform.urlPattern'), trigger: 'blur' },
    ],
  }))

  function clientStatusText(value: string): string {
    const key = `openPlatform.clientStatus.${value}`
    const text = t(key)
    return text === key ? value : text
  }

  function mask(value?: string | null): string {
    if (!value) return '—'
    return `${value.slice(0, 4)}••••••••${value.slice(-4)}`
  }

  /** 逗号/空格/换行任意的分隔都算一个 IP，回给后端的必须是数组 */
  function splitIps(text: string): string[] {
    return text
      .split(/[\s,;]+/)
      .map((s) => s.trim())
      .filter(Boolean)
  }

  async function copy(text?: string) {
    if (!text) return
    try {
      await navigator.clipboard.writeText(text)
      ElMessage.success(t('openPlatform.copied'))
    } catch {
      // 非安全上下文（http:// 且非 localhost）下 navigator.clipboard 直接不存在，
      // 复制失败必须说出来：用户以为已经存好了，口令却随弹窗一起没了
      ElMessage.warning(t('openPlatform.copyFailed'))
    }
  }

  async function loadClients() {
    clientsLoading.value = true
    try {
      clients.value = await listClients()
    } catch (e) {
      ElMessage.error((e as Error).message)
      clients.value = []
    } finally {
      clientsLoading.value = false
    }
  }

  async function loadApps() {
    appsLoading.value = true
    try {
      apps.value = await listApps()
    } catch (e) {
      ElMessage.error((e as Error).message)
      apps.value = []
    } finally {
      appsLoading.value = false
    }
  }

  function showSecret(result: OpenApiKeyResult) {
    secretResult.value = result
    secretVisible.value = true
  }

  function openKeyCreate() {
    keyForm.appName = ''
    keyForm.rateLimit = 60
    keyForm.allowedIpsText = ''
    keyForm.expireTime = ''
    keyDialogVisible.value = true
  }

  async function onCreateClient() {
    const valid = await keyFormRef.value?.validate().catch(() => false)
    if (!valid) return
    saving.value = true
    try {
      const result = await createClient({
        appName: keyForm.appName.trim(),
        rateLimit: keyForm.rateLimit,
        allowedIps: splitIps(keyForm.allowedIpsText),
        expireTime: keyForm.expireTime || undefined,
      })
      keyDialogVisible.value = false
      ElMessage.success(t('openPlatform.clientCreated'))
      showSecret(result)
      loadClients()
    } catch (e) {
      ElMessage.error((e as Error).message)
    } finally {
      saving.value = false
    }
  }

  async function onResetSecret(row: OpenApiClient) {
    const confirmed = await ElMessageBox.confirm(
      t('openPlatform.resetConfirm', { name: row.appName }),
      t('common.warning'),
      { type: 'warning' },
    ).catch(() => false)
    if (!confirmed) return
    try {
      showSecret(await resetClientSecret(row.id))
      loadClients()
    } catch (e) {
      ElMessage.error((e as Error).message)
    }
  }

  async function onDisable(row: OpenApiClient) {
    const confirmed = await ElMessageBox.confirm(
      t('openPlatform.disableConfirm', { name: row.appName }),
      t('common.warning'),
      { type: 'warning' },
    ).catch(() => false)
    if (!confirmed) return
    try {
      await disableClient(row.id)
      ElMessage.success(t('openPlatform.disabled'))
      loadClients()
    } catch (e) {
      ElMessage.error((e as Error).message)
    }
  }

  function openAppCreate() {
    appForm.appName = ''
    appForm.permissions = ''
    appForm.rateLimit = 60
    appForm.ipWhitelist = ''
    appDialogVisible.value = true
  }

  async function onCreateApp() {
    const valid = await appFormRef.value?.validate().catch(() => false)
    if (!valid) return
    saving.value = true
    try {
      await createApp({
        appName: appForm.appName.trim(),
        permissions: appForm.permissions.trim() || undefined,
        rateLimit: appForm.rateLimit,
        ipWhitelist: appForm.ipWhitelist.trim() || undefined,
      })
      appDialogVisible.value = false
      ElMessage.success(t('openPlatform.appCreated'))
      loadApps()
    } catch (e) {
      ElMessage.error((e as Error).message)
    } finally {
      saving.value = false
    }
  }

  function toggleReveal(row: OpenApiApp) {
    revealedAppId.value = revealedAppId.value === row.id ? '' : row.id
  }

  async function onRegenerateApp(row: OpenApiApp) {
    const confirmed = await ElMessageBox.confirm(
      t('openPlatform.resetConfirm', { name: row.appName }),
      t('common.warning'),
      { type: 'warning' },
    ).catch(() => false)
    if (!confirmed) return
    try {
      const updated = await regenerateAppSecret(row.id)
      revealedAppId.value = updated.id
      ElMessage.success(t('openPlatform.secretRegenerated'))
      loadApps()
    } catch (e) {
      ElMessage.error((e as Error).message)
    }
  }

  async function onDeleteApp(row: OpenApiApp) {
    const confirmed = await ElMessageBox.confirm(
      t('common.confirmDelete', { name: row.appName }),
      t('common.warning'),
      { type: 'warning' },
    ).catch(() => false)
    if (!confirmed) return
    try {
      await deleteApp(row.id)
      ElMessage.success(t('openPlatform.appDeleted'))
      loadApps()
    } catch (e) {
      ElMessage.error((e as Error).message)
    }
  }

  function openWebhooks(row: OpenApiApp) {
    webhookAppId.value = row.id
    webhookAppName.value = row.appName
    resetWebhookForm()
    webhooks.value = []
    webhookVisible.value = true
    loadWebhooks()
  }

  async function loadWebhooks() {
    if (!webhookAppId.value) return
    webhookLoading.value = true
    try {
      webhooks.value = await listWebhooks(webhookAppId.value)
    } catch (e) {
      ElMessage.error((e as Error).message)
      webhooks.value = []
    } finally {
      webhookLoading.value = false
    }
  }

  function resetWebhookForm() {
    webhookForm.id = ''
    webhookForm.eventType = ''
    webhookForm.url = ''
    webhookForm.secret = ''
    nextTickClear()
  }

  function nextTickClear() {
    // el-form 的残留校验提示在切换新增/编辑时最容易误导人
    setTimeout(() => webhookFormRef.value?.clearValidate(), 0)
  }

  function openWebhookCreate() {
    resetWebhookForm()
  }

  function openWebhookEdit(row: OpenApiWebhook) {
    webhookForm.id = row.id
    webhookForm.eventType = row.eventType ?? ''
    webhookForm.url = row.url ?? ''
    webhookForm.secret = row.secret ?? ''
    nextTickClear()
  }

  async function onSubmitWebhook() {
    const valid = await webhookFormRef.value?.validate().catch(() => false)
    if (!valid) return
    saving.value = true
    try {
      const payload = {
        appId: webhookAppId.value,
        eventType: webhookForm.eventType.trim(),
        url: webhookForm.url.trim(),
        secret: webhookForm.secret.trim() || undefined,
      }
      if (webhookForm.id) await updateWebhook({ ...payload, id: webhookForm.id })
      else await createWebhook(payload)
      resetWebhookForm()
      ElMessage.success(t('openPlatform.webhookSaved'))
      loadWebhooks()
    } catch (e) {
      ElMessage.error((e as Error).message)
    } finally {
      saving.value = false
    }
  }

  async function onDeleteWebhook(row: OpenApiWebhook) {
    const confirmed = await ElMessageBox.confirm(
      t('common.confirmDelete', { name: row.eventType }),
      t('common.warning'),
      { type: 'warning' },
    ).catch(() => false)
    if (!confirmed) return
    try {
      await deleteWebhook(row.id)
      ElMessage.success(t('openPlatform.webhookDeleted'))
      loadWebhooks()
    } catch (e) {
      ElMessage.error((e as Error).message)
    }
  }

  onMounted(() => {
    loadClients()
    loadApps()
  })
</script>

<style lang="scss" scoped>
  .notice {
    margin-bottom: 12px;
  }
  .mono {
    font-family: var(--dv-font-mono, monospace);
    word-break: break-all;
  }
  .copy-row {
    display: flex;
    align-items: center;
    gap: 8px;
    width: 100%;
  }
  .hint {
    display: block;
    font-size: var(--dv-font-xs);
    line-height: 1.6;
  }
  .webhook-form {
    margin-top: 20px;
    padding-top: 16px;
    border-top: 1px solid var(--dv-border-1);
  }
</style>
