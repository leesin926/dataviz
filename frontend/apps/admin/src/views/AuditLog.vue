<template>
  <div class="dv-page">
    <div class="dv-page-head">
      <div>
        <h2>{{ t('audit.title') }}</h2>
        <p class="dv-page-desc">{{ t('audit.desc') }}</p>
      </div>
      <div class="dv-page-actions">
        <el-button @click="load">{{ t('common.refresh') }}</el-button>
        <el-button type="primary" :loading="exporting" @click="onExport">{{ t('audit.export') }}</el-button>
      </div>
    </div>

    <div class="dv-toolbar">
      <el-input
        v-model="username"
        :placeholder="t('audit.username')"
        clearable
        style="width: 160px"
        @keyup.enter="reload"
        @clear="reload"
      />
      <el-input
        v-model="moduleName"
        :placeholder="t('audit.module')"
        clearable
        style="width: 160px"
        :title="t('audit.modulePlaceholder')"
        @keyup.enter="reload"
        @clear="reload"
      />
      <el-input
        v-model="action"
        :placeholder="t('audit.action')"
        clearable
        style="width: 160px"
        :title="t('audit.actionPlaceholder')"
        @keyup.enter="reload"
        @clear="reload"
      />
      <el-button type="primary" :loading="loading" @click="reload">{{ t('common.search') }}</el-button>
      <el-button @click="onReset">{{ t('common.reset') }}</el-button>
    </div>

    <el-card shadow="never">
      <el-table :data="list" v-loading="loading" stripe>
        <el-table-column prop="username" :label="t('audit.username')" width="120" />
        <el-table-column prop="module" :label="t('audit.module')" width="110" />
        <el-table-column prop="action" :label="t('audit.action')" width="110">
          <template #default="{ row }">
            <el-tag :type="actionTag(row.action)" size="small" disable-transitions>{{ row.action || '-' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="requestUrl" :label="t('audit.requestUrl')" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">
            <code class="dv-mono">{{ row.method }} {{ row.requestUrl }}</code>
          </template>
        </el-table-column>
        <el-table-column prop="responseCode" :label="t('audit.responseCode')" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.responseCode === 200 ? 'success' : 'danger'" size="small" disable-transitions>
              {{ row.responseCode ?? '-' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="ip" :label="t('audit.ip')" width="140" />
        <el-table-column prop="executionTime" :label="t('audit.duration')" width="90" align="center">
          <template #default="{ row }">{{ row.executionTime ?? 0 }} ms</template>
        </el-table-column>
        <el-table-column prop="createTime" :label="t('audit.time')" width="170" fixed="right" />
        <el-table-column :label="t('common.operations')" width="80" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">{{ t('common.detail') }}</el-button>
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

    <el-dialog v-model="detailVisible" :title="t('audit.detailTitle')" width="640px">
      <el-descriptions v-if="detail" :column="1" border>
        <el-descriptions-item :label="t('audit.username')">{{ detail.username || '-' }}</el-descriptions-item>
        <el-descriptions-item :label="t('audit.module')">{{ detail.module || '-' }}</el-descriptions-item>
        <el-descriptions-item :label="t('audit.action')">{{ detail.action || '-' }}</el-descriptions-item>
        <el-descriptions-item :label="t('audit.requestUrl')">
          <code class="dv-mono">{{ detail.method }} {{ detail.requestUrl }}</code>
        </el-descriptions-item>
        <el-descriptions-item :label="t('audit.requestParams')">
          <pre class="dv-mono params">{{ prettyParams }}</pre>
        </el-descriptions-item>
        <el-descriptions-item :label="t('audit.userAgent')">{{ detail.userAgent || '-' }}</el-descriptions-item>
        <el-descriptions-item :label="t('audit.time')">{{ detail.createTime || '-' }}</el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button @click="detailVisible = false">{{ t('common.cancel') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
  import { computed, onMounted, ref } from 'vue'
  import { ElMessage } from 'element-plus'
  import type { AuditLog } from '@dataviz/shared-types'
  import { rowsOf, totalOf } from '@dataviz/shared-types'
  import { exportAuditLogs, getAuditLog, pageAuditLogs } from '@dataviz/api-client'
  import { downloadByBlob } from '@dataviz/shared-utils'
  import { useI18n } from 'vue-i18n'

  const { t } = useI18n()

  const loading = ref(false)
  const exporting = ref(false)
  const list = ref<AuditLog[]>([])
  const total = ref(0)
  const pageNum = ref(1)
  const pageSize = ref(20)
  const username = ref('')
  const moduleName = ref('')
  const action = ref('')

  const detailVisible = ref(false)
  const detail = ref<AuditLog | null>(null)

  const prettyParams = computed(() => {
    const raw = detail.value?.requestParams
    if (!raw) return '-'
    try {
      return JSON.stringify(JSON.parse(raw), null, 2)
    } catch {
      return raw
    }
  })

  function actionTag(value?: string): 'success' | 'warning' | 'danger' | 'info' {
    const text = (value ?? '').toLowerCase()
    if (text.includes('create') || text.includes('add')) return 'success'
    if (text.includes('update') || text.includes('edit')) return 'warning'
    if (text.includes('delete') || text.includes('remove')) return 'danger'
    return 'info'
  }

  function params() {
    return {
      username: username.value || undefined,
      module: moduleName.value || undefined,
      action: action.value || undefined,
    }
  }

  async function load() {
    loading.value = true
    try {
      const res = await pageAuditLogs({ pageNum: pageNum.value, pageSize: pageSize.value, ...params() })
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
    username.value = ''
    moduleName.value = ''
    action.value = ''
    reload()
  }

  async function openDetail(row: AuditLog) {
    detailVisible.value = true
    try {
      detail.value = await getAuditLog(row.id)
    } catch {
      detail.value = row
    }
  }

  async function onExport() {
    exporting.value = true
    try {
      const blob = await exportAuditLogs(params())
      downloadByBlob(blob, 'audit-logs.xlsx')
      ElMessage.success(t('audit.exportSuccess'))
    } catch (e) {
      ElMessage.error(t('audit.exportFailed'))
      console.error(e)
    } finally {
      exporting.value = false
    }
  }

  onMounted(load)
</script>

<style lang="scss" scoped>
  .params {
    margin: 0;
    max-height: 220px;
    overflow: auto;
    font-size: 12px;
    line-height: 1.6;
    white-space: pre-wrap;
    word-break: break-all;
  }
</style>
