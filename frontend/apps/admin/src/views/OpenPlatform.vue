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

      <!-- ═══════════ 开放接口调用日志 ═══════════ -->
      <el-tab-pane :label="t('openPlatform.tabLogs')" name="logs">
        <p class="dv-page-desc">{{ t('openPlatform.logDesc') }}</p>

        <div class="dv-toolbar">
          <el-input
            v-model="logFilter.appKey"
            :placeholder="t('openPlatform.logAppKeyPlaceholder')"
            clearable
            style="width: 210px"
            @keyup.enter="reloadLogs"
            @clear="reloadLogs"
          />
          <el-input
            v-model="logFilter.clientId"
            :placeholder="t('openPlatform.logClientIdPlaceholder')"
            clearable
            style="width: 170px"
            @keyup.enter="reloadLogs"
            @clear="reloadLogs"
          />
          <el-select v-model="logFilter.result" style="width: 150px" @change="reloadLogs">
            <el-option
              v-for="opt in logResultSelectKeys"
              :key="opt"
              :label="logResultText(opt)"
              :value="opt"
            />
          </el-select>
          <el-date-picker
            v-model="logRange"
            type="datetimerange"
            :start-placeholder="t('openPlatform.logRangeFrom')"
            :end-placeholder="t('openPlatform.logRangeTo')"
            value-format="YYYY-MM-DDTHH:mm:ss"
            style="width: 330px"
            @change="reloadLogs"
          />
          <el-input
            v-model="logFilter.serviceCode"
            :placeholder="t('openPlatform.logServiceCodePlaceholder')"
            clearable
            style="width: 170px"
            @keyup.enter="reloadLogs"
            @clear="reloadLogs"
          />
          <el-input
            v-model="logFilter.apiPath"
            :placeholder="t('openPlatform.logApiPathPlaceholder')"
            clearable
            style="width: 190px"
            @keyup.enter="reloadLogs"
            @clear="reloadLogs"
          />
          <el-button type="primary" :loading="logsLoading" @click="reloadLogs">{{ t('common.search') }}</el-button>
          <el-button @click="onResetLogs">{{ t('common.reset') }}</el-button>
          <el-button :loading="logsLoading" @click="loadLogs">{{ t('common.refresh') }}</el-button>
        </div>

        <!-- 读不到就是读不到：后端消息原样留在这儿，不能让它只响一次就不见了 -->
        <el-alert
          v-if="logsError"
          class="notice"
          type="error"
          :closable="false"
          show-icon
          :title="t('openPlatform.logFailed')"
          :description="logsError"
        />
        <el-alert
          class="notice"
          type="info"
          :closable="false"
          show-icon
          :title="t('openPlatform.logLimitTitle')"
          :description="t('openPlatform.logLimitDesc')"
        />
        <!-- 分片读路径会把"不完整"如实报出来；这一条必须画在表上方，因为表里的行看起来完全正常 -->
        <el-alert
          v-if="logsPartialReason"
          class="notice"
          :type="logsPartialReason === 'truncated' ? 'warning' : 'info'"
          :closable="false"
          show-icon
          :title="t(logsPartialText.title, { shards: logsShards })"
          :description="t(logsPartialText.desc)"
        />

        <el-alert
          class="notice"
          type="warning"
          :closable="false"
          show-icon
          :title="t('openPlatform.logAuthFailTitle')"
          :description="t('openPlatform.logAuthFailDesc')"
        />

        <el-table :data="logs" v-loading="logsLoading" stripe>
          <el-table-column prop="createTime" :label="t('openPlatform.logTime')" width="170" fixed="left">
            <template #default="{ row }">{{ row.createTime || '—' }}</template>
          </el-table-column>
          <el-table-column :label="t('openPlatform.logCaller')" min-width="160" show-overflow-tooltip>
            <template #default="{ row }">
              <span v-if="row.appName">{{ row.appName }}</span>
              <span v-else class="dv-text-3">{{ t('openPlatform.logCallerUnattributed') }}</span>
              <span class="dv-text-3 mono"> #{{ row.clientId ?? '—' }}</span>
            </template>
          </el-table-column>
          <el-table-column :label="t('openPlatform.logPath')" min-width="250" show-overflow-tooltip>
            <template #default="{ row }">
              <code class="mono">{{ row.method || '?' }} {{ row.apiPath || '—' }}</code>
            </template>
          </el-table-column>
          <el-table-column :label="t('openPlatform.logStatus')" width="110" align="center">
            <template #default="{ row }">
              <el-tag :type="logResultTag(logResultKeyOf(row))" size="small" disable-transitions>
                {{ row.responseCode ?? t('openPlatform.logStatusUnknown') }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column :label="t('openPlatform.logResult')" width="120" align="center">
            <!-- 状态码分类是这里能给的最细的"失败原因"：openapi_log 没有 message/error 列 -->
            <template #default="{ row }">
              <span class="dv-text-3">{{ logResultText(logResultKeyOf(row)) }}</span>
            </template>
          </el-table-column>
          <el-table-column :label="t('openPlatform.logLatency')" width="100" align="center">
            <template #default="{ row }">
              {{ row.executionTime == null ? t('openPlatform.logLatencyUnknown') : `${row.executionTime} ms` }}
            </template>
          </el-table-column>
          <el-table-column :label="t('openPlatform.logIp')" width="140">
            <template #default="{ row }"><span class="mono">{{ row.ip || '—' }}</span></template>
          </el-table-column>
          <template #empty>
            <span class="dv-text-3">{{ logsError ? t('openPlatform.logFailed') : t('openPlatform.logEmpty') }}</span>
          </template>
        </el-table>
        <el-pagination
          class="dv-pager"
          layout="total, sizes, prev, pager, next"
          :total="logsTotal"
          :current-page="logPageNum"
          :page-size="logPageSize"
          @current-change="(p: number) => { logPageNum = p; loadLogs() }"
          @size-change="(s: number) => { logPageSize = s; logPageNum = 1; loadLogs() }"
        />
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
  import { computed, onMounted, reactive, ref, watch } from 'vue'
  import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
  import type {
    OpenApiClient,
    OpenApiApp,
    OpenApiKeyResult,
    OpenApiLog,
    OpenApiLogPageQuery,
    OpenApiLogResult,
    OpenApiWebhook,
  } from '@dataviz/shared-types'
  import { rowsOf, shardsOf, totalOf } from '@dataviz/shared-types'
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
    pageOpenApiLogs,
    regenerateAppSecret,
    resetClientSecret,
    updateWebhook,
  } from '@dataviz/api-client'
  import { useI18n } from 'vue-i18n'

  const { t } = useI18n()

  const tab = ref<'clients' | 'apps' | 'logs'>('clients')
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

  // ─────────── 调用日志（读 openapi_log，GET /openapi/log/page） ───────────
  const logs = ref<OpenApiLog[]>([])
  const logsTotal = ref(0)
  const logsLoading = ref(false)
  /**
   * 分片读路径的三个可选读数。"后端没说"与"后端说完整"要分得开：
   * 用 null 而不是 false 起头，否则一个不分片的接口会被画成"结果完整"这种没根据的承诺。
   */
  const logsTruncated = ref<boolean | null>(null)
  const logsWindowDefaulted = ref<boolean | null>(null)
  const logsShards = ref(0)
  /** 上一次的失败消息，留在页面上：只弹一次 toast 的话，运维会把"读不到"当成"没有调用" */
  const logsError = ref('')
  const logPageNum = ref(1)
  const logPageSize = ref(20)
  const logRange = ref<string[] | null>(null)
  const logsLoadedOnce = ref(false)
  const logFilter = reactive<{
    appKey: string
    clientId: string
    result: OpenApiLogResult | 'ALL'
    serviceCode: string
    apiPath: string
  }>({ appKey: '', clientId: '', result: 'ALL', serviceCode: '', apiPath: '' })
  const logResultSelectKeys: Array<OpenApiLogResult | 'ALL'> = [
    'ALL',
    'SUCCESS',
    'FAIL',
    'CLIENT_ERROR',
    'SERVER_ERROR',
    'OTHER',
  ]

  function logResultText(key: string): string {
    const i18nKey = `openPlatform.logResultOptions.${key}`
    const text = t(i18nKey)
    return text === i18nKey ? key : text
  }

  /** 分类判据 = 后端 result 过滤的同一套区间（SUCCESS 按 2xx），两边不同口径的话筛出来的和显示的对不上 */
  function logResultKeyOf(row: OpenApiLog): OpenApiLogResult {
    const code = row.responseCode
    if (code == null) return 'OTHER'
    if (code >= 200 && code < 300) return 'SUCCESS'
    if (code >= 400 && code < 500) return 'CLIENT_ERROR'
    if (code >= 500 && code < 600) return 'SERVER_ERROR'
    return 'OTHER'
  }

  function logResultTag(key: OpenApiLogResult): 'success' | 'warning' | 'danger' | 'info' {
    if (key === 'SUCCESS') return 'success'
    if (key === 'CLIENT_ERROR') return 'warning'
    if (key === 'SERVER_ERROR') return 'danger'
    return 'info'
  }

  /**
   * 表格上方的"这一页不是全部"是哪一种：
   * truncated（扫到分片上限被截）优先于 windowDefaulted（服务端替我补了时间窗），
   * 因为前者意味着连默认窗口内都没查完，后者只是范围比用户以为的小。
   */
  const logsPartialReason = computed<'truncated' | 'window' | null>(() => {
    if (logsTruncated.value === true) return 'truncated'
    if (logsWindowDefaulted.value === true) return 'window'
    return null
  })

  /** 键名走普通字符串而不是拼出来的点号路径：vue-i18n 的扁平键里带 `.` 不会被当路径解析。 */
  const logsPartialText = computed(() =>
    logsPartialReason.value === 'truncated'
      ? { title: 'openPlatform.logPartialTruncatedTitle', desc: 'openPlatform.logPartialTruncatedDesc' }
      : { title: 'openPlatform.logPartialWindowTitle', desc: 'openPlatform.logPartialWindowDesc' })

  function logQuery(): OpenApiLogPageQuery {
    const range = logRange.value
    return {
      pageNum: logPageNum.value,
      pageSize: logPageSize.value,
      appKey: logFilter.appKey.trim() || undefined,
      clientId: logFilter.clientId.trim() || undefined,
      result: logFilter.result === 'ALL' ? undefined : logFilter.result,
      serviceCode: logFilter.serviceCode.trim() || undefined,
      apiPath: logFilter.apiPath.trim() || undefined,
      startTime: range?.[0] || undefined,
      endTime: range?.[1] || undefined,
    }
  }

  async function loadLogs() {
    logsLoading.value = true
    logsError.value = ''
    try {
      const page = await pageOpenApiLogs(logQuery())
      logs.value = rowsOf(page)
      logsTotal.value = totalOf(page)
      logsTruncated.value = page.truncated ?? null
      logsWindowDefaulted.value = page.windowDefaulted ?? null
      logsShards.value = shardsOf(page)
    } catch (e) {
      // 后端 message 原样显示，不塌成"加载失败"三个字，也不静默给一张空表
      logsError.value = (e as Error).message
      logs.value = []
      logsTotal.value = 0
      logsTruncated.value = null
      logsWindowDefaulted.value = null
      logsShards.value = 0
      ElMessage.error(logsError.value)
    } finally {
      logsLoading.value = false
    }
  }

  function reloadLogs() {
    logPageNum.value = 1
    loadLogs()
  }

  function onResetLogs() {
    logFilter.appKey = ''
    logFilter.clientId = ''
    logFilter.result = 'ALL'
    logFilter.serviceCode = ''
    logFilter.apiPath = ''
    logRange.value = null
    reloadLogs()
  }

  // 只在第一次进这个页签时打一次：日志页是排查用的，不该为另外两个页签的打开陪跑一次全表分页
  watch(tab, (value) => {
    if (value === 'logs' && !logsLoadedOnce.value) {
      logsLoadedOnce.value = true
      loadLogs()
    }
  })

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
