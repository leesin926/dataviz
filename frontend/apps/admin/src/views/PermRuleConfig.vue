<template>
  <div class="dv-page">
    <div class="dv-page-head">
      <div>
        <h2>{{ t('permRule.title') }}</h2>
        <p class="dv-page-desc">{{ t('permRule.desc') }}</p>
      </div>
      <div class="dv-page-actions">
        <el-button v-permission="'analysis:write'" type="primary" @click="openCreate">
          {{ t('permRule.create') }}
        </el-button>
      </div>
    </div>

    <div class="dv-toolbar">
      <el-input
        v-model="keyword"
        :placeholder="t('common.keywordPlaceholder')"
        clearable
        style="width: 240px"
        @keyup.enter="reload"
        @clear="reload"
      />
      <el-button type="primary" :loading="loading" @click="reload">{{ t('common.search') }}</el-button>
      <el-button @click="onReset">{{ t('common.reset') }}</el-button>
    </div>

    <el-card shadow="never">
      <el-table :data="list" v-loading="loading" stripe>
        <el-table-column prop="name" :label="t('permRule.name')" min-width="150" show-overflow-tooltip />
        <el-table-column :label="t('permRule.dataset')" min-width="140">
          <template #default="{ row }">
            <span v-if="row.datasetId">{{ datasetName(row.datasetId) }}</span>
            <span v-else class="dv-text-3">{{ t('permRule.datasetAll') }}</span>
          </template>
        </el-table-column>
        <el-table-column :label="t('permRule.scope')" min-width="170">
          <template #default="{ row }">
            <el-tag size="small" type="info">{{ scopeTypeLabel(row.scopeType) }}</el-tag>
            <span class="scope-name">{{ scopeLabel(row) }}</span>
          </template>
        </el-table-column>
        <el-table-column :label="t('permRule.conditions')" min-width="200">
          <template #default="{ row }">
            <span v-if="row.conditions && row.conditions.length" class="dv-mono summary">
              {{ conditionsSummary(row.conditions) }}
            </span>
            <span v-else class="dv-text-3">{{ t('permRule.conditionsNone') }}</span>
          </template>
        </el-table-column>
        <el-table-column :label="t('permRule.restrictedColumns')" min-width="170">
          <template #default="{ row }">
            <span v-if="row.restrictedColumns && row.restrictedColumns.length" class="dv-mono summary">
              {{ row.restrictedColumns.join(', ') }}
            </span>
            <span v-else class="dv-text-3">{{ t('permRule.columnsNone') }}</span>
          </template>
        </el-table-column>
        <el-table-column :label="t('common.status')" width="110">
          <template #default="{ row }">
            <el-switch
              :model-value="row.enabled !== 0"
              :disabled="!canWrite"
              :loading="togglingId === row.id"
              :active-text="t('common.enabled')"
              @change="(v: boolean) => onToggle(row, v)"
            />
          </template>
        </el-table-column>
        <el-table-column prop="updateTime" :label="t('common.updatedAt')" width="170" />
        <el-table-column :label="t('common.operations')" width="150" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="'analysis:write'" link type="primary" @click="openEdit(row)">
              {{ t('common.edit') }}
            </el-button>
            <el-button v-permission="'analysis:write'" link type="danger" @click="onDelete(row)">
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

    <el-card shadow="never" class="explain-card">
      <div class="explain-head">
        <div>
          <h3>{{ t('permRule.explainTitle') }}</h3>
          <p class="dv-page-desc">{{ t('permRule.explainDesc') }}</p>
        </div>
        <div class="explain-controls">
          <el-select v-model="explainDataset" clearable :placeholder="t('permRule.datasetAll')" style="width: 220px">
            <el-option v-for="d in datasets" :key="d.id" :label="d.name" :value="d.id" />
          </el-select>
          <el-button :loading="explaining" @click="runExplain">{{ t('permRule.explainRun') }}</el-button>
        </div>
      </div>

      <template v-if="explain">
        <el-alert
          v-if="explain.denyAll"
          type="error"
          :closable="false"
          show-icon
          :title="t('permRule.explainDenyAll')"
          :description="t('permRule.explainDenyAllTip')"
          class="explain-alert"
        />
        <el-alert
          v-else-if="!explain.appliedRuleNames.length && !explain.enabledRuleCount"
          type="info"
          :closable="false"
          show-icon
          :title="t('permRule.explainNoRule')"
          class="explain-alert"
        />
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item :label="t('permRule.explainRuleCount')">
            {{ explain.enabledRuleCount }}
          </el-descriptions-item>
          <el-descriptions-item :label="t('permRule.explainAppliedRules')">
            <span v-if="explain.appliedRuleNames.length">{{ explain.appliedRuleNames.join('、') }}</span>
            <span v-else class="dv-text-3">—</span>
          </el-descriptions-item>
          <el-descriptions-item :label="t('permRule.explainPredicate')">
            <code v-if="explain.rowPredicate" class="dv-mono">{{ explain.rowPredicate }}</code>
            <span v-else class="dv-text-3">{{ t('permRule.explainPredicateNone') }}</span>
          </el-descriptions-item>
          <el-descriptions-item :label="t('permRule.explainHiddenColumns')">
            <span v-if="explain.restrictedColumns.length" class="dv-mono">{{ explain.restrictedColumns.join(', ') }}</span>
            <span v-else class="dv-text-3">{{ t('permRule.explainHiddenNone') }}</span>
          </el-descriptions-item>
        </el-descriptions>
      </template>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="editingId ? t('permRule.editTitle') : t('permRule.createTitle')" width="720px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="96px">
        <el-form-item :label="t('permRule.name')" prop="name">
          <el-input v-model="form.name" :placeholder="t('permRule.namePlaceholder')" maxlength="64" show-word-limit />
        </el-form-item>
        <el-form-item :label="t('permRule.dataset')">
          <el-select v-model="form.datasetId" clearable filterable :placeholder="t('permRule.datasetAll')" style="width: 100%">
            <el-option v-for="d in datasets" :key="d.id" :label="d.name" :value="d.id" />
          </el-select>
          <span class="dv-text-3 hint">{{ t('permRule.datasetHint') }}</span>
        </el-form-item>
        <el-form-item :label="t('permRule.scopeType')">
          <el-radio-group v-model="form.scopeType" @change="form.scopeValue = ''">
            <el-radio-button value="USER">{{ t('permRule.scopeUser') }}</el-radio-button>
            <el-radio-button value="ROLE">{{ t('permRule.scopeRole') }}</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item :label="t('permRule.scopeValue')" prop="scopeValue">
          <el-select
            v-if="form.scopeType === 'USER'"
            v-model="form.scopeValue"
            filterable
            :placeholder="t('permRule.userPlaceholder')"
            style="width: 100%"
          >
            <el-option v-for="u in users" :key="u.id" :label="`${u.nickname || u.username} (${u.username})`" :value="String(u.id)" />
          </el-select>
          <el-select
            v-else
            v-model="form.scopeValue"
            filterable
            :placeholder="t('permRule.rolePlaceholder')"
            style="width: 100%"
          >
            <el-option v-for="r in roles" :key="r.id" :label="`${r.roleName} (${r.roleCode})`" :value="r.roleCode" />
          </el-select>
        </el-form-item>

        <el-form-item :label="t('permRule.conditions')">
          <div v-for="(row, idx) in form.conditions" :key="idx" class="cond-row">
            <el-select v-model="row.field" filterable allow-create default-first-option :placeholder="t('permRule.conditionField')" class="cond-field">
              <el-option v-for="f in fieldOptions" :key="f" :label="f" :value="f" />
            </el-select>
            <el-select v-model="row.operator" :placeholder="t('permRule.conditionOperator')" class="cond-op">
              <el-option v-for="op in OPERATORS" :key="op.value" :label="t(op.labelKey)" :value="op.value" />
            </el-select>
            <div class="cond-value">
              <el-input v-if="needsNoValue(row.operator)" disabled :placeholder="t('permRule.noValue')" />
              <el-select
                v-else-if="needsRange(row.operator)"
                v-model="row.list"
                multiple
                filterable
                allow-create
                default-first-option
                :placeholder="t('permRule.betweenHint')"
                style="width: 100%"
              >
                <el-option v-for="p in PLACEHOLDERS" :key="p" :label="p" :value="p" />
              </el-select>
              <el-select
                v-else-if="needsList(row.operator)"
                v-model="row.list"
                multiple
                filterable
                allow-create
                default-first-option
                :placeholder="t('permRule.listValueHint')"
                style="width: 100%"
              >
                <el-option v-for="p in PLACEHOLDERS" :key="p" :label="p" :value="p" />
              </el-select>
              <el-select v-else v-model="row.scalar" filterable allow-create default-first-option :placeholder="t('permRule.valuePlaceholder')">
                <el-option v-for="p in PLACEHOLDERS" :key="p" :label="p" :value="p" />
              </el-select>
            </div>
            <el-button link type="danger" @click="form.conditions.splice(idx, 1)">
              {{ t('permRule.removeCondition') }}
            </el-button>
          </div>
          <el-button link type="primary" @click="addCondition">{{ t('permRule.addCondition') }}</el-button>
          <div class="dv-text-3 hint">{{ t('permRule.conditionsHint') }}</div>
        </el-form-item>

        <el-form-item :label="t('permRule.restrictedColumns')">
          <el-select
            v-model="form.restrictedColumns"
            multiple
            filterable
            allow-create
            default-first-option
            :placeholder="t('permRule.restrictedColumnsPlaceholder')"
            style="width: 100%"
          >
            <el-option v-for="f in fieldOptions" :key="f" :label="f" :value="f" />
          </el-select>
          <div class="dv-text-3 hint">{{ t('permRule.restrictedColumnsHint') }}</div>
        </el-form-item>

        <el-form-item :label="t('permRule.remark')">
          <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="256" show-word-limit />
        </el-form-item>
        <el-form-item :label="t('common.status')">
          <el-switch v-model="form.enabled" :active-text="t('common.enabled')" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="saving" @click="onSubmit">{{ t('common.save') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
  import { computed, nextTick, onMounted, reactive, ref } from 'vue'
  import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
  import type {
    DataPermCondition,
    DataPermExplain,
    DataPermRule,
    DataPermRulePayload,
    QueryDataset,
    Role,
    User,
  } from '@dataviz/shared-types'
  import { rowsOf, totalOf } from '@dataviz/shared-types'
  import {
    createPermRule,
    deletePermRule,
    explainPermRules,
    listDatasets,
    listRoles,
    listUsers,
    pagePermRules,
    updatePermRule,
  } from '@dataviz/api-client'
  import { useI18n } from 'vue-i18n'
  import { hasSessionPermission } from '@dataviz/permission'

  const { t } = useI18n()
  const canWrite = hasSessionPermission('analysis:write')

  /**
   * 操作符必须是一份闭集：后端 SqlBuilder 的白名单只认这些，自由输入的操作符会在保存时被拒。
   * 这里把同一份集合摆成下拉，配置的人不需要去猜"支持哪些写法"。
   */
  const OPERATORS = [
    { value: '=', labelKey: 'permRule.opEq' },
    { value: '!=', labelKey: 'permRule.opNe' },
    { value: '>', labelKey: 'permRule.opGt' },
    { value: '>=', labelKey: 'permRule.opGte' },
    { value: '<', labelKey: 'permRule.opLt' },
    { value: '<=', labelKey: 'permRule.opLte' },
    { value: 'like', labelKey: 'permRule.opLike' },
    { value: 'notlike', labelKey: 'permRule.opNotLike' },
    { value: 'in', labelKey: 'permRule.opIn' },
    { value: 'notin', labelKey: 'permRule.opNotIn' },
    { value: 'between', labelKey: 'permRule.opBetween' },
    { value: 'notbetween', labelKey: 'permRule.opNotBetween' },
    { value: 'isnull', labelKey: 'permRule.opIsNull' },
    { value: 'isnotnull', labelKey: 'permRule.opIsNotNull' },
  ]

  /** 与后端 DataPermissionResolver 的占位符集合一致；直接选，不靠手打 */
  const PLACEHOLDERS = [':userId', ':tenantId', ':username', ':deptId']

  interface ConditionRow {
    field: string
    operator: string
    scalar: string
    list: string[]
  }

  const loading = ref(false)
  const saving = ref(false)
  const explaining = ref(false)
  const list = ref<DataPermRule[]>([])
  const total = ref(0)
  const pageNum = ref(1)
  const pageSize = ref(20)
  const keyword = ref('')
  const togglingId = ref<string | number>('')

  const datasets = ref<QueryDataset[]>([])
  const users = ref<User[]>([])
  const roles = ref<Role[]>([])

  const explainDataset = ref<string | number | undefined>()
  const explain = ref<DataPermExplain | null>(null)

  const dialogVisible = ref(false)
  const editingId = ref<string | number>('')
  const formRef = ref<FormInstance>()
  const form = reactive({
    name: '',
    datasetId: undefined as string | number | undefined,
    scopeType: 'ROLE' as 'USER' | 'ROLE',
    scopeValue: '',
    conditions: [] as ConditionRow[],
    restrictedColumns: [] as string[],
    remark: '',
    enabled: true,
  })

  const rules = computed<FormRules>(() => ({
    name: [{ required: true, message: t('permRule.nameRequired'), trigger: 'blur' }],
    scopeValue: [{ required: true, message: t('permRule.scopeValueRequired'), trigger: 'change' }],
  }))

  /** 选了数据集就用它已配置的字段做候选；没选数据集时只能手填（列名本来属于各自的表） */
  const fieldOptions = computed(() => {
    const dataset = datasets.value.find((d) => String(d.id) === String(form.datasetId))
    if (!dataset) return []
    const fields = new Set<string>()
    ;(dataset.dimensions ?? []).forEach((d) => fields.add(d.field))
    ;(dataset.measures ?? []).forEach((m) => fields.add(m.field))
    return Array.from(fields)
  })

  function needsNoValue(operator: string) {
    return operator === 'isnull' || operator === 'isnotnull'
  }

  function needsRange(operator: string) {
    return operator === 'between' || operator === 'notbetween'
  }

  function needsList(operator: string) {
    return operator === 'in' || operator === 'notin'
  }

  /** 数值形状的文本转成数字，避免把 dept_id = 5 拼成 '5' 走隐式转换 */
  function coerce(text: string): string | number {
    return /^-?\d+(\.\d+)?$/.test(text.trim()) ? Number(text.trim()) : text.trim()
  }

  function conditionValue(row: ConditionRow): unknown {
    if (needsNoValue(row.operator)) return null
    if (needsList(row.operator) || needsRange(row.operator)) return row.list.map(coerce)
    return coerce(row.scalar)
  }

  function toRow(condition: DataPermCondition): ConditionRow {
    const raw = condition.value
    const asList = Array.isArray(raw) ? raw.map((v) => String(v)) : []
    return {
      field: condition.field,
      operator: condition.operator,
      scalar: raw == null || Array.isArray(raw) ? '' : String(raw),
      list: asList,
    }
  }

  function operatorLabel(operator: string): string {
    const found = OPERATORS.find((op) => op.value === operator)
    return found ? t(found.labelKey) : operator
  }

  function conditionsSummary(conditions: DataPermCondition[]): string {
    return conditions
      .map((c) => `${c.field} ${operatorLabel(c.operator)}${needsNoValue(c.operator) ? '' : ` ${formatValue(c.value)}`}`)
      .join(' AND ')
  }

  function formatValue(value: unknown): string {
    if (Array.isArray(value)) return `[${value.join(', ')}]`
    return value == null ? '' : String(value)
  }

  function datasetName(id: string | number): string {
    return datasets.value.find((d) => String(d.id) === String(id))?.name ?? `#${id}`
  }

  function scopeTypeLabel(scopeType: string): string {
    return scopeType === 'USER' ? t('permRule.scopeUser') : t('permRule.scopeRole')
  }

  /** 命中对象存的是 ID / 角色码，列表里要看得见是谁 —— 只有 ID 的规则表没人能读懂 */
  function scopeLabel(row: DataPermRule): string {
    if (row.scopeType === 'USER') {
      const user = users.value.find((u) => String(u.id) === String(row.scopeValue))
      return user ? `${user.nickname || user.username} (${user.username})` : `#${row.scopeValue}`
    }
    const role = roles.value.find((r) => r.roleCode === row.scopeValue)
    return role ? role.roleName : row.scopeValue
  }

  async function load() {
    loading.value = true
    try {
      const res = await pagePermRules({
        pageNum: pageNum.value,
        pageSize: pageSize.value,
        keyword: keyword.value || undefined,
      })
      list.value = rowsOf(res)
      total.value = totalOf(res)
    } catch (e) {
      ElMessage.error((e as Error).message)
    } finally {
      loading.value = false
    }
  }

  async function loadOptions() {
    // 选项只是候选值，拉不到时下拉框为空、字段名退化成手填 —— 规则列表本身不该被它挡住，
    // 所以这里只记账不弹窗：给 analyst 弹一句「请求失败」只会让人以为整页坏了
    const results = await Promise.allSettled([
      listDatasets({ pageNum: 1, pageSize: 200 }),
      listUsers({ pageNum: 1, pageSize: 200 }),
      listRoles(),
    ])
    const [ds, us, rs] = results
    if (ds.status === 'fulfilled') datasets.value = rowsOf(ds.value)
    else console.warn('[perm-rule] 数据集列表拉取失败', ds.reason)
    if (us.status === 'fulfilled') users.value = rowsOf(us.value)
    else console.warn('[perm-rule] 用户列表拉取失败', us.reason)
    if (rs.status === 'fulfilled') roles.value = rowsOf(rs.value)
    else console.warn('[perm-rule] 角色列表拉取失败', rs.reason)
  }

  function reload() {
    pageNum.value = 1
    load()
  }

  function onReset() {
    keyword.value = ''
    reload()
  }

  function addCondition() {
    form.conditions.push({ field: '', operator: '=', scalar: '', list: [] })
  }

  function resetForm(row?: DataPermRule) {
    form.name = row?.name ?? ''
    form.datasetId = row?.datasetId ?? undefined
    form.scopeType = row?.scopeType === 'USER' ? 'USER' : 'ROLE'
    form.scopeValue = row?.scopeValue ?? ''
    form.conditions = (row?.conditions ?? []).map(toRow)
    form.restrictedColumns = [...(row?.restrictedColumns ?? [])]
    form.remark = row?.remark ?? ''
    form.enabled = row ? row.enabled !== 0 : true
  }

  function openCreate() {
    editingId.value = ''
    resetForm()
    addCondition()
    showDialog()
  }

  function openEdit(row: DataPermRule) {
    editingId.value = row.id
    resetForm(row)
    if (!form.conditions.length) addCondition()
    showDialog()
  }

  /** 校验态要跟着这次编辑走：上一次的红字留在下一次打开的弹窗里，等于在说一件这次没发生的事 */
  function showDialog() {
    dialogVisible.value = true
    nextTick(() => formRef.value?.clearValidate())
  }

  function payload(): DataPermRulePayload {
    const conditions: DataPermCondition[] = form.conditions
      .filter((row) => row.field.trim())
      .map((row) => ({ field: row.field.trim(), operator: row.operator, value: conditionValue(row) }))
    return {
      name: form.name.trim(),
      datasetId: form.datasetId ?? null,
      scopeType: form.scopeType,
      scopeValue: form.scopeValue.trim(),
      conditions,
      restrictedColumns: form.restrictedColumns.map((c) => c.trim()).filter(Boolean),
      enabled: form.enabled ? 1 : 0,
      remark: form.remark.trim() || undefined,
    }
  }

  async function onSubmit() {
    const valid = await formRef.value?.validate().catch(() => false)
    if (!valid) return
    const data = payload()
    if (!data.conditions.length && !data.restrictedColumns.length) {
      // 与后端 DataPermRuleServiceImpl 同一条判据：什么都不做的规则不该存下来
      ElMessage.error(t('permRule.atLeastOne'))
      return
    }
    const filled = form.conditions.filter((row) => row.field.trim())
    if (filled.some((row) => needsRange(row.operator) && row.list.length !== 2)) {
      ElMessage.error(t('permRule.betweenRequired'))
      return
    }
    if (filled.some((row) => needsList(row.operator) && !row.list.length)) {
      ElMessage.error(t('permRule.listRequired'))
      return
    }
    saving.value = true
    try {
      if (editingId.value) await updatePermRule(editingId.value, data)
      else await createPermRule(data)
      ElMessage.success(t('permRule.saved'))
      dialogVisible.value = false
      load()
    } catch (e) {
      ElMessage.error((e as Error).message)
    } finally {
      saving.value = false
    }
  }

  async function onToggle(row: DataPermRule, enabled: boolean) {
    togglingId.value = row.id
    try {
      await updatePermRule(row.id, {
        name: row.name,
        datasetId: row.datasetId ?? null,
        scopeType: row.scopeType,
        scopeValue: row.scopeValue,
        conditions: row.conditions ?? [],
        restrictedColumns: row.restrictedColumns ?? [],
        enabled: enabled ? 1 : 0,
        remark: row.remark,
      })
    } catch (e) {
      ElMessage.error((e as Error).message)
    } finally {
      togglingId.value = ''
      // 失败也要弹回库里的真实状态：这一列的开关是一次写操作，不是本地偏好
      load()
    }
  }

  async function onDelete(row: DataPermRule) {
    const confirmed = await ElMessageBox.confirm(
      t('common.confirmDelete', { name: row.name }),
      t('common.warning'),
      { type: 'warning' },
    ).catch(() => false)
    if (!confirmed) return
    try {
      await deletePermRule(row.id)
      ElMessage.success(t('permRule.deleted'))
      load()
    } catch (e) {
      ElMessage.error((e as Error).message)
    }
  }

  async function runExplain() {
    explaining.value = true
    try {
      explain.value = await explainPermRules(explainDataset.value)
    } catch (e) {
      ElMessage.error((e as Error).message)
    } finally {
      explaining.value = false
    }
  }

  onMounted(() => {
    load()
    loadOptions()
  })
</script>

<style lang="scss" scoped>
  .scope-name {
    margin-left: 8px;
  }
  .summary {
    font-size: var(--dv-font-sm);
  }
  .hint {
    font-size: var(--dv-font-sm);
    line-height: 1.6;
  }
  .explain-card {
    margin-top: 16px;
  }
  .explain-head {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 16px;
    flex-wrap: wrap;
  }
  .explain-controls {
    display: flex;
    gap: 8px;
  }
  .explain-alert {
    margin-bottom: 12px;
  }
  .cond-row {
    display: flex;
    align-items: center;
    gap: 8px;
    width: 100%;
    margin-bottom: 8px;
  }
  .cond-field {
    width: 160px;
    flex: none;
  }
  .cond-op {
    width: 120px;
    flex: none;
  }
  .cond-value {
    flex: 1;
    min-width: 0;
  }
</style>
