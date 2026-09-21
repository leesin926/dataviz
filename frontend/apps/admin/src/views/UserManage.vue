<template>
  <div class="dv-page">
    <div class="dv-page-head">
      <div>
        <h2>{{ t('user.title') }}</h2>
        <p class="dv-page-desc">{{ t('common.appSubtitle') }}</p>
      </div>
      <div class="dv-page-actions">
        <el-button type="primary" @click="openCreate">{{ t('user.create') }}</el-button>
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
      <el-select v-model="status" :placeholder="t('common.status')" clearable style="width: 130px" @change="reload">
        <el-option :label="t('common.enabled')" :value="1" />
        <el-option :label="t('common.disabled')" :value="0" />
      </el-select>
      <el-button type="primary" :loading="loading" @click="reload">{{ t('common.search') }}</el-button>
      <el-button @click="reload">{{ t('common.reset') }}</el-button>
    </div>

    <el-row :gutter="14">
      <el-col :span="5">
        <el-card shadow="never">
          <template #header>{{ t('user.deptTree') }}</template>
          <el-tree
            :data="deptTree"
            :props="{ label: 'deptName', children: 'children' }"
            node-key="id"
            default-expand-all
            highlight-current
            @node-click="onDeptClick"
          />
        </el-card>
      </el-col>
      <el-col :span="19">
        <el-card shadow="never">
          <el-table :data="list" v-loading="loading" stripe>
            <el-table-column prop="username" :label="t('user.username')" min-width="120" />
            <el-table-column prop="nickname" :label="t('user.nickname')" min-width="120" />
            <el-table-column prop="email" :label="t('user.email')" min-width="160" show-overflow-tooltip />
            <el-table-column prop="phone" :label="t('user.phone')" width="130" />
            <el-table-column prop="deptName" :label="t('user.dept')" width="130" show-overflow-tooltip />
            <el-table-column :label="t('common.status')" width="90">
              <template #default="{ row }">
                <el-switch
                  :model-value="row.status"
                  :active-value="1"
                  :inactive-value="0"
                  @change="(v: number) => onStatusChange(row, v)"
                />
              </template>
            </el-table-column>
            <el-table-column :label="t('common.operations')" width="230" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="openEdit(row)">{{ t('common.edit') }}</el-button>
                <el-button link type="primary" @click="onResetPwd(row)">{{ t('user.resetPwd') }}</el-button>
                <el-button link type="danger" @click="onDelete(row)">{{ t('common.delete') }}</el-button>
              </template>
            </el-table-column>
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
      </el-col>
    </el-row>

    <el-dialog v-model="dialogVisible" :title="editingId ? t('user.editTitle') : t('user.createTitle')" width="520px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="92px">
        <el-form-item :label="t('user.username')" prop="username">
          <el-input v-model="form.username" :disabled="!!editingId" />
        </el-form-item>
        <el-form-item :label="t('user.nickname')" prop="nickname">
          <el-input v-model="form.nickname" />
        </el-form-item>
        <el-form-item v-if="!editingId" :label="t('login.password')" prop="password">
          <el-input v-model="form.password" type="password" show-password :placeholder="t('user.pwdPlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('user.email')">
          <el-input v-model="form.email" />
        </el-form-item>
        <el-form-item :label="t('user.phone')">
          <el-input v-model="form.phone" />
        </el-form-item>
        <el-form-item :label="t('user.dept')">
          <el-tree-select
            v-model="form.deptId"
            :data="deptTree"
            :props="{ label: 'deptName', children: 'children', value: 'id' }"
            check-strictly
            style="width: 100%"
          />
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
  import type { Dept, User } from '@dataviz/shared-types'
  import { rowsOf, totalOf } from '@dataviz/shared-types'
  import {
    createUser,
    deleteUser,
    getDeptTree,
    listUsers,
    resetPassword,
    setUserStatus,
    updateUser,
  } from '@dataviz/api-client'
  import { useI18n } from 'vue-i18n'

  const { t } = useI18n()

  const loading = ref(false)
  const saving = ref(false)
  const list = ref<User[]>([])
  const deptTree = ref<Dept[]>([])
  const total = ref(0)
  const pageNum = ref(1)
  const pageSize = ref(20)
  const keyword = ref('')
  const status = ref<number | ''>('')
  /** 点击部门树后作为新增用户的默认部门 */
  const pendingDeptId = ref<string | number>('')

  const dialogVisible = ref(false)
  const editingId = ref<string | number>('')
  const formRef = ref<FormInstance>()
  const form = reactive({
    username: '',
    nickname: '',
    password: '',
    email: '',
    phone: '',
    deptId: '' as string | number,
    status: 1 as 0 | 1,
  })

  const rules = computed<FormRules>(() => ({
    username: [{ required: true, message: t('login.errUsernameRequired'), trigger: 'blur' }],
    password: editingId.value ? [] : [{ required: true, message: t('login.errPasswordRequired'), trigger: 'blur' }],
  }))

  async function load() {
    loading.value = true
    try {
      const res = await listUsers({
        pageNum: pageNum.value,
        pageSize: pageSize.value,
        username: keyword.value || undefined,
        status: status.value === '' ? undefined : status.value,
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

  async function loadDepts() {
    try {
      deptTree.value = await getDeptTree()
    } catch (e) {
      ElMessage.error((e as Error).message)
    }
  }

  /** 后端用户查询暂不支持按部门过滤，点击部门仅作为新增用户的默认部门 */
  function onDeptClick(node: Dept) {
    pendingDeptId.value = node.id
    ElMessage.info(`${node.deptName} → ${t('user.create')}`)
  }

  function openCreate() {
    editingId.value = ''
    Object.assign(form, {
      username: '',
      nickname: '',
      password: '',
      email: '',
      phone: '',
      deptId: pendingDeptId.value,
      status: 1,
    })
    dialogVisible.value = true
  }

  function openEdit(row: User) {
    editingId.value = row.id
    Object.assign(form, {
      username: row.username,
      nickname: row.nickname ?? '',
      password: '',
      email: row.email ?? '',
      phone: row.phone ?? '',
      deptId: row.deptId ?? '',
      status: row.status,
    })
    dialogVisible.value = true
  }

  async function onSubmit() {
    const valid = await formRef.value?.validate().catch(() => false)
    if (!valid) return
    saving.value = true
    try {
      if (editingId.value) {
        await updateUser(editingId.value, { ...form, password: undefined })
      } else {
        await createUser({ ...form, password: form.password })
      }
      ElMessage.success(t('common.success'))
      dialogVisible.value = false
      load()
    } catch (e) {
      ElMessage.error((e as Error).message)
    } finally {
      saving.value = false
    }
  }

  async function onStatusChange(row: User, value: number) {
    try {
      await setUserStatus(row.id, value)
      row.status = value as 0 | 1
      ElMessage.success(t('user.statusChanged'))
    } catch (e) {
      ElMessage.error((e as Error).message)
    }
  }

  async function onResetPwd(row: User) {
    const input = await ElMessageBox.prompt(t('user.newPwd'), t('user.resetPwd'), {
      inputPlaceholder: t('user.pwdPlaceholder'),
    }).catch(() => null)
    if (!input?.value) return
    try {
      await resetPassword(row.id, input.value)
      ElMessage.success(t('user.pwdReset'))
    } catch (e) {
      ElMessage.error((e as Error).message)
    }
  }

  async function onDelete(row: User) {
    const confirmed = await ElMessageBox.confirm(t('common.confirmDelete', { name: row.username }), t('common.warning'), {
      type: 'warning',
    }).catch(() => false)
    if (!confirmed) return
    try {
      await deleteUser(row.id)
      ElMessage.success(t('user.deleted'))
      load()
    } catch (e) {
      ElMessage.error((e as Error).message)
    }
  }

  onMounted(() => {
    loadDepts()
    load()
  })
</script>
