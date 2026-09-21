<template>
  <div class="dv-page">
    <div class="dv-page-head">
      <div>
        <h2>{{ t('datasource.title') }}</h2>
        <p class="dv-page-desc">{{ t('datasource.desc') }}</p>
      </div>
      <div class="dv-page-actions">
        <el-button @click="loadData">{{ t('common.refresh') }}</el-button>
        <el-button type="primary" @click="handleCreate">{{ t('datasource.create') }}</el-button>
      </div>
    </div>

    <el-card shadow="never">
      <el-table :data="list" v-loading="loading" stripe>
        <el-table-column prop="name" :label="t('common.name')" min-width="150" show-overflow-tooltip />
        <el-table-column prop="type" :label="t('common.type')" width="130">
          <template #default="{ row }">
            <code class="dv-mono">{{ row.type }}</code>
          </template>
        </el-table-column>
        <el-table-column :label="t('datasource.endpoint')" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="dv-mono">{{ endpoint(row) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="description" :label="t('common.description')" min-width="150" show-overflow-tooltip />
        <el-table-column :label="t('common.status')" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status)" size="small" disable-transitions>{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('common.operations')" width="220" fixed="right">
          <template #default="{ row }">
            <el-button link @click="handleTest(row)">{{ t('datasource.testConnection') }}</el-button>
            <el-button link type="primary" @click="handleEdit(row)">{{ t('common.edit') }}</el-button>
            <el-button link type="danger" @click="handleDelete(row)">{{ t('common.delete') }}</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <span class="dv-text-3">{{ t('common.empty') }}</span>
        </template>
      </el-table>
      <p class="dv-stat-label dv-total">{{ t('common.total', { n: list.length }) }}</p>
    </el-card>

    <el-dialog
      v-model="dialogVisible"
      :title="editingId ? t('datasource.edit') : t('datasource.create')"
      width="520px"
    >
      <el-form :model="form" label-width="96px">
        <el-form-item :label="t('common.name')">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item :label="t('common.type')">
          <el-select v-model="form.type" style="width: 100%">
            <el-option v-for="opt in types" :key="opt" :label="opt" :value="opt" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('datasource.host')">
          <el-input v-model="form.config!.host" />
        </el-form-item>
        <el-form-item :label="t('datasource.port')">
          <el-input-number v-model="form.config!.port" :min="1" :max="65535" controls-position="right" />
        </el-form-item>
        <el-form-item :label="t('datasource.database')">
          <el-input v-model="form.config!.database" />
        </el-form-item>
        <el-form-item :label="t('datasource.username')">
          <el-input v-model="form.config!.username" />
        </el-form-item>
        <el-form-item :label="t('datasource.password')">
          <el-input v-model="form.config!.password" type="password" show-password autocomplete="new-password" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button :loading="testing" @click="handleTestDialog">{{ t('datasource.testConnection') }}</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">{{ t('common.save') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
  import { reactive, ref, onMounted } from 'vue'
  import { ElMessage, ElMessageBox } from 'element-plus'
  import type { Datasource, DatasourceType } from '@dataviz/shared-types'
  import { rowsOf } from '@dataviz/shared-types'
  import { createDatasource, deleteDatasource, listDatasources, testConnection, updateDatasource } from '@dataviz/api-client'
  import { useI18n } from 'vue-i18n'

  const { t } = useI18n()

  const loading = ref(false)
  const saving = ref(false)
  const testing = ref(false)
  const list = ref<Datasource[]>([])
  const dialogVisible = ref(false)
  const editingId = ref<string | number>('')
  const types: DatasourceType[] = [
    'mysql',
    'postgresql',
    'oracle',
    'sqlserver',
    'clickhouse',
    'hive',
    'mongodb',
    'restapi',
    'csv',
    'excel',
  ]

  function emptyForm() {
    return {
      name: '',
      type: 'mysql' as DatasourceType,
      description: '',
      config: { host: 'localhost', port: 3306, database: '', username: '', password: '' },
    }
  }

  const form = reactive<Partial<Datasource>>(emptyForm())

  function endpoint(row: Datasource): string {
    const cfg = row.config ?? {}
    if (cfg.host) return `${cfg.host}:${cfg.port ?? '-'}/${cfg.database ?? '-'}`
    return cfg.url || '-'
  }

  function statusLabel(status: string): string {
    if (status === 'active') return t('common.online')
    if (status === 'error') return t('common.offline')
    return t('common.unknown')
  }

  function statusTag(status: string): 'success' | 'danger' | 'info' {
    if (status === 'active') return 'success'
    if (status === 'error') return 'danger'
    return 'info'
  }

  async function loadData() {
    loading.value = true
    try {
      list.value = rowsOf(await listDatasources())
    } catch (e) {
      ElMessage.error((e as Error).message)
    } finally {
      loading.value = false
    }
  }

  function handleCreate() {
    editingId.value = ''
    Object.assign(form, emptyForm())
    dialogVisible.value = true
  }

  function handleEdit(row: Datasource) {
    editingId.value = row.id
    Object.assign(form, emptyForm(), row, { config: { ...emptyForm().config, ...(row.config ?? {}) } })
    dialogVisible.value = true
  }

  async function handleDelete(row: Datasource) {
    const confirmed = await ElMessageBox.confirm(t('common.confirmDelete', { name: row.name }), t('common.warning'), {
      type: 'warning',
    }).catch(() => false)
    if (!confirmed) return
    try {
      await deleteDatasource(row.id)
      ElMessage.success(t('datasource.deleted'))
      loadData()
    } catch (e) {
      ElMessage.error((e as Error).message)
    }
  }

  async function handleTest(row: Datasource) {
    try {
      const r = await testConnection(row)
      if (r.success) ElMessage.success(t('datasource.testOkWithLatency', { ms: r.latency ?? 0 }))
      else ElMessage.error(r.message || t('datasource.testFail'))
    } catch (e) {
      ElMessage.error((e as Error).message)
    }
  }

  async function handleTestDialog() {
    testing.value = true
    try {
      const r = await testConnection(form)
      if (r.success) ElMessage.success(t('datasource.testOk'))
      else ElMessage.error(r.message || t('datasource.testFail'))
    } catch (e) {
      ElMessage.error((e as Error).message)
    } finally {
      testing.value = false
    }
  }

  async function handleSave() {
    saving.value = true
    try {
      if (editingId.value) await updateDatasource(editingId.value, form)
      else await createDatasource(form)
      ElMessage.success(t('datasource.saved'))
      dialogVisible.value = false
      loadData()
    } catch (e) {
      ElMessage.error((e as Error).message)
    } finally {
      saving.value = false
    }
  }

  onMounted(loadData)
</script>

<style lang="scss" scoped>
  .dv-total {
    margin-top: var(--dv-space-md);
  }
</style>
