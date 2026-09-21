<template>
  <div class="dv-page">
    <div class="dv-page-head">
      <div>
        <h2>{{ t('role.title') }}</h2>
        <p class="dv-page-desc">{{ t('common.appSubtitle') }}</p>
      </div>
      <div class="dv-page-actions">
        <el-button type="primary" @click="openCreate">{{ t('role.create') }}</el-button>
      </div>
    </div>

    <el-card shadow="never">
      <el-table :data="list" v-loading="loading" stripe>
        <el-table-column prop="roleName" :label="t('role.roleName')" min-width="140" />
        <el-table-column prop="roleCode" :label="t('role.roleCode')" min-width="150">
          <template #default="{ row }">
            <code class="dv-mono">{{ row.roleCode }}</code>
          </template>
        </el-table-column>
        <el-table-column prop="description" :label="t('common.description')" min-width="180" show-overflow-tooltip />
        <el-table-column prop="sortOrder" :label="t('role.sort')" width="80" align="center" />
        <el-table-column :label="t('common.status')" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" disable-transitions>
              {{ row.status === 1 ? t('common.enabled') : t('common.disabled') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('common.operations')" width="180" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">{{ t('common.edit') }}</el-button>
            <el-button link type="danger" @click="onDelete(row)">{{ t('common.delete') }}</el-button>
          </template>
        </el-table-column>
      </el-table>
      <p class="dv-stat-label dv-mt">{{ t('common.total', { n: list.length }) }}</p>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="editingId ? t('role.editTitle') : t('role.createTitle')" width="480px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="96px">
        <el-form-item :label="t('role.roleName')" prop="roleName">
          <el-input v-model="form.roleName" />
        </el-form-item>
        <el-form-item :label="t('role.roleCode')" prop="roleCode">
          <el-input v-model="form.roleCode" :placeholder="t('role.codePlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('common.description')">
          <el-input v-model="form.description" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item :label="t('common.status')">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" />
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
  import { computed, onMounted, reactive, ref } from 'vue'
  import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
  import type { Role } from '@dataviz/shared-types'
  import { rowsOf } from '@dataviz/shared-types'
  import { createRole, deleteRole, listRoles, updateRole } from '@dataviz/api-client'
  import { useI18n } from 'vue-i18n'

  const { t } = useI18n()

  const loading = ref(false)
  const saving = ref(false)
  const list = ref<Role[]>([])
  const dialogVisible = ref(false)
  const editingId = ref<string | number>('')
  const formRef = ref<FormInstance>()
  const form = reactive({ roleName: '', roleCode: '', description: '', status: 1 as 0 | 1 })

  const rules = computed<FormRules>(() => ({
    roleName: [{ required: true, message: t('role.nameRequired'), trigger: 'blur' }],
    roleCode: [{ required: true, message: t('role.codeRequired'), trigger: 'blur' }],
  }))

  async function load() {
    loading.value = true
    try {
      list.value = rowsOf(await listRoles())
    } catch (e) {
      ElMessage.error((e as Error).message)
    } finally {
      loading.value = false
    }
  }

  function openCreate() {
    editingId.value = ''
    Object.assign(form, { roleName: '', roleCode: '', description: '', status: 1 })
    dialogVisible.value = true
  }

  function openEdit(row: Role) {
    editingId.value = row.id
    Object.assign(form, {
      roleName: row.roleName,
      roleCode: row.roleCode,
      description: row.description ?? '',
      status: row.status,
    })
    dialogVisible.value = true
  }

  async function onSubmit() {
    const valid = await formRef.value?.validate().catch(() => false)
    if (!valid) return
    saving.value = true
    try {
      if (editingId.value) await updateRole(editingId.value, { ...form })
      else await createRole({ ...form })
      ElMessage.success(t('common.success'))
      dialogVisible.value = false
      load()
    } catch (e) {
      ElMessage.error((e as Error).message)
    } finally {
      saving.value = false
    }
  }

  async function onDelete(row: Role) {
    const confirmed = await ElMessageBox.confirm(t('common.confirmDelete', { name: row.roleName }), t('common.warning'), {
      type: 'warning',
    }).catch(() => false)
    if (!confirmed) return
    try {
      await deleteRole(row.id)
      ElMessage.success(t('role.deleted'))
      load()
    } catch (e) {
      ElMessage.error((e as Error).message)
    }
  }

  onMounted(load)
</script>

<style lang="scss" scoped>
  .dv-mt {
    margin-top: var(--dv-space-md);
  }
</style>
