<template>
  <div class="dv-page">
    <div class="dv-page-head">
      <div>
        <h2>{{ t('tenant.title') }}</h2>
        <p class="dv-page-desc">{{ t('tenant.desc') }}</p>
      </div>
      <div class="dv-page-actions">
        <el-button type="primary" @click="openCreate">{{ t('tenant.create') }}</el-button>
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
      <el-select v-model="status" :placeholder="t('common.status')" clearable style="width: 140px" @change="reload">
        <el-option :label="t('tenant.active')" value="ACTIVE" />
        <el-option :label="t('common.disabled')" value="DISABLED" />
      </el-select>
      <el-button type="primary" :loading="loading" @click="reload">{{ t('common.search') }}</el-button>
      <el-button @click="onReset">{{ t('common.reset') }}</el-button>
    </div>

    <el-card shadow="never">
      <el-table :data="list" v-loading="loading" stripe>
        <el-table-column prop="name" :label="t('common.name')" min-width="150" show-overflow-tooltip />
        <el-table-column prop="code" :label="t('tenant.code')" width="140">
          <template #default="{ row }">
            <code class="dv-mono">{{ row.code }}</code>
          </template>
        </el-table-column>
        <el-table-column prop="contactName" :label="t('tenant.contactName')" width="110" />
        <el-table-column prop="contactPhone" :label="t('tenant.contactPhone')" width="130" />
        <el-table-column prop="maxUsers" :label="t('tenant.maxUsers')" width="90" align="center" />
        <el-table-column prop="expireTime" :label="t('tenant.expireTime')" width="170">
          <template #default="{ row }">{{ row.expireTime ? formatDate(row.expireTime) : t('tenant.neverExpire') }}</template>
        </el-table-column>
        <el-table-column :label="t('common.status')" width="110" align="center">
          <template #default="{ row }">
            <el-switch
              :model-value="row.status === 'ACTIVE'"
              @change="(v: boolean) => onStatusChange(row, v)"
            />
          </template>
        </el-table-column>
        <el-table-column prop="createTime" :label="t('common.createdAt')" width="170">
          <template #default="{ row }">{{ formatDate(row.createTime) }}</template>
        </el-table-column>
        <el-table-column :label="t('common.operations')" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">{{ t('common.edit') }}</el-button>
            <el-button link type="danger" @click="onDelete(row)">{{ t('common.delete') }}</el-button>
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

    <el-dialog v-model="dialogVisible" :title="editingId ? t('tenant.editTitle') : t('tenant.createTitle')" width="540px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item :label="t('common.name')" prop="name">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item :label="t('tenant.code')" prop="code">
          <el-input v-model="form.code" :disabled="!!editingId" :placeholder="t('tenant.codePlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('tenant.contactName')">
          <el-input v-model="form.contactName" />
        </el-form-item>
        <el-form-item :label="t('tenant.contactPhone')">
          <el-input v-model="form.contactPhone" />
        </el-form-item>
        <el-form-item :label="t('tenant.maxUsers')">
          <el-input-number v-model="form.maxUsers" :min="1" :max="100000" controls-position="right" />
        </el-form-item>
        <el-form-item :label="t('tenant.expireTime')">
          <el-date-picker
            v-model="form.expireTime"
            type="datetime"
            value-format="YYYY-MM-DDTHH:mm:ss"
            :placeholder="t('tenant.expireTip')"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item :label="t('tenant.config')">
          <el-input v-model="form.config" type="textarea" :rows="3" placeholder='{"theme":"dark"}' />
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
  import type { TenantRecord } from '@dataviz/shared-types'
  import { rowsOf, totalOf } from '@dataviz/shared-types'
  import { createTenant, deleteTenant, pageTenants, setTenantStatus, updateTenant } from '@dataviz/api-client'
  import { formatDate } from '@dataviz/shared-utils'
  import { useI18n } from 'vue-i18n'

  const { t } = useI18n()

  const loading = ref(false)
  const saving = ref(false)
  const list = ref<TenantRecord[]>([])
  const total = ref(0)
  const pageNum = ref(1)
  const pageSize = ref(20)
  const keyword = ref('')
  const status = ref('')

  const dialogVisible = ref(false)
  const editingId = ref<string | number>('')
  const formRef = ref<FormInstance>()
  const form = reactive({
    name: '',
    code: '',
    contactName: '',
    contactPhone: '',
    maxUsers: 50,
    expireTime: '',
    config: '',
  })

  const rules = computed<FormRules>(() => ({
    name: [{ required: true, message: t('common.name'), trigger: 'blur' }],
    code: [
      { required: true, message: t('tenant.code'), trigger: 'blur' },
      { pattern: /^[a-z0-9_-]{2,32}$/, message: t('tenant.codePlaceholder'), trigger: 'blur' },
    ],
  }))

  async function load() {
    loading.value = true
    try {
      const res = await pageTenants({
        pageNum: pageNum.value,
        pageSize: pageSize.value,
        keyword: keyword.value || undefined,
        status: status.value || undefined,
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
    load()
  }

  function onReset() {
    keyword.value = ''
    status.value = ''
    reload()
  }

  function openCreate() {
    editingId.value = ''
    Object.assign(form, { name: '', code: '', contactName: '', contactPhone: '', maxUsers: 50, expireTime: '', config: '' })
    dialogVisible.value = true
  }

  function openEdit(row: TenantRecord) {
    editingId.value = row.id
    Object.assign(form, {
      name: row.name,
      code: row.code,
      contactName: row.contactName ?? '',
      contactPhone: row.contactPhone ?? '',
      maxUsers: row.maxUsers ?? 50,
      expireTime: row.expireTime ?? '',
      config: row.config ?? '',
    })
    dialogVisible.value = true
  }

  async function onSubmit() {
    const valid = await formRef.value?.validate().catch(() => false)
    if (!valid) return
    if (form.config && !isJson(form.config)) {
      ElMessage.error(t('tenant.configInvalid'))
      return
    }
    saving.value = true
    try {
      if (editingId.value) await updateTenant({ ...form, id: editingId.value })
      else await createTenant({ ...form })
      ElMessage.success(t('common.success'))
      dialogVisible.value = false
      load()
    } catch (e) {
      ElMessage.error((e as Error).message)
    } finally {
      saving.value = false
    }
  }

  function isJson(text: string): boolean {
    try {
      JSON.parse(text)
      return true
    } catch {
      return false
    }
  }

  async function onStatusChange(row: TenantRecord, active: boolean) {
    try {
      await setTenantStatus(row.id, active)
      row.status = active ? 'ACTIVE' : 'DISABLED'
      ElMessage.success(t('tenant.statusChanged'))
    } catch (e) {
      ElMessage.error((e as Error).message)
    }
  }

  async function onDelete(row: TenantRecord) {
    const confirmed = await ElMessageBox.confirm(t('common.confirmDelete', { name: row.name }), t('common.warning'), {
      type: 'warning',
    }).catch(() => false)
    if (!confirmed) return
    try {
      await deleteTenant(row.id)
      ElMessage.success(t('tenant.deleted'))
      load()
    } catch (e) {
      ElMessage.error((e as Error).message)
    }
  }

  onMounted(load)
</script>
