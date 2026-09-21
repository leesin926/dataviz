<template>
  <div class="dv-page">
    <div class="dv-page-head">
      <div>
        <h2>{{ t('monitor.title') }}</h2>
        <p class="dv-page-desc">{{ t('monitor.desc') }}</p>
      </div>
      <div class="dv-page-actions">
        <el-button :loading="exporting" @click="handleExport">
          <el-icon><Download /></el-icon>
          {{ t('common.export') }}
        </el-button>
      </div>
    </div>

    <div class="dv-toolbar">
      <el-input
        v-model="username"
        :placeholder="t('monitor.operator')"
        clearable
        style="width: 180px"
        @keyup.enter="reload"
        @clear="reload"
      />
      <el-select v-model="module" :placeholder="t('monitor.module')" clearable style="width: 150px" @change="reload">
        <el-option v-for="m in MODULES" :key="m" :label="t(`monitor.module_${m}`)" :value="m" />
      </el-select>
      <el-input
        v-model="action"
        :placeholder="t('monitor.action')"
        clearable
        style="width: 160px"
        @keyup.enter="reload"
        @clear="reload"
      />
      <el-button type="primary" :loading="loading" @click="reload">{{ t('common.search') }}</el-button>
      <el-button @click="onReset">{{ t('common.reset') }}</el-button>
    </div>

    <el-card shadow="never">
      <el-table :data="list" v-loading="loading" stripe>
        <el-table-column prop="username" :label="t('monitor.operator')" width="130" show-overflow-tooltip />
        <el-table-column prop="module" :label="t('monitor.module')" width="120" />
        <el-table-column prop="action" :label="t('monitor.action')" width="140" show-overflow-tooltip />
        <el-table-column :label="t('monitor.request')" min-width="240" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="dv-mono">{{ row.method }} {{ row.requestUrl }}</span>
          </template>
        </el-table-column>
        <el-table-column :label="t('monitor.result')" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.responseCode === 200 ? 'success' : 'danger'" size="small" disable-transitions>
              {{ row.responseCode ?? '-' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('monitor.duration')" width="110" align="right">
          <template #default="{ row }">{{ row.executionTime ?? 0 }} ms</template>
        </el-table-column>
        <el-table-column prop="ip" :label="t('monitor.ip')" width="140" class-name="dv-mono" />
        <el-table-column prop="createTime" :label="t('common.createdAt')" width="180" />
        <el-table-column :label="t('common.operations')" width="90" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleDetail(row)">{{ t('common.detail') }}</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <span class="dv-text-3">{{ t('common.empty') }}</span>
        </template>
      </el-table>
      <el-pagination
        v-if="total > 0"
        class="dv-pager"
        :current-page="pageNum"
        :page-size="pageSize"
        :total="total"
        layout="total, prev, pager, next"
        @current-change="handlePageChange"
      />
    </el-card>

    <el-dialog v-model="detailVisible" :title="t('monitor.detailTitle')" width="640px">
      <el-descriptions v-if="detail" :column="1" border>
        <el-descriptions-item :label="t('monitor.operator')">{{ detail.username }}</el-descriptions-item>
        <el-descriptions-item :label="t('monitor.module')">{{ detail.module }}</el-descriptions-item>
        <el-descriptions-item :label="t('monitor.action')">{{ detail.action }}</el-descriptions-item>
        <el-descriptions-item :label="t('monitor.request')">
          <span class="dv-mono">{{ detail.method }} {{ detail.requestUrl }}</span>
        </el-descriptions-item>
        <el-descriptions-item :label="t('monitor.ip')">{{ detail.ip }}</el-descriptions-item>
        <el-descriptions-item :label="t('monitor.userAgent')">{{ detail.userAgent }}</el-descriptions-item>
        <el-descriptions-item :label="t('monitor.params')">
          <pre class="dv-mono params">{{ prettyParams }}</pre>
        </el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
  import { computed, ref, onMounted } from 'vue'
  import { ElMessage } from 'element-plus'
  import { Download } from '@element-plus/icons-vue'
  import { useI18n } from 'vue-i18n'
  import type { AuditLog } from '@dataviz/shared-types'
  import { rowsOf, totalOf } from '@dataviz/shared-types'
  import { exportAuditLogs, getAuditLog, pageAuditLogs } from '@dataviz/api-client'
  import { downloadByBlob } from '@dataviz/shared-utils'

  const { t } = useI18n()
  const MODULES = ['auth', 'user', 'datasource', 'dashboard', 'screen', 'alert']

  const loading = ref(false)
  const exporting = ref(false)
  const list = ref<AuditLog[]>([])
  const total = ref(0)
  const pageNum = ref(1)
  const pageSize = ref(20)
  const username = ref('')
  const module = ref('')
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

  function params() {
    return {
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      username: username.value || undefined,
      module: module.value || undefined,
      action: action.value || undefined,
    }
  }

  async function loadData() {
    loading.value = true
    try {
      const res = await pageAuditLogs(params())
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
    username.value = ''
    module.value = ''
    action.value = ''
    reload()
  }

  async function handlePageChange(p: number) {
    pageNum.value = p
    loadData()
  }

  async function handleDetail(row: AuditLog) {
    try {
      detail.value = await getAuditLog(row.id)
      detailVisible.value = true
    } catch (e) {
      ElMessage.error((e as Error).message)
    }
  }

  async function handleExport() {
    exporting.value = true
    try {
      const blob = await exportAuditLogs({
        username: username.value || undefined,
        module: module.value || undefined,
        action: action.value || undefined,
      })
      downloadByBlob(blob, 'audit-logs.xlsx')
    } catch (e) {
      ElMessage.error((e as Error).message)
    } finally {
      exporting.value = false
    }
  }

  onMounted(loadData)
</script>

<style lang="scss" scoped>
  .params {
    margin: 0;
    max-height: 220px;
    overflow: auto;
    white-space: pre-wrap;
    word-break: break-all;
    font-size: 12px;
  }
</style>
