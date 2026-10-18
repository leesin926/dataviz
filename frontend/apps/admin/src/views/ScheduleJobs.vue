<template>
  <div class="dv-page">
    <div class="dv-page-head">
      <div>
        <h2>{{ t('schedule.title') }}</h2>
        <p class="dv-page-desc">{{ t('schedule.desc') }}</p>
      </div>
      <div class="dv-page-actions">
        <el-button v-permission="'schedule:write'" type="primary" @click="openCreate">
          {{ t('schedule.create') }}
        </el-button>
      </div>
    </div>

    <div class="stats">
      <div class="stat">
        <span class="k">{{ t('schedule.statTotal') }}</span>
        <span class="v">{{ stats.totalJobs }}</span>
      </div>
      <div class="stat">
        <span class="k">{{ t('schedule.statRunning') }}</span>
        <span class="v ok">{{ stats.runningJobs }}</span>
      </div>
      <div class="stat">
        <span class="k">{{ t('schedule.statPaused') }}</span>
        <span class="v">{{ stats.pausedJobs }}</span>
      </div>
      <div class="stat">
        <span class="k">{{ t('schedule.statError') }}</span>
        <span class="v bad">{{ stats.errorJobs }}</span>
      </div>
      <div class="stat">
        <span class="k">{{ t('schedule.statToday') }}</span>
        <span class="v">
          {{ stats.todayExecutions }}
          <span class="sub">{{ t('schedule.statTodayDetail', { success: stats.todaySuccess, failed: stats.todayFailed }) }}</span>
        </span>
      </div>
    </div>

    <div class="dv-toolbar">
      <el-select v-model="status" clearable :placeholder="t('schedule.statusAll')" style="width: 150px" @change="reload">
        <el-option :label="t('schedule.status.RUNNING')" value="RUNNING" />
        <el-option :label="t('schedule.status.PAUSED')" value="PAUSED" />
        <el-option :label="t('schedule.status.ERROR')" value="ERROR" />
      </el-select>
      <el-input
        v-model="jobGroup"
        :placeholder="t('schedule.groupPlaceholder')"
        clearable
        style="width: 200px"
        @keyup.enter="reload"
        @clear="reload"
      />
      <el-button type="primary" :loading="loading" @click="reload">{{ t('common.search') }}</el-button>
      <el-button @click="onReset">{{ t('common.reset') }}</el-button>
      <el-button :loading="statsLoading" @click="loadStats">{{ t('common.refresh') }}</el-button>
    </div>

    <!-- 一个处理器都没注册上时，新建表单是填不出可用任务的：先把原因摆在列表头上 -->
    <el-alert
      v-if="!handlersLoading && !handlers.length"
      class="notice"
      type="error"
      :closable="false"
      :title="t('schedule.noHandlerTitle')"
      :description="t('schedule.noHandlerDesc')"
      show-icon
    />

    <el-card shadow="never">
      <el-table :data="list" v-loading="loading" stripe>
        <el-table-column prop="jobName" :label="t('schedule.jobName')" min-width="150" show-overflow-tooltip />
        <el-table-column :label="t('schedule.jobGroup')" width="120">
          <template #default="{ row }">
            <span v-if="row.jobGroup">{{ row.jobGroup }}</span>
            <span v-else class="dv-text-3">—</span>
          </template>
        </el-table-column>
        <el-table-column :label="t('schedule.handler')" min-width="180">
          <template #default="{ row }">
            <span v-if="row.handlerLabel">{{ row.handlerLabel }}</span>
            <span v-else class="dv-text-3">{{ t('schedule.handlerMissing') }}</span>
            <div class="handler-key">{{ row.jobClass }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="cronExpression" :label="t('schedule.cron')" width="160">
          <template #default="{ row }"><code class="mono">{{ row.cronExpression }}</code></template>
        </el-table-column>
        <el-table-column :label="t('common.status')" width="130">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status)" size="small" disable-transitions>
              {{ statusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('schedule.risk')" width="150">
          <template #default="{ row }">
            <el-tooltip v-if="row.handlerRegistered === false" :content="t('schedule.riskUnregistered')" placement="top">
              <el-tag type="danger" size="small" disable-transitions>{{ t('schedule.riskUnregisteredTag') }}</el-tag>
            </el-tooltip>
            <span v-else class="dv-text-3">—</span>
          </template>
        </el-table-column>
        <el-table-column prop="updateTime" :label="t('common.updatedAt')" width="170" />
        <el-table-column :label="t('common.operations')" width="250" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="'schedule:write'" link type="primary" @click="openEdit(row)">
              {{ t('common.edit') }}
            </el-button>
            <el-button
              v-if="row.status === 'RUNNING'"
              v-permission="'schedule:write'"
              link
              type="warning"
              @click="onPause(row)"
            >
              {{ t('schedule.pause') }}
            </el-button>
            <el-button
              v-else
              v-permission="'schedule:write'"
              link
              type="success"
              @click="onResume(row)"
            >
              {{ t('schedule.resume') }}
            </el-button>
            <el-button v-permission="'schedule:write'" link type="primary" @click="onRunOnce(row)">
              {{ t('schedule.runOnce') }}
            </el-button>
            <el-button link type="primary" @click="openLogs(row)">{{ t('schedule.logs') }}</el-button>
            <el-button v-permission="'schedule:write'" link type="danger" @click="onDelete(row)">
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

    <el-dialog v-model="dialogVisible" :title="editingId ? t('schedule.editTitle') : t('schedule.createTitle')" width="640px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="96px">
        <el-form-item :label="t('schedule.jobName')" prop="jobName">
          <el-input v-model="form.jobName" maxlength="128" show-word-limit />
        </el-form-item>
        <el-form-item :label="t('schedule.jobGroup')">
          <el-input v-model="form.jobGroup" maxlength="64" :placeholder="t('schedule.groupPlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('schedule.handler')" prop="jobClass">
          <el-select v-model="form.jobClass" style="width: 100%" :loading="handlersLoading" :placeholder="t('schedule.handlerPick')">
            <el-option v-for="h in handlers" :key="h.handlerKey" :label="h.label" :value="h.handlerKey">
              <span>{{ h.label }}</span>
              <span class="opt-key">{{ h.handlerKey }}</span>
            </el-option>
          </el-select>
          <span v-if="selectedHandler" class="hint dv-text-3">{{ selectedHandler.description }}</span>
          <span v-else-if="!handlersLoading" class="hint dv-text-3">{{ t('schedule.noHandlerInForm') }}</span>
        </el-form-item>
        <el-form-item :label="t('schedule.cron')" prop="cronExpression">
          <el-input v-model="form.cronExpression" class="mono" :placeholder="t('schedule.cronPlaceholder')" />
          <span class="hint dv-text-3">{{ t('schedule.cronHint') }}</span>
        </el-form-item>
        <el-form-item :label="t('schedule.params')">
          <el-input
            v-model="form.jobParams"
            type="textarea"
            :rows="2"
            :placeholder="t('schedule.paramsPlaceholder')"
          />
          <span v-if="!isParamJson(form.jobParams)" class="hint warn">{{ t('schedule.paramsNotJson') }}</span>
        </el-form-item>
        <el-form-item :label="t('common.description')">
          <el-input v-model="form.description" type="textarea" :rows="2" maxlength="512" show-word-limit />
        </el-form-item>
        <el-form-item :label="t('schedule.misfireLabel')">
          <el-select v-model="form.misfirePolicy" style="width: 200px">
            <el-option :label="t('schedule.misfire.IGNORE')" value="IGNORE" />
            <el-option :label="t('schedule.misfire.FIRE_ONCE')" value="FIRE_ONCE" />
            <el-option :label="t('schedule.misfire.EXECUTE_ALL')" value="EXECUTE_ALL" />
          </el-select>
          <!-- 存库但调度器不读：承诺补跑就是假成功 -->
          <span class="hint dv-text-3">{{ t('schedule.misfireHint') }}</span>
        </el-form-item>
        <el-form-item v-if="editingId && form.status !== 'RUNNING'" :label="t('common.status')">
          <span class="hint dv-text-3">{{ t('schedule.pausedEditTip') }}</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="saving" @click="onSubmit">{{ t('common.save') }}</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="logsVisible" :title="t('schedule.logsTitle', { name: logsJobName })" size="640px">
      <el-table :data="logs" v-loading="logsLoading" stripe>
        <el-table-column prop="startTime" :label="t('schedule.logStart')" width="170" />
        <el-table-column :label="t('common.status')" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 'SUCCESS' ? 'success' : 'danger'" size="small" disable-transitions>
              {{ row.status === 'SUCCESS' ? t('common.success') : t('common.failed') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="message" :label="t('schedule.logMessage')" min-width="240" show-overflow-tooltip />
        <template #empty>
          <span class="dv-text-3">{{ t('schedule.logsEmpty') }}</span>
        </template>
      </el-table>
      <el-pagination
        class="dv-pager"
        layout="total, prev, pager, next"
        :total="logsTotal"
        :current-page="logsPageNum"
        :page-size="logsPageSize"
        @current-change="(p: number) => { logsPageNum = p; loadLogs() }"
      />
      <p class="dv-page-desc">{{ t('schedule.logsExplain') }}</p>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
  import { computed, onMounted, reactive, ref } from 'vue'
  import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
  import type { ScheduleHandler, ScheduleJob, ScheduleJobLog, ScheduleJobPayload, ScheduleStats } from '@dataviz/shared-types'
  import { rowsOf, totalOf } from '@dataviz/shared-types'
  import {
    createJob,
    deleteJob,
    listHandlers,
    listJobLogs,
    listJobs,
    pauseJob,
    resumeJob,
    runJobOnce,
    updateJob,
    getStats,
  } from '@dataviz/api-client'
  import { useI18n } from 'vue-i18n'

  const { t } = useI18n()

  const EMPTY_STATS: ScheduleStats = {
    totalJobs: 0,
    runningJobs: 0,
    pausedJobs: 0,
    errorJobs: 0,
    todayExecutions: 0,
    todaySuccess: 0,
    todayFailed: 0,
  }

  const loading = ref(false)
  const statsLoading = ref(false)
  const saving = ref(false)
  const handlersLoading = ref(false)
  const list = ref<ScheduleJob[]>([])
  const total = ref(0)
  const pageNum = ref(1)
  const pageSize = ref(20)
  const status = ref('')
  const jobGroup = ref('')
  const stats = ref<ScheduleStats>({ ...EMPTY_STATS })
  const handlers = ref<ScheduleHandler[]>([])

  const dialogVisible = ref(false)
  const editingId = ref<string | number>('')
  const formRef = ref<FormInstance>()
  const form = reactive<{
    jobName: string
    jobGroup: string
    cronExpression: string
    jobClass: string
    jobParams: string
    description: string
    misfirePolicy: string
    status: string
  }>({
    jobName: '',
    jobGroup: '',
    cronExpression: '',
    jobClass: '',
    jobParams: '',
    description: '',
    misfirePolicy: 'IGNORE',
    status: 'RUNNING',
  })

  const rules = computed<FormRules>(() => ({
    jobName: [{ required: true, message: t('schedule.jobNameRequired'), trigger: 'blur' }],
    cronExpression: [{ required: true, message: t('schedule.cronRequired'), trigger: 'blur' }],
    jobClass: [{ required: true, message: t('schedule.handlerRequired'), trigger: 'change' }],
  }))

  const selectedHandler = computed(() => handlers.value.find((h) => h.handlerKey === form.jobClass))

  const logsVisible = ref(false)
  const logsLoading = ref(false)
  const logsJobId = ref<string | number>('')
  const logsJobName = ref('')
  const logs = ref<ScheduleJobLog[]>([])
  const logsTotal = ref(0)
  const logsPageNum = ref(1)
  const logsPageSize = ref(20)

  function statusText(value: string): string {
    const key = `schedule.status.${value}`
    const text = t(key)
    // 老数据里有 STOPPED 这类不在枚举内的取值：翻译不出来时原样显示，别把它洗成 RUNNING
    return text === key ? value : text
  }

  function statusTag(value: string): 'success' | 'warning' | 'danger' | 'info' {
    if (value === 'RUNNING') return 'success'
    if (value === 'PAUSED') return 'warning'
    if (value === 'ERROR') return 'danger'
    return 'info'
  }

  function isParamJson(text: string): boolean {
    if (!text.trim()) return true
    try {
      JSON.parse(text)
      return true
    } catch {
      return false
    }
  }

  async function load() {
    loading.value = true
    try {
      const res = await listJobs({
        pageNum: pageNum.value,
        pageSize: pageSize.value,
        status: status.value || undefined,
        jobGroup: jobGroup.value.trim() || undefined,
      })
      list.value = rowsOf<ScheduleJob>(res)
      total.value = totalOf(res)
    } catch (e) {
      ElMessage.error((e as Error).message)
    } finally {
      loading.value = false
    }
  }

  async function loadStats() {
    statsLoading.value = true
    try {
      stats.value = await getStats()
    } catch (e) {
      ElMessage.error((e as Error).message)
    } finally {
      statsLoading.value = false
    }
  }

  async function loadHandlers() {
    handlersLoading.value = true
    try {
      handlers.value = await listHandlers()
    } catch (e) {
      ElMessage.error((e as Error).message)
      handlers.value = []
    } finally {
      handlersLoading.value = false
    }
  }

  function reload() {
    pageNum.value = 1
    load()
  }

  function onReset() {
    status.value = ''
    jobGroup.value = ''
    reload()
  }

  function openCreate() {
    editingId.value = ''
    form.jobName = ''
    form.jobGroup = ''
    form.cronExpression = ''
    form.jobClass = handlers.value.length === 1 ? handlers.value[0].handlerKey : ''
    form.jobParams = ''
    form.description = ''
    form.misfirePolicy = 'IGNORE'
    form.status = 'RUNNING'
    dialogVisible.value = true
  }

  function openEdit(row: ScheduleJob) {
    editingId.value = row.id
    form.jobName = row.jobName ?? ''
    form.jobGroup = row.jobGroup ?? ''
    form.cronExpression = row.cronExpression ?? ''
    form.jobClass = row.jobClass ?? ''
    form.jobParams = row.jobParams ?? ''
    form.description = row.description ?? ''
    form.misfirePolicy = row.misfirePolicy ?? 'IGNORE'
    form.status = row.status
    dialogVisible.value = true
  }

  function payload(): ScheduleJobPayload {
    return {
      jobName: form.jobName.trim(),
      jobGroup: form.jobGroup.trim() || undefined,
      cronExpression: form.cronExpression.trim(),
      jobClass: form.jobClass,
      jobParams: form.jobParams.trim() || undefined,
      description: form.description.trim() || undefined,
      misfirePolicy: form.misfirePolicy,
    }
  }

  async function onSubmit() {
    const valid = await formRef.value?.validate().catch(() => false)
    if (!valid) return
    if (!isParamJson(form.jobParams)) {
      ElMessage.warning(t('schedule.paramsNotJson'))
      return
    }
    saving.value = true
    try {
      if (editingId.value) await updateJob({ ...payload(), id: editingId.value })
      else await createJob(payload())
      ElMessage.success(t('schedule.saved'))
      dialogVisible.value = false
      load()
      loadStats()
    } catch (e) {
      ElMessage.error((e as Error).message)
    } finally {
      saving.value = false
    }
  }

  async function onPause(row: ScheduleJob) {
    try {
      await pauseJob(row.id)
      ElMessage.success(t('schedule.paused'))
      load()
      loadStats()
    } catch (e) {
      ElMessage.error((e as Error).message)
    }
  }

  async function onResume(row: ScheduleJob) {
    try {
      await resumeJob(row.id)
      ElMessage.success(t('schedule.resumed'))
    } catch (e) {
      ElMessage.error((e as Error).message)
    } finally {
      // 恢复失败时后端可能已经把状态改成 ERROR，两种结果都要重读一遍才知道truth
      load()
      loadStats()
    }
  }

  async function onRunOnce(row: ScheduleJob) {
    try {
      await runJobOnce(row.id)
      ElMessage.success(t('schedule.runOnceQueued'))
    } catch (e) {
      ElMessage.error((e as Error).message)
    }
  }

  function openLogs(row: ScheduleJob) {
    logsJobId.value = row.id
    logsJobName.value = row.jobName
    logsPageNum.value = 1
    logsVisible.value = true
    loadLogs()
  }

  async function loadLogs() {
    logsLoading.value = true
    try {
      const res = await listJobLogs(logsJobId.value, { pageNum: logsPageNum.value, pageSize: logsPageSize.value })
      logs.value = rowsOf<ScheduleJobLog>(res)
      logsTotal.value = totalOf(res)
    } catch (e) {
      ElMessage.error((e as Error).message)
    } finally {
      logsLoading.value = false
    }
  }

  async function onDelete(row: ScheduleJob) {
    const confirmed = await ElMessageBox.confirm(
      t('common.confirmDelete', { name: row.jobName }),
      t('common.warning'),
      { type: 'warning' },
    ).catch(() => false)
    if (!confirmed) return
    try {
      await deleteJob(row.id)
      ElMessage.success(t('schedule.deleted'))
      load()
      loadStats()
    } catch (e) {
      ElMessage.error((e as Error).message)
    }
  }

  onMounted(() => {
    load()
    loadStats()
    loadHandlers()
  })
</script>

<style lang="scss" scoped>
  .stats {
    display: flex;
    flex-wrap: wrap;
    gap: 12px;
    margin-bottom: 16px;
  }
  .stat {
    flex: 1 1 150px;
    padding: 12px 16px;
    border: 1px solid var(--dv-border-1);
    border-radius: var(--dv-radius-md);
    background: var(--dv-bg-1);
  }
  .stat .k {
    display: block;
    font-size: var(--dv-font-xs);
    color: var(--dv-text-3);
  }
  .stat .v {
    font-size: var(--dv-font-2xl);
    font-weight: 600;
  }
  .stat .v.ok {
    color: var(--dv-success);
  }
  .stat .v.bad {
    color: var(--dv-danger);
  }
  .stat .sub {
    font-size: var(--dv-font-xs);
    font-weight: 400;
    color: var(--dv-text-3);
  }
  .notice {
    margin-bottom: 12px;
  }
  .handler-key {
    font-family: var(--dv-font-mono, monospace);
    font-size: var(--dv-font-xs);
    color: var(--dv-text-3);
  }
  .mono {
    font-family: var(--dv-font-mono, monospace);
  }
  .hint {
    display: block;
    font-size: var(--dv-font-xs);
    line-height: 1.6;
  }
  .hint.warn {
    color: var(--dv-warning);
  }
  .opt-key {
    float: right;
    margin-left: 12px;
    font-family: var(--dv-font-mono, monospace);
    font-size: var(--dv-font-xs);
    color: var(--dv-text-3);
  }
</style>
