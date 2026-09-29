<template>
  <div class="dv-page">
    <div class="dv-page-head">
      <div>
        <h2>{{ t('alert.title') }}</h2>
        <p class="dv-page-desc">{{ t('alert.desc') }}</p>
      </div>
      <div class="dv-page-actions">
        <el-button @click="loadData">{{ t('common.refresh') }}</el-button>
        <el-button type="primary" @click="handleCreate">
          <el-icon><Plus /></el-icon>
          {{ t('alert.createRule') }}
        </el-button>
      </div>
    </div>

    <div class="dv-toolbar">
      <el-input
        v-model="keyword"
        :placeholder="t('common.keywordPlaceholder')"
        clearable
        style="width: 220px"
        @keyup.enter="reload"
        @clear="reload"
      />
      <el-select v-model="levelFilter" :placeholder="t('alert.level')" clearable style="width: 140px" @change="reload">
        <el-option v-for="lv in LEVELS" :key="lv" :label="levelLabel(lv)" :value="lv" />
      </el-select>
      <el-button type="primary" :loading="loading" @click="reload">{{ t('common.search') }}</el-button>
      <el-button @click="onReset">{{ t('common.reset') }}</el-button>
    </div>

    <el-card shadow="never">
      <el-table :data="list" v-loading="loading" stripe>
        <el-table-column prop="name" :label="t('alert.ruleName')" min-width="170" show-overflow-tooltip />
        <el-table-column :label="t('alert.level')" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="levelTag(row.level)" size="small" disable-transitions>{{ levelLabel(row.level) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('alert.condition')" min-width="230">
          <template #default="{ row }">
            <code class="dv-mono cond">{{ conditionText(row) }}</code>
          </template>
        </el-table-column>
        <el-table-column :label="t('alert.notifyTo')" min-width="230">
          <template #default="{ row }">
            <div class="notify-cell">
              <span v-if="!channelsOf(row).length" class="dv-text-3">{{ t('alert.noChannels') }}</span>
              <el-tag v-for="c in channelsOf(row)" :key="c" size="small" type="info" disable-transitions>
                {{ channelLabel(c) }}
              </el-tag>
              <span v-if="!groupsOf(row).length" class="dv-text-3">{{ t('alert.noGroups') }}</span>
              <el-tag v-for="g in groupsOf(row)" :key="g.id" size="small" disable-transitions>
                {{ g.name }}
              </el-tag>
              <el-tooltip v-if="row.notify && row.notify.targetsEmpty" :content="t('alert.noTargetsHint')" placement="top">
                <el-tag type="warning" size="small" disable-transitions>{{ t('alert.noTargets') }}</el-tag>
              </el-tooltip>
            </div>
          </template>
        </el-table-column>
        <el-table-column :label="t('common.status')" width="90" align="center">
          <template #default="{ row }">
            <el-switch
              :model-value="row.status === 'enabled'"
              :loading="togglingId === row.id"
              @change="(v: boolean) => handleToggle(row, v)"
            />
          </template>
        </el-table-column>
        <el-table-column prop="lastFiredAt" :label="t('alert.lastFired')" width="170">
          <template #default="{ row }">{{ row.lastFiredAt || '-' }}</template>
        </el-table-column>
        <el-table-column :label="t('common.operations')" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleEdit(row)">{{ t('common.edit') }}</el-button>
            <el-button link type="primary" :loading="testingId === row.id" @click="handleTest(row)">
              {{ t('alert.testRun') }}
            </el-button>
            <el-button link type="danger" @click="handleDelete(row)">{{ t('common.delete') }}</el-button>
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
        @current-change="(p: number) => { pageNum = p; loadData() }"
        @size-change="(s: number) => { pageSize = s; pageNum = 1; loadData() }"
      />
    </el-card>

    <el-dialog
      v-model="dialogVisible"
      :title="editingId ? t('alert.editTitle') : t('alert.createTitle')"
      width="700px"
      top="6vh"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-form-item :label="t('alert.ruleName')" prop="name">
          <el-input v-model="form.name" :placeholder="t('alert.namePlaceholder')" maxlength="128" show-word-limit />
        </el-form-item>
        <el-form-item :label="t('alert.description')" prop="description">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="2"
            maxlength="512"
            show-word-limit
            :placeholder="t('alert.descriptionPlaceholder')"
          />
        </el-form-item>
        <el-form-item :label="t('alert.level')">
          <el-radio-group v-model="form.level">
            <el-radio-button v-for="lv in LEVELS" :key="lv" :value="lv">{{ levelLabel(lv) }}</el-radio-button>
          </el-radio-group>
        </el-form-item>

        <el-form-item :label="t('alert.conditionType')">
          <el-radio-group v-model="form.conditionType">
            <el-radio-button value="threshold">{{ t('alert.typeThreshold') }}</el-radio-button>
            <el-radio-button value="rate">{{ t('alert.typeRate') }}</el-radio-button>
            <el-radio-button value="trend">{{ t('alert.typeTrend') }}</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-alert
          v-if="form.conditionType !== 'threshold'"
          type="warning"
          :closable="false"
          show-icon
          class="form-alert"
          :title="t('alert.typeNotEvaluated')"
        />

        <el-form-item :label="t('alert.datasource')" prop="datasourceId">
          <el-select v-model="form.datasourceId" filterable clearable style="width: 100%" :loading="loadingSources">
            <el-option v-for="d in datasources" :key="d.id" :label="d.name" :value="String(d.id)">
              <span>{{ d.name }}</span>
              <span class="opt-addr dv-mono">{{ d.type }}</span>
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item :label="t('alert.metric')" prop="metric">
          <el-input
            v-model="form.metric"
            type="textarea"
            :rows="3"
            class="dv-mono"
            :placeholder="t('alert.metricPlaceholder')"
          />
          <p class="dv-text-3 hint">{{ t('alert.metricHint') }}</p>
        </el-form-item>
        <el-form-item :label="t('alert.condition')" prop="threshold">
          <div class="cond-row">
            <el-select v-model="form.operator" style="width: 110px">
              <el-option v-for="op in OPERATORS" :key="op" :label="op" :value="op" />
            </el-select>
            <el-input-number
              v-model="form.threshold"
              controls-position="right"
              class="cond-value"
              :placeholder="t('alert.threshold')"
            />
            <el-input-number v-model="form.duration" :min="0" :step="30" controls-position="right" class="cond-value" />
            <span class="dv-text-3">{{ t('alert.durationUnit') }}</span>
          </div>
          <p class="dv-text-3 hint">{{ t('alert.durationHint') }}</p>
        </el-form-item>

        <el-divider content-position="left">{{ t('alert.notifySection') }}</el-divider>

        <el-form-item :label="t('alert.notifyChannels')">
          <el-select v-model="form.channels" multiple style="width: 100%">
            <el-option v-for="s in schemas" :key="s.type" :label="channelLabel(s.type)" :value="s.type.toLowerCase()">
              <span>{{ channelLabel(s.type) }}</span>
              <el-tag v-if="!s.deliverable" type="warning" size="small" disable-transitions class="opt-tag">
                {{ t('alert.notDeliverable') }}
              </el-tag>
            </el-option>
          </el-select>
          <p v-if="!form.channels.length" class="dv-text-3 hint">{{ t('alert.noChannelsHint') }}</p>
        </el-form-item>

        <el-form-item :label="t('alert.notifyGroups')">
          <el-select v-model="form.groupIds" multiple filterable style="width: 100%" :loading="loadingGroups">
            <el-option v-for="g in groupOptions" :key="g.id" :label="g.name" :value="String(g.id)">
              <span>{{ g.name }}</span>
              <span class="opt-addr dv-mono">{{ t('alert.groupCounts', { m: g.memberCount, e: g.emailCount, n: g.mobileCount }) }}</span>
              <el-tag v-if="!g.enabled" type="info" size="small" disable-transitions class="opt-tag">
                {{ t('common.disabled') }}
              </el-tag>
            </el-option>
          </el-select>
          <!-- "这条告警到底发得出去吗"必须在勾选的当场说清楚：渠道要的是邮箱还是手机号，
               决定了这一组算不算空，等告警真触发那天才发现名单没用就晚了 -->
          <p v-for="w in groupWarnings" :key="w" class="hint warn">{{ w }}</p>
          <p v-if="!form.groupIds.length" class="dv-text-3 hint">{{ t('alert.noGroupsHint') }}</p>
        </el-form-item>

        <el-form-item :label="t('common.status')">
          <el-switch v-model="form.enabled" :active-text="t('common.enabled')" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">{{ t('common.save') }}</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="testVisible" :title="t('alert.testTitle')" width="480px">
      <div v-if="testResult" class="test-body">
        <p class="test-line">
          <el-tag :type="testResult.triggered ? 'danger' : 'success'" size="small" disable-transitions>
            {{ testResult.triggered ? t('alert.testTriggered') : t('alert.testNotTriggered') }}
          </el-tag>
          <span class="dv-mono">{{ t('alert.testValue', { v: testResult.value === null ? '-' : testResult.value }) }}</span>
        </p>
        <p v-if="testResult.note" class="dv-text-3">{{ testResult.note }}</p>
        <p class="dv-text-3 hint">{{ t('alert.testNote') }}</p>
      </div>
      <template #footer>
        <el-button type="primary" @click="testVisible = false">{{ t('common.confirm') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
  import { computed, nextTick, onMounted, reactive, ref } from 'vue'
  import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
  import { Plus } from '@element-plus/icons-vue'
  import { useI18n } from 'vue-i18n'
  import type { AlertCondition, AlertLevel, AlertRule, ChannelSchema, NotifyChannel, NotifyGroupOption } from '@dataviz/shared-types'
  import { rowsOf, totalOf } from '@dataviz/shared-types'
  import {
    createAlertRule,
    deleteAlertRule,
    getAlertRule,
    listAlertRules,
    listChannelSchemas,
    listDatasources,
    listNotifyGroupOptions,
    testAlertRule,
    type AlertRuleTestResult,
    toggleAlertRule,
    updateAlertRule,
  } from '@dataviz/api-client'

  const { t, te } = useI18n()

  const LEVELS: AlertLevel[] = ['info', 'warning', 'critical']
  const OPERATORS: AlertCondition['operator'][] = ['>', '>=', '<', '<=', '=', '!=']

  const loading = ref(false)
  const loadingSources = ref(false)
  const loadingGroups = ref(false)
  const saving = ref(false)
  const list = ref<AlertRule[]>([])
  const total = ref(0)
  const pageNum = ref(1)
  const pageSize = ref(20)
  const keyword = ref('')
  const levelFilter = ref('')
  const togglingId = ref<string | number>('')
  const testingId = ref<string | number>('')

  const schemas = ref<ChannelSchema[]>([])
  const datasources = ref<{ id: string | number; name: string; type: string }[]>([])
  const groupOptions = ref<NotifyGroupOption[]>([])

  const dialogVisible = ref(false)
  const editingId = ref<string | number>('')
  /** 打开弹窗时的启停状态，只在真的被改动后才多调一次启停接口 */
  const enabledAtOpen = ref(true)
  const formRef = ref<FormInstance>()
  const form = reactive({
    name: '',
    description: '',
    level: 'warning' as AlertLevel,
    conditionType: 'threshold' as AlertCondition['type'],
    datasourceId: '',
    metric: '',
    operator: '>' as AlertCondition['operator'],
    threshold: undefined as number | undefined,
    duration: 0,
    channels: [] as string[],
    groupIds: [] as string[],
    enabled: true,
  })

  const testVisible = ref(false)
  const testResult = ref<AlertRuleTestResult | null>(null)

  /**
   * 必填判据跟着"这条规则到底会不会被执行"走：只有 THRESHOLD 会被调度器取数判定，
   * 所以数据源/指标 SQL 也只在这一支是必填 —— 存 DERIVATIVE 草稿时挡人在半路上，
   * 只会让人学会"先建一条阈值型再改回来"这种没有意义的绕法。
   */
  const needsQuery = computed(() => form.conditionType === 'threshold')

  const rules = computed<FormRules>(() => ({
    name: [{ required: true, message: t('alert.nameRequired'), trigger: 'blur' }],
    datasourceId: [{ required: needsQuery.value, message: t('alert.datasourceRequired'), trigger: 'change' }],
    metric: [{ required: needsQuery.value, message: t('alert.metricRequired'), trigger: 'blur' }],
    // 阈值不填会被后端在保存时挡回来（它照抄了判定器的前四道检查），但"填了 0"和"没填"
    // 在这里是两件事：让它们在界面长成两个样子，而不是都变成一个 0 存进去再比对
    threshold: [{ required: needsQuery.value, type: 'number', message: t('alert.thresholdRequired'), trigger: 'change' }],
  }))

  /** 渠道类型码 → 这条渠道要的收件人类别（email / mobile / none），判据来自后端注册表 */
  const kindByType = computed(() => {
    const map: Record<string, string> = {}
    for (const s of schemas.value) map[s.type.toLowerCase()] = String(s.targetKind || 'none')
    return map
  })

  const needsEmail = computed(() => form.channels.some((c) => kindByType.value[c] === 'email'))
  const needsMobile = computed(() => form.channels.some((c) => kindByType.value[c] === 'mobile'))

  /** 选中组的可达数与勾选渠道对不上时逐组点名（含停用整组的，那是另一种原因） */
  const groupWarnings = computed(() => {
    if (!form.channels.length) return []
    const picked = new Set(form.groupIds)
    const out: string[] = []
    for (const g of groupOptions.value) {
      if (!picked.has(String(g.id))) continue
      if (!g.enabled) {
        out.push(t('alert.groupDisabledWarn', { name: g.name }))
        continue
      }
      const missing: string[] = []
      if (needsEmail.value && !g.emailCount) missing.push(t('alert.channelKindEmail'))
      if (needsMobile.value && !g.mobileCount) missing.push(t('alert.channelKindMobile'))
      if (missing.length) out.push(t('alert.groupNoAddress', { name: g.name, kinds: missing.join(' / ') }))
    }
    return out
  })

  function levelTag(level: string): 'danger' | 'warning' | 'info' {
    if (level === 'critical') return 'danger'
    if (level === 'warning') return 'warning'
    return 'info'
  }

  function levelLabel(level: string): string {
    if (level === 'critical') return t('alert.levelCritical')
    if (level === 'warning') return t('alert.levelWarning')
    return t('alert.levelInfo')
  }

  /** 后端加新渠道时这里不该需要改：没有译文就显示原始大写码 */
  function channelLabel(type: string): string {
    const key = `alert.channelName.${String(type || '').toLowerCase()}`
    return te(key) ? t(key) : String(type || '')
  }

  function conditionText(row: AlertRule): string {
    const c = row.condition
    if (!c) return '-'
    const head = `${row.metric || '-'} ${c.operator} ${c.value}`
    return c.durationSeconds ? `${head} · ${t('alert.durationFor', { n: c.durationSeconds })}` : head
  }

  function channelsOf(row: AlertRule): string[] {
    return (row.notify && row.notify.channels) || []
  }

  function groupsOf(row: AlertRule): NotifyGroupOption[] {
    return (row.notify && row.notify.groups) || []
  }

  async function loadData() {
    loading.value = true
    try {
      const res = await listAlertRules({
        pageNum: pageNum.value,
        pageSize: pageSize.value,
        keyword: keyword.value || undefined,
        level: levelFilter.value || undefined,
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
    loadData()
  }

  function onReset() {
    keyword.value = ''
    levelFilter.value = ''
    reload()
  }

  async function loadPickerData() {
    loadingSources.value = true
    loadingGroups.value = true
    try {
      const [sources, groups] = await Promise.all([listDatasources({ pageNum: 1, pageSize: 200 }), listNotifyGroupOptions()])
      datasources.value = rowsOf(sources).map((d) => ({ id: d.id, name: d.name, type: String(d.type) }))
      groupOptions.value = groups
    } catch (e) {
      ElMessage.error((e as Error).message)
    } finally {
      loadingSources.value = false
      loadingGroups.value = false
    }
  }

  function resetForm() {
    form.name = ''
    form.description = ''
    form.level = 'warning'
    form.conditionType = 'threshold'
    form.datasourceId = ''
    form.metric = ''
    form.operator = '>'
    form.threshold = undefined
    form.duration = 0
    form.channels = []
    form.groupIds = []
    form.enabled = true
  }

  function applyRule(rule: AlertRule) {
    form.name = rule.name ?? ''
    form.description = rule.description ?? ''
    form.level = rule.level ?? 'warning'
    form.conditionType = (rule.condition && rule.condition.type) || 'threshold'
    form.datasourceId = rule.datasetId == null ? '' : String(rule.datasetId)
    form.metric = rule.metric ?? ''
    form.operator = (rule.condition && rule.condition.operator) || '>'
    form.threshold = rule.condition && rule.condition.value != null ? Number(rule.condition.value) : undefined
    form.duration = Number((rule.condition && rule.condition.durationSeconds) || 0)
    form.channels = channelsOf(rule).slice()
    form.groupIds = groupsOf(rule).map((g) => String(g.id))
    form.enabled = rule.status === 'enabled'
  }

  async function handleCreate() {
    editingId.value = ''
    resetForm()
    enabledAtOpen.value = true
    dialogVisible.value = true
    loadPickerData()
    nextTick(() => formRef.value?.clearValidate())
  }

  /**
   * 编辑走一次详情而不是直接铺列表行：列表页可能因为分页/后端裁剪缺少后补的字段，
   * 而这份表单是<b>整条规则的覆盖写</b>，少读到一个组挂载关系就会在保存时把它冲掉。
   */
  async function handleEdit(row: AlertRule) {
    try {
      const rule = await getAlertRule(row.id)
      editingId.value = row.id
      applyRule(rule)
      enabledAtOpen.value = rule.status === 'enabled'
      dialogVisible.value = true
      loadPickerData()
      nextTick(() => formRef.value?.clearValidate())
    } catch (e) {
      ElMessage.error((e as Error).message)
    }
  }

  function payload(): Partial<AlertRule> {
    return {
      name: form.name.trim(),
      description: form.description.trim() || undefined,
      level: form.level,
      datasetId: form.datasourceId ? Number(form.datasourceId) : undefined,
      metric: form.metric.trim() || undefined,
      condition: {
        type: form.conditionType,
        operator: form.operator,
        // 阈值型必填（rules.threshold 挡着），所以这里的 0 只可能出现在变化率/趋势两支的草稿上 ——
        // 那两类调度器不会执行，存 0 不影响任何一次判定，也就不用为它发明一个"空阈值"的形状
        value: Number(form.threshold ?? 0),
        durationSeconds: form.duration ? Number(form.duration) : undefined,
      },
      notify: {
        channels: form.channels as NotifyChannel[],
        // 表单里成员是完整事实，所以这里永远回传数组（空数组就是"一个组都不挂"）
        groupIds: form.groupIds.map((id) => Number(id)),
      },
    }
  }

  async function handleSave() {
    const valid = await formRef.value?.validate().catch(() => false)
    if (!valid) return
    saving.value = true
    const wantEnabled = form.enabled
    try {
      if (editingId.value) {
        await updateAlertRule(editingId.value, payload())
      } else {
        // 把新 id 记回 editingId：保存后半路失败（比如启停没换成）时再点一次保存是改这一条，
        // 而不是又建一条同名规则 —— 中途失败不该让用户的重试变成重复数据
        editingId.value = await createAlertRule(payload())
      }
      // 启停是独立端点（后端 create 固定 enabled=true、update 不动 enabled），
      // 所以表单里这次改动要单独落一次，而不是假装它跟着保存请求进去了
      if (wantEnabled !== enabledAtOpen.value) {
        await toggleAlertRule(editingId.value, wantEnabled)
      }
      ElMessage.success(t('alert.saved'))
      dialogVisible.value = false
      loadData()
    } catch (e) {
      ElMessage.error((e as Error).message)
      loadData()
    } finally {
      saving.value = false
    }
  }

  async function handleToggle(row: AlertRule, enabled: boolean) {
    togglingId.value = row.id
    try {
      await toggleAlertRule(row.id, enabled)
      row.status = enabled ? 'enabled' : 'disabled'
      ElMessage.success(t('alert.statusUpdated'))
    } catch (e) {
      ElMessage.error((e as Error).message)
    } finally {
      togglingId.value = ''
      loadData()
    }
  }

  async function handleTest(row: AlertRule) {
    testingId.value = row.id
    try {
      testResult.value = await testAlertRule(row.id)
      testVisible.value = true
    } catch (e) {
      ElMessage.error((e as Error).message)
    } finally {
      testingId.value = ''
    }
  }

  async function handleDelete(row: AlertRule) {
    const confirmed = await ElMessageBox.confirm(t('common.confirmDelete', { name: row.name }), t('common.warning'), {
      type: 'warning',
    }).catch(() => false)
    if (!confirmed) return
    try {
      await deleteAlertRule(row.id)
      ElMessage.success(t('alert.deleted'))
      loadData()
    } catch (e) {
      ElMessage.error((e as Error).message)
    }
  }

  onMounted(() => {
    loadData()
    listChannelSchemas()
      .then((res) => (schemas.value = res))
      .catch(() => (schemas.value = []))
  })
</script>

<style lang="scss" scoped>
  .cond {
    font-size: var(--dv-font-sm);
  }
  .notify-cell {
    display: flex;
    align-items: center;
    gap: 4px;
    flex-wrap: wrap;
  }
  .form-alert {
    margin-bottom: var(--dv-space-md);
  }
  .cond-row {
    display: flex;
    align-items: center;
    gap: 8px;
    width: 100%;
  }
  .cond-value {
    flex: 1;
    min-width: 120px;
  }
  .hint {
    margin: 2px 0 0;
    font-size: var(--dv-font-sm);
    line-height: 1.6;
  }
  .warn {
    color: var(--dv-warning, #e6a23c);
  }
  .opt-addr {
    float: right;
    margin-left: 12px;
    color: var(--dv-text-3);
    font-size: var(--dv-font-xs);
  }
  .opt-tag {
    margin-left: 6px;
  }
  .test-body p {
    margin: 0 0 8px;
  }
  .test-line {
    display: flex;
    align-items: center;
    gap: 10px;
  }
</style>
