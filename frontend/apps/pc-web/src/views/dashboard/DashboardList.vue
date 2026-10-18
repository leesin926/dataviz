<template>
  <div class="dv-page">
    <div class="dv-page-head">
      <div>
        <h2>{{ t('dashboard.title') }}</h2>
        <p class="dv-page-desc">{{ t('dashboard.desc') }}</p>
      </div>
      <div class="dv-page-actions">
        <el-button type="primary" @click="handleCreate">
          <el-icon><Plus /></el-icon>
          {{ t('dashboard.create') }}
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
      <el-select v-model="statusFilter" :placeholder="t('common.status')" clearable style="width: 130px" @change="reload">
        <el-option :label="t('dashboard.statusDraft')" value="draft" />
        <el-option :label="t('dashboard.statusPublished')" value="published" />
        <el-option :label="t('dashboard.statusArchived')" value="archived" />
      </el-select>
      <el-button type="primary" :loading="loading" @click="reload">{{ t('common.search') }}</el-button>
      <el-button @click="onReset">{{ t('common.reset') }}</el-button>
    </div>

    <el-card shadow="never">
      <el-table :data="list" v-loading="loading" stripe>
        <el-table-column prop="name" :label="t('common.name')" min-width="160" show-overflow-tooltip />
        <el-table-column prop="description" :label="t('common.description')" min-width="180" show-overflow-tooltip />
        <el-table-column :label="t('common.status')" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small" disable-transitions>{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" :label="t('common.createdAt')" width="180" />
        <el-table-column :label="t('common.operations')" width="300" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleEdit(row)">{{ t('common.edit') }}</el-button>
            <el-button link type="primary" @click="handlePublish(row)">{{ t('common.publish') }}</el-button>
            <el-button v-permission="'collab:write'" link type="primary" @click="handleApply(row)">
              {{ t('collab.apply') }}
            </el-button>
            <el-button link type="primary" @click="openCollab(row)">{{ t('collab.drawerShort') }}</el-button>
            <el-button link type="danger" @click="handleDelete(row)">{{ t('common.delete') }}</el-button>
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

    <CollabDrawer v-model="collabVisible" target-type="DASHBOARD" :target-id="collabId" :target-name="collabName" />
  </div>
</template>

<script setup lang="ts">
  import { ref, onMounted } from 'vue'
  import { useRouter } from 'vue-router'
  import { ElMessage, ElMessageBox } from 'element-plus'
  import { Plus } from '@element-plus/icons-vue'
  import type { Dashboard } from '@dataviz/shared-types'
  import { rowsOf, totalOf } from '@dataviz/shared-types'
  import { applyApproval, deleteDashboard, listDashboards, publishDashboard } from '@dataviz/api-client'
  import { useI18n } from 'vue-i18n'
  import CollabDrawer from '@/components/CollabDrawer.vue'

  const router = useRouter()
  const { t } = useI18n()
  const keyword = ref('')
  const statusFilter = ref('')
  const loading = ref(false)
  const list = ref<Dashboard[]>([])
  const total = ref(0)
  const pageNum = ref(1)
  const pageSize = ref(10)

  const collabVisible = ref(false)
  const collabId = ref<string | number>('')
  const collabName = ref('')

  function openCollab(row: Dashboard) {
    collabId.value = row.id
    collabName.value = row.name ?? ''
    collabVisible.value = true
  }

  /**
   * 审批发布（M06-06-02）在这一版的后端里只是"记一张单子"：
   * 通过不会上线，驳回也不会锁住发布按钮，且这条链路上没有任何通知。
   * 所以下确认框时把这句说在前面，而不是让用户以为点了申请就进了发布流程。
   */
  async function handleApply(row: Dashboard) {
    const confirmed = await ElMessageBox.confirm(
      t('collab.applyConfirm', { name: row.name }),
      t('collab.apply'),
      { type: 'info', confirmButtonText: t('common.confirm'), cancelButtonText: t('common.cancel') },
    ).catch(() => false)
    if (!confirmed) return
    try {
      await applyApproval({ targetType: 'DASHBOARD', targetId: row.id })
      ElMessage.success(t('collab.applied'))
    } catch (e) {
      ElMessage.error((e as Error).message)
    }
  }

  function statusLabel(status: string): string {
    if (status === 'published') return t('dashboard.statusPublished')
    if (status === 'archived') return t('dashboard.statusArchived')
    return t('dashboard.statusDraft')
  }

  function statusTagType(status: string): 'success' | 'warning' | 'info' {
    if (status === 'published') return 'success'
    if (status === 'archived') return 'warning'
    return 'info'
  }

  async function loadData() {
    loading.value = true
    try {
      const res = await listDashboards({
        pageNum: pageNum.value,
        pageSize: pageSize.value,
        keyword: keyword.value || undefined,
        status: statusFilter.value || undefined,
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
    statusFilter.value = ''
    reload()
  }

  function handleCreate() {
    router.push('/dashboard/editor')
  }

  function handleEdit(row: Dashboard) {
    router.push(`/dashboard/editor/${row.id}`)
  }

  async function handlePublish(row: Dashboard) {
    try {
      await publishDashboard(row.id)
      ElMessage.success(t('dashboard.published'))
      loadData()
    } catch (e) {
      ElMessage.error((e as Error).message)
    }
  }

  async function handleDelete(row: Dashboard) {
    const confirmed = await ElMessageBox.confirm(t('common.confirmDelete', { name: row.name }), t('common.warning'), {
      type: 'warning',
    }).catch(() => false)
    if (!confirmed) return
    try {
      await deleteDashboard(row.id)
      ElMessage.success(t('dashboard.deleted'))
      loadData()
    } catch (e) {
      ElMessage.error((e as Error).message)
    }
  }

  function handlePageChange(p: number) {
    pageNum.value = p
    loadData()
  }

  onMounted(loadData)
</script>
