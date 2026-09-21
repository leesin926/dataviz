<template>
  <div class="dv-page">
    <div class="dv-page-head">
      <div>
        <h2>{{ t('model.title') }}</h2>
        <p class="dv-page-desc">{{ t('model.desc') }}</p>
      </div>
      <div class="dv-page-actions">
        <el-button type="primary" @click="handleCreate">
          <el-icon><Plus /></el-icon>
          {{ t('model.create') }}
        </el-button>
      </div>
    </div>

    <el-card shadow="never">
      <el-table :data="list" v-loading="loading" stripe>
        <el-table-column prop="name" :label="t('model.datasetName')" min-width="180" show-overflow-tooltip />
        <el-table-column prop="datasourceId" :label="t('model.datasourceId')" width="140" class-name="dv-mono" />
        <el-table-column prop="tableName" :label="t('model.tableName')" min-width="180" show-overflow-tooltip />
        <el-table-column :label="t('model.fieldCount')" width="120" align="center">
          <template #default="{ row }">{{ (row.dimensions?.length ?? 0) + (row.measures?.length ?? 0) }}</template>
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
  import { useRouter } from 'vue-router'
  import { ElMessage, ElMessageBox } from 'element-plus'
  import { Plus } from '@element-plus/icons-vue'
  import { useI18n } from 'vue-i18n'
  import type { QueryDataset } from '@dataviz/shared-types'
  import { rowsOf } from '@dataviz/shared-types'
  import { deleteDataset, listDatasets } from '@dataviz/api-client'

  const router = useRouter()
  const { t } = useI18n()
  const loading = ref(false)
  const list = ref<QueryDataset[]>([])

  async function loadData() {
    loading.value = true
    try {
      const res = await listDatasets()
      list.value = rowsOf(res)
    } catch (e) {
      ElMessage.error((e as Error).message)
    } finally {
      loading.value = false
    }
  }

  function handleCreate() {
    router.push('/analysis')
  }

  function handleEdit(row: QueryDataset) {
    router.push(`/analysis?datasetId=${row.id}`)
  }

  async function handleDelete(row: QueryDataset) {
    const confirmed = await ElMessageBox.confirm(t('common.confirmDelete', { name: row.name }), t('common.warning'), {
      type: 'warning',
    }).catch(() => false)
    if (!confirmed) return
    try {
      await deleteDataset(row.id)
      ElMessage.success(t('model.deleted'))
      loadData()
    } catch (e) {
      ElMessage.error((e as Error).message)
    }
  }

  onMounted(loadData)
</script>
