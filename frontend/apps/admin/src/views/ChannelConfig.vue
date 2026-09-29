<template>
  <div class="dv-page">
    <div class="dv-page-head">
      <div>
        <h2>{{ t('channel.title') }}</h2>
        <p class="dv-page-desc">{{ t('channel.desc') }}</p>
      </div>
      <div class="dv-page-actions">
        <el-button v-permission="'alert:write'" type="primary" :disabled="!schemas.length" @click="openCreate">
          {{ t('channel.create') }}
        </el-button>
      </div>
    </div>

    <!-- 表单字段由后端 /channel/schema 导出：这里没有可用渠道 == 服务端一个 notifier 都没注册，
         此时"新增"是死按钮，必须说清楚而不是留个空表单 -->
    <el-alert
      v-if="!loadingSchema && !schemas.length"
      type="warning"
      :closable="false"
      show-icon
      class="schema-alert"
      :title="t('channel.noSchema')"
    />

    <div class="dv-toolbar">
      <el-input
        v-model="keyword"
        :placeholder="t('common.keywordPlaceholder')"
        clearable
        style="width: 220px"
        @keyup.enter="reload"
        @clear="reload"
      />
      <el-select v-model="typeFilter" :placeholder="t('channel.channelType')" clearable style="width: 190px" @change="reload">
        <el-option v-for="schema in schemas" :key="schema.type" :label="typeLabel(schema.type)" :value="schema.type" />
      </el-select>
      <el-button type="primary" :loading="loading" @click="reload">{{ t('common.search') }}</el-button>
      <el-button @click="onReset">{{ t('common.reset') }}</el-button>
    </div>

    <el-card shadow="never">
      <el-table :data="list" v-loading="loading" stripe>
        <el-table-column prop="name" :label="t('channel.channelName')" min-width="170" show-overflow-tooltip />
        <el-table-column :label="t('channel.channelType')" width="210">
          <template #default="{ row }">
            <div class="type-cell">
              <span class="type-name">{{ typeLabel(row.type) }}</span>
              <code class="dv-mono type-code">{{ row.type }}</code>
              <el-tooltip v-if="!row.deliverable" :content="t('channel.notDeliverableHint')" placement="top">
                <el-tag type="warning" size="small" disable-transitions>{{ t('channel.notDeliverable') }}</el-tag>
              </el-tooltip>
            </div>
          </template>
        </el-table-column>
        <el-table-column :label="t('channel.configSummary')" min-width="180">
          <template #default="{ row }">
            <span v-if="!configuredKeys(row).length" class="dv-text-3">{{ t('channel.emptyConfig') }}</span>
            <code v-else class="dv-mono keys" :title="configuredKeys(row).join(', ')">
              {{ configuredKeys(row).join(', ') }}
            </code>
          </template>
        </el-table-column>
        <el-table-column :label="t('common.status')" width="150">
          <template #default="{ row }">
            <el-switch
              :model-value="row.enabled"
              :disabled="!canWrite"
              :loading="togglingId === row.id"
              :active-text="t('common.enabled')"
              @change="(v: boolean) => onToggle(row, v)"
            />
            <el-tooltip v-if="row.shadowedByOtherRow" :content="t('channel.shadowHint')" placement="top">
              <el-tag type="danger" size="small" disable-transitions class="shadow-tag">
                {{ t('channel.shadowed', { id: row.shadowId }) }}
              </el-tag>
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column prop="updateTime" :label="t('common.updatedAt')" width="170" />
        <el-table-column :label="t('common.operations')" width="210" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="'alert:write'" link type="primary" @click="openEdit(row)">
              {{ t('common.edit') }}
            </el-button>
            <el-button v-permission="'alert:write'" link type="primary" @click="onTest(row)">
              {{ t('channel.test') }}
            </el-button>
            <el-button v-permission="'alert:write'" link type="danger" @click="onDelete(row)">
              {{ t('common.delete') }}
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <span class="dv-text-3">{{ t('common.empty') }}</span>
        </template>
      </el-table>
      <el-pagination
        class="dv-pager"
        layout="total, sizes, prev, pager, next"
        :total="total"
        :current-page="pageNum"
        :page-size="pageSize"
        @current-change="(p: number) => { pageNum = p; load() }"
        @size-change="(s: number) => { pageSize = s; pageNum = 1; load() }"
      />
    </el-card>

    <el-dialog v-model="dialogVisible" :title="editingId ? t('channel.editTitle') : t('channel.createTitle')" width="620px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="150px">
        <el-form-item :label="t('channel.channelName')" prop="name">
          <el-input v-model="form.name" :placeholder="t('channel.namePlaceholder')" maxlength="128" />
        </el-form-item>
        <el-form-item :label="t('channel.channelType')" prop="type">
          <el-select v-model="form.type" style="width: 100%" @change="onTypeChange">
            <el-option v-for="schema in schemas" :key="schema.type" :label="typeLabel(schema.type)" :value="schema.type" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('common.status')">
          <el-switch v-model="form.enabled" :active-text="t('common.enabled')" />
        </el-form-item>

        <el-alert
          v-if="currentSchema && !currentSchema.deliverable"
          type="warning"
          :closable="false"
          show-icon
          class="deliver-alert"
          :title="t('channel.notDeliverableHint')"
        />

        <!-- 收件人已经搬到「告警 · 通知对象」，但老配置里那几项必须继续留着才不断发（后端整表覆盖保存），
             所以这里不能把它们渲染成可编辑输入框，也不能悄悄丢掉 ⇒ 显式说明这是历史存量 -->
        <el-alert
          v-if="legacyRecipientKeysInForm.length"
          type="info"
          :closable="false"
          show-icon
          class="deliver-alert"
          :title="t('channel.legacyRecipients', { keys: legacyLabel(legacyRecipientKeysInForm) })"
        />

        <el-form-item
          v-for="field in currentFields"
          :key="field.key"
          :label="fieldLabel(field)"
          :prop="`config.${field.key}`"
          :rules="ruleFor(field)"
        >
          <!-- 口令位：读回来是 ***，原样保存表示沿用库里那份；清空保存才是撤销 -->
          <el-input
            v-if="field.secret"
            v-model="form.config[field.key]"
            type="password"
            autocomplete="new-password"
            :placeholder="form.config[field.key] === CHANNEL_SECRET_MASK ? t('channel.secretKept') : field.placeholder || ''"
          />
          <el-select
            v-else-if="field.kind === 'select'"
            v-model="form.config[field.key]"
            clearable
            style="width: 100%"
            :placeholder="field.placeholder || ''"
          >
            <el-option v-for="opt in field.options || []" :key="opt" :label="opt" :value="opt" />
          </el-select>
          <el-select
            v-else-if="field.kind === 'list'"
            v-model="form.config[field.key]"
            multiple
            filterable
            allow-create
            default-first-option
            :reserve-keyword="false"
            style="width: 100%"
            :placeholder="t('channel.listHint')"
          >
            <el-option v-for="item in listOf(field.key)" :key="item" :label="item" :value="item" />
          </el-select>
          <el-switch v-else-if="field.kind === 'switch'" v-model="form.config[field.key]" />
          <el-input
            v-else-if="field.kind === 'json' || field.kind === 'textarea'"
            v-model="form.config[field.key]"
            type="textarea"
            :rows="3"
            class="dv-mono"
            :placeholder="field.placeholder || ''"
          />
          <el-input
            v-else
            v-model="form.config[field.key]"
            :type="field.kind === 'number' ? 'number' : 'text'"
            :placeholder="field.placeholder || ''"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="saving" @click="onSubmit">{{ t('common.save') }}</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="testVisible" :title="t('channel.testTitle')" width="560px">
      <div v-if="testRow" class="test-body">
        <p class="test-line">
          <span class="test-target">{{ testRow.name }}</span>
          <code class="dv-mono type-code">{{ testRow.type }}</code>
        </p>

        <div v-if="testTargetKind !== 'none'" class="test-field">
          <span class="test-field-label">{{ t('channel.testRecipients') }}</span>
          <el-input v-model="testRecipients" :placeholder="testRecipientsPlaceholder" clearable />
          <p class="dv-text-3 test-hint">{{ t('channel.testRecipientsNote') }}</p>
        </div>

        <div v-if="testResult" class="test-result">
          <p class="test-line">
            <el-tag :type="testResult.success ? 'success' : 'danger'" size="small" disable-transitions>
              {{ testResult.success ? t('channel.testSuccess') : t('channel.testFailed') }}
            </el-tag>
            <span class="test-meta">{{ t('channel.testElapsed', { n: testResult.elapsedMs }) }}</span>
          </p>
          <p v-if="testResult.recipient">
            <span class="dv-text-3">{{ t('channel.testRecipient') }}</span>
            <code class="dv-mono">{{ testResult.recipient }}</code>
          </p>
          <!-- 收件人从哪来现在是个真问题：测试用的是通知组还是渠道里那串历史值，界面必须说清，
               否则"测试通过"证明不了实发给对的人 -->
          <p v-if="testResult.recipientSource">
            <span class="dv-text-3">{{ t('channel.testSource') }}</span>
            <span :class="sourceClass">{{ sourceLabel(testResult.recipientSource) }}</span>
          </p>
          <p v-if="testResult.error" class="test-error">{{ testResult.error }}</p>
        </div>
        <p class="dv-text-3 test-note">{{ t('channel.testNote') }}</p>
      </div>
      <template #footer>
        <el-button @click="testVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="testing" @click="onTestSend">{{ t('channel.testSend') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
  import { computed, onMounted, reactive, ref } from 'vue'
  import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
  import type { ChannelField, ChannelSchema, NotifyChannelConfig, NotifyChannelPayload, ChannelTestResult } from '@dataviz/shared-types'
  import { legacyRecipientKeys, rowsOf, totalOf } from '@dataviz/shared-types'
  import {
    CHANNEL_SECRET_MASK,
    createNotifyChannel,
    deleteNotifyChannel,
    listChannelSchemas,
    pageNotifyChannels,
    testNotifyChannel,
    toggleNotifyChannel,
    updateNotifyChannel,
  } from '@dataviz/api-client'
  import { useI18n } from 'vue-i18n'
  import { hasSessionPermission } from '@dataviz/permission'

  const { t, te } = useI18n()
  // 开关既是状态也是写操作，非写权限要能看见当前状态，所以走 :disabled 而不是藏起来
  const canWrite = hasSessionPermission('alert:write')

  const loadingSchema = ref(true)
  const loading = ref(false)
  const saving = ref(false)
  const schemas = ref<ChannelSchema[]>([])
  const list = ref<NotifyChannelConfig[]>([])
  const total = ref(0)
  const pageNum = ref(1)
  const pageSize = ref(20)
  const keyword = ref('')
  const typeFilter = ref('')
  const togglingId = ref<string | number>('')
  const testingId = ref<string | number>('')

  const dialogVisible = ref(false)
  const editingId = ref<string | number>('')
  const formRef = ref<FormInstance>()
  const form = reactive<{ name: string; type: string; enabled: boolean; config: Record<string, any> }>({
    name: '',
    type: '',
    enabled: true,
    config: {},
  })

  const testVisible = ref(false)
  const testResult = ref<ChannelTestResult | null>(null)
  const testRow = ref<NotifyChannelConfig | null>(null)
  const testRecipients = ref('')
  const testing = ref(false)

  const rules = computed<FormRules>(() => ({
    name: [{ required: true, message: t('channel.nameRequired'), trigger: 'blur' }],
    type: [{ required: true, message: t('channel.typeRequired'), trigger: 'change' }],
  }))

  /** 后端注册表里的渠道类型码 → 展示名；没有译文就显示原始大写码（新渠道可以先不上线文案） */
  function typeLabel(type: string): string {
    const key = `channel.typeName.${String(type || '').toLowerCase()}`
    return te(key) ? t(key) : String(type || '')
  }

  /** 后端 ChannelField.labelKey（形如 channel.field.smtp）→ 展示名；缺译文时退回字段键名 */
  function fieldLabel(field: ChannelField): string {
    return te(field.labelKey) ? t(field.labelKey) : field.key
  }

  const currentSchema = computed(() => schemas.value.find((s) => s.type === form.type))
  const currentFields = computed(() => currentSchema.value?.fields ?? [])

  /**
   * 老配置里那几项收件人（to/receivers/mobiles/atMobiles）：后端仍然保留它们，
   * 规则没挂通知组时还会拿它们兜底，所以表单必须原样带回去 —— 但不能再当可编辑项渲染，
   * 否则配置页就成了第二个"改收件人的地方"，而它恰恰是这次要废掉的那个。
   */
  const legacyRecipientKeysInForm = computed(() => legacyRecipientKeys(form.config))

  function legacyLabel(keys: string[]): string {
    return keys.map((key) => (te(`channel.field.${key}`) ? t(`channel.field.${key}`) : key)).join('、')
  }

  const testTargetKind = computed(() => {
    const schema = schemas.value.find((s) => s.type === testRow.value?.type)
    return schema ? String(schema.targetKind ?? 'none') : 'none'
  })

  const testRecipientsPlaceholder = computed(() =>
    testTargetKind.value === 'email' ? t('channel.testRecipientsEmail') : t('channel.testRecipientsMobile'),
  )

  function sourceLabel(source?: string): string {
    const key = `channel.testSourceKind.${String(source ?? '').toLowerCase().replace(/_([a-z])/g, (m, c) => c.toLowerCase())}`
    return te(key) ? t(key) : String(source ?? '')
  }

  /** 走的是渠道存量收件人 ⇒ 这条规则还没搬到通知对象上，颜色要说这件事 */
  const sourceClass = computed(() => (testResult.value?.recipientSource === 'CHANNEL_CONFIG' ? 'test-source-legacy' : ''))

  function blankConfig(fields: ChannelField[]): Record<string, any> {
    const config: Record<string, any> = {}
    for (const field of fields) {
      if (field.kind === 'switch') {
        config[field.key] = field.defaultValue === 'true'
      } else if (field.kind === 'list') {
        config[field.key] = []
      } else {
        config[field.key] = field.secret ? '' : (field.defaultValue ?? '')
      }
    }
    return config
  }

  /** 库里的值 → 表单控件的值（列表归一成数组、JSON 归一成文本、开关归一成布尔） */
  function toFormValue(field: ChannelField, stored: unknown): unknown {
    if (field.kind === 'switch') {
      return stored === true || stored === 'true' || stored === 1
    }
    if (field.kind === 'list') {
      if (Array.isArray(stored)) {
        return stored.map((item) => String(item))
      }
      if (stored == null || String(stored).trim() === '') {
        return []
      }
      return String(stored)
        .split(/[,;\s\n]+/)
        .map((item) => item.trim())
        .filter(Boolean)
    }
    if (field.kind === 'json') {
      if (stored == null) return ''
      if (typeof stored === 'string') return stored
      return JSON.stringify(stored, null, 2)
    }
    if (stored == null) return field.secret ? '' : (field.defaultValue ?? '')
    return String(stored)
  }

  function configuredKeys(row: NotifyChannelConfig): string[] {
    // 只列键名不列值：这一列在列表页，把 webhook 全文贴出来等于把 token 摊进每一屏
    return Object.keys(row.config ?? {}).filter((key) => {
      const value = row.config[key]
      return !(value === null || value === undefined || value === '' || (Array.isArray(value) && !value.length))
    })
  }

  function listOf(key: string): string[] {
    const value = form.config[key]
    return Array.isArray(value) ? (value as string[]) : []
  }

  /**
   * 必填判据在前端也做一遍（后端同样会判）：动态表单里"这条没填"必须是输入框下面的红字，
   * 而不是提交之后一条服务端消息 —— 用户填 8 个字段，不该为了一个漏填走一趟网络。
   */
  function ruleFor(field: ChannelField) {
    if (!field.required) return undefined
    const label = fieldLabel(field)
    if (field.kind === 'list') {
      return [{ required: true, type: 'array' as const, message: t('channel.fieldRequired', { label }), trigger: 'change' }]
    }
    if (field.kind === 'switch') return undefined
    return [{ required: true, message: t('channel.fieldRequired', { label }), trigger: 'blur' }]
  }

  async function loadSchema() {
    loadingSchema.value = true
    try {
      schemas.value = await listChannelSchemas()
    } catch (e) {
      ElMessage.error((e as Error).message)
      schemas.value = []
    } finally {
      loadingSchema.value = false
    }
  }

  async function load() {
    loading.value = true
    try {
      const res = await pageNotifyChannels({
        pageNum: pageNum.value,
        pageSize: pageSize.value,
        keyword: keyword.value || undefined,
        type: typeFilter.value || undefined,
      })
      list.value = rowsOf(res)
      total.value = totalOf(res)
    } catch (e) {
      ElMessage.error((e as Error).message)
    } finally {
      loading.value = false
    }
  }

  function reload() {
    pageNum.value = 1
    load()
  }

  function onReset() {
    keyword.value = ''
    typeFilter.value = ''
    reload()
  }

  function openCreate() {
    editingId.value = ''
    const first = schemas.value[0]
    form.name = ''
    form.type = first ? first.type : ''
    form.enabled = true
    form.config = blankConfig(first ? first.fields : [])
    dialogVisible.value = true
  }

  function openEdit(row: NotifyChannelConfig) {
    const schema = schemas.value.find((s) => s.type === row.type)
    const stored = row.config ?? {}
    editingId.value = row.id
    form.name = row.name
    form.type = row.type
    form.enabled = !!row.enabled
    // 库里可能有渠道声明之外的键（手写 SQL 补的、或换过类型的），一并带上：配置页不该有隐形删数据的能力
    const config = blankConfig(schema ? schema.fields : [])
    for (const key of Object.keys(stored)) {
      const field = schema?.fields.find((f) => f.key === key)
      config[key] = field ? toFormValue(field, stored[key]) : stored[key]
    }
    for (const field of schema ? schema.fields : []) {
      if (!(field.key in stored)) {
        config[field.key] = blankConfig([field])[field.key]
      }
    }
    form.config = config
    dialogVisible.value = true
  }

  function onTypeChange(type: string) {
    const schema = schemas.value.find((s) => s.type === type)
    const fields = schema ? schema.fields : []
    const next = blankConfig(fields)
    // 换类型时同名键的值留住（比如 mobiles 在钉钉和企业微信是同一个意思），用户不该被要求重填
    for (const field of fields) {
      const existing = form.config[field.key]
      if (existing !== undefined && existing !== '' && !(field.secret && existing === CHANNEL_SECRET_MASK)) {
        next[field.key] = existing
      }
    }
    form.config = next
  }

  function payload(): NotifyChannelPayload {
    // 空串/空数组不提交（后端把它们当成"清空这一项"，多余的空键只会让配置越来越难读）
    const config: Record<string, unknown> = {}
    for (const [key, value] of Object.entries(form.config)) {
      if (value === undefined || value === null) continue
      if (value === '' && !isSecretKey(key)) {
        continue
      }
      if (Array.isArray(value) && !value.length) continue
      config[key] = value
    }
    return {
      id: editingId.value || undefined,
      name: form.name.trim(),
      type: form.type,
      enabled: form.enabled,
      config,
    }
  }

  function isSecretKey(key: string): boolean {
    const field = currentFields.value.find((f) => f.key === key)
    return !!field?.secret
  }

  async function onSubmit() {
    const valid = await formRef.value?.validate().catch(() => false)
    if (!valid) return
    saving.value = true
    try {
      if (editingId.value) await updateNotifyChannel(payload())
      else await createNotifyChannel(payload())
      ElMessage.success(t('channel.saved'))
      dialogVisible.value = false
      load()
    } catch (e) {
      ElMessage.error((e as Error).message)
    } finally {
      saving.value = false
    }
  }

  async function onToggle(row: NotifyChannelConfig, enabled: boolean) {
    togglingId.value = row.id
    try {
      await toggleNotifyChannel(row.id, enabled)
    } catch (e) {
      ElMessage.error((e as Error).message)
    } finally {
      togglingId.value = ''
      // 无论成败都重取：失败时开关必须弹回库里的真实状态，后端在这里是有拒绝权的
      // （停用某类型最后一条启用配置、而还有规则在用它 ⇒ 400）
      load()
    }
  }

  async function onDelete(row: NotifyChannelConfig) {
    const confirmed = await ElMessageBox.confirm(
      t('common.confirmDelete', { name: row.name }),
      t('common.warning'),
      { type: 'warning' },
    ).catch(() => false)
    if (!confirmed) return
    try {
      await deleteNotifyChannel(row.id)
      ElMessage.success(t('channel.deleted'))
      load()
    } catch (e) {
      ElMessage.error((e as Error).message)
    }
  }

  /**
   * 点「测试发送」只开窗，不发东西：收件人现在可能是要临时填的，
   * 而且误点一下就该只是一次点击，不是一次真发到别人邮箱里的请求。
   */
  function onTest(row: NotifyChannelConfig) {
    testRow.value = row
    testResult.value = null
    testRecipients.value = ''
    testVisible.value = true
  }

  async function onTestSend() {
    if (!testRow.value) return
    testing.value = true
    try {
      testResult.value = await testNotifyChannel(testRow.value.id, testRecipients.value)
    } catch (e) {
      // 发送失败服务层是当结果回的；走到这里的是网络/权限那一层，要照实报错而不是伪装成"未送达"
      testResult.value = null
      ElMessage.error((e as Error).message)
    } finally {
      testing.value = false
    }
  }

  onMounted(async () => {
    await loadSchema()
    load()
  })
</script>

<style lang="scss" scoped>
  .schema-alert,
  .deliver-alert {
    margin-bottom: var(--dv-space-md);
  }
  .type-cell {
    display: flex;
    align-items: center;
    gap: 6px;
    flex-wrap: wrap;
  }
  .type-name {
    font-weight: 600;
  }
  .type-code,
  .keys {
    font-size: var(--dv-font-xs);
    color: var(--dv-text-3);
  }
  .shadow-tag {
    margin-top: 4px;
  }
  .test-body p {
    margin: 0 0 8px;
  }
  .test-line {
    display: flex;
    align-items: center;
    gap: 10px;
  }
  .test-meta {
    color: var(--dv-text-3);
    font-size: var(--dv-font-sm);
  }
  .test-target {
    font-weight: 600;
  }
  .test-field {
    margin-bottom: var(--dv-space-md);
  }
  .test-field-label {
    display: block;
    margin-bottom: 4px;
    font-size: var(--dv-font-sm);
    color: var(--dv-text-2);
  }
  .test-hint {
    margin: 4px 0 0;
    font-size: var(--dv-font-xs);
    line-height: 1.6;
  }
  .test-result {
    padding: var(--dv-space-sm) var(--dv-space-md);
    border-radius: var(--dv-radius-md, 6px);
    background: var(--dv-fill-light, #f7f8fa);
  }
  /* 走的是渠道里那串历史收件人：这一条规则还没搬到通知对象，颜色要把它单挑出来 */
  .test-source-legacy {
    color: var(--dv-warning, #e6a23c);
    font-weight: 600;
  }
  .test-error {
    white-space: pre-wrap;
    color: var(--dv-danger, #f56c6c);
  }
  .test-note {
    font-size: var(--dv-font-sm);
    line-height: 1.6;
  }
</style>
