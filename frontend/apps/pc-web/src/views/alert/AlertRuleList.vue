<template>
  <div class="dv-page">
    <div class="dv-page-head">
      <div>
        <h2>{{ t('alert.title') }}</h2>
        <p class="dv-page-desc">{{ t('alert.desc') }}</p>
      </div>
      <div class="dv-page-actions">
        <el-button type="primary" @click="handleCreate">
          <el-icon><Plus /></el-icon>
          {{ t('alert.createRule') }}
        </el-button>
      </div>
    </div>

    <el-card shadow="never">
      <el-table :data="list" v-loading="loading" stripe>
        <el-table-column prop="name" :label="t('alert.ruleName')" min-width="180" show-overflow-tooltip />
        <el-table-column :label="t('alert.level')" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="levelTag(row.level)" size="small" disable-transitions>{{ levelLabel(row.level) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('alert.condition')" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ conditionText(row) }}</template>
        </el-table-column>
        <el-table-column :label="t('common.status')" width="110" align="center">
          <template #default="{ row }">
            <el-switch
              v-model="row.status"
              active-value="enabled"
              inactive-value="disabled"
              @change="handleToggle(row)"
            />
          </template>
        </el-table-column>
        <el-table-column prop="lastFiredAt" :label="t('alert.lastFired')" width="180">
          <template #default="{ row }">{{ row.lastFiredAt || '-' }}</template>
        </el-table-column>
        <el-table-column :label="t('common.operations')" width="160" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleEdit(row)">{{ t('common.edit') }}</el-button>
            <el-button link type="danger" @click="handleDelete(row)">{{ t('common.delete') }}</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <span class="dv-text-3">{{ t('common.empty') }}</span>
        </template>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
  import { ref, onMounted } from 'vue'
  import { ElMessage, ElMessageBox } from 'element-plus'
  import { Plus } from '@element-plus/icons-vue'
  import { useI18n } from 'vue-i18n'
  import type { AlertRule } from '@dataviz/shared-types'
  import { rowsOf } from '@dataviz/shared-types'
  import { deleteAlertRule, listAlertRules, toggleAlertRule } from '@dataviz/api-client'

  const { t } = useI18n()
  const loading = ref(false)
  const list = ref<AlertRule[]>([])

  async function loadData() {
    loading.value = true
    try {
      const res = await listAlertRules()
      list.value = rowsOf(res)
    } catch (e) {
      ElMessage.error((e as Error).message)
    } finally {
      loading.value = false
    }
  }

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

  function conditionText(row: AlertRule): string {
    const c = row.condition
    if (!c) return '-'
    return `${row.metric || c.type} ${c.operator} ${c.value}`
  }

  async function handleToggle(row: AlertRule) {
    try {
      await toggleAlertRule(row.id, row.status === 'enabled')
      ElMessage.success(t('alert.statusUpdated'))
    } catch (e) {
      ElMessage.error((e as Error).message)
      row.status = row.status === 'enabled' ? 'disabled' : 'enabled'
    }
  }

  function handleCreate() {
    ElMessage.info(t('alert.createComingSoon'))
  }

  function handleEdit(row: AlertRule) {
    ElMessage.info(`${t('alert.editComingSoon')}: ${row.name}`)
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

  onMounted(loadData)
</script>
