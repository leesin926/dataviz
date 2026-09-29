<template>
  <div class="dv-page">
    <div class="dv-page-head">
      <div>
        <h2>{{ t('contact.title') }}</h2>
        <p class="dv-page-desc">{{ t('contact.desc') }}</p>
      </div>
      <div class="dv-page-actions">
        <el-button v-permission="'alert:write'" type="primary" @click="openCreate">
          {{ t('contact.create') }}
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
      <el-button type="primary" :loading="loading" @click="reload">{{ t('common.search') }}</el-button>
      <el-button @click="onReset">{{ t('common.reset') }}</el-button>
    </div>

    <el-card shadow="never">
      <el-table :data="list" v-loading="loading" stripe>
        <el-table-column prop="name" :label="t('contact.name')" min-width="140" show-overflow-tooltip />
        <el-table-column :label="t('contact.email')" min-width="200">
          <template #default="{ row }">
            <code v-if="row.email" class="dv-mono addr">{{ row.email }}</code>
            <span v-else class="dv-text-3">—</span>
          </template>
        </el-table-column>
        <el-table-column :label="t('contact.mobile')" min-width="140">
          <template #default="{ row }">
            <code v-if="row.mobile" class="dv-mono addr">{{ row.mobile }}</code>
            <span v-else class="dv-text-3">—</span>
          </template>
        </el-table-column>
        <el-table-column :label="t('contact.groupCount')" width="130">
          <template #default="{ row }">
            <span v-if="groupsOf(row) > 0">{{ t('contact.groupCountValue', { n: groupsOf(row) }) }}</span>
            <span v-else class="dv-text-3">{{ t('contact.groupCountNone') }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="remark" :label="t('contact.remark')" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.remark">{{ row.remark }}</span>
            <span v-else class="dv-text-3">—</span>
          </template>
        </el-table-column>
        <el-table-column :label="t('common.status')" width="110">
          <template #default="{ row }">
            <el-switch
              :model-value="row.enabled"
              :disabled="!canWrite"
              :loading="togglingId === row.id"
              :active-text="t('common.enabled')"
              @change="(v: boolean) => onToggle(row, v)"
            />
          </template>
        </el-table-column>
        <el-table-column prop="updateTime" :label="t('common.updatedAt')" width="170" />
        <el-table-column :label="t('common.operations')" width="150" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="'alert:write'" link type="primary" @click="openEdit(row)">
              {{ t('common.edit') }}
            </el-button>
            <el-button v-permission="'alert:write'" link type="danger" @click="onDelete(row)">
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

    <el-dialog v-model="dialogVisible" :title="editingId ? t('contact.editTitle') : t('contact.createTitle')" width="560px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item :label="t('contact.name')" prop="name">
          <el-input v-model="form.name" :placeholder="t('contact.namePlaceholder')" maxlength="64" show-word-limit />
        </el-form-item>
        <el-form-item :label="t('contact.email')" prop="email">
          <el-input v-model="form.email" class="dv-mono" :placeholder="t('contact.emailPlaceholder')" maxlength="128" />
        </el-form-item>
        <el-form-item :label="t('contact.mobile')" prop="mobile">
          <el-input v-model="form.mobile" class="dv-mono" :placeholder="t('contact.mobilePlaceholder')" maxlength="24" />
        </el-form-item>
        <el-form-item>
          <!-- 判据在后端，这里也判一遍：必填没填必须是框下面的红字，不该是一趟网络往返之后的服务端消息 -->
          <span class="dv-text-3 hint">{{ t('contact.reachableHint') }}</span>
        </el-form-item>
        <el-form-item :label="t('contact.remark')" prop="remark">
          <el-input
            v-model="form.remark"
            type="textarea"
            :rows="2"
            maxlength="256"
            show-word-limit
            :placeholder="t('contact.remarkPlaceholder')"
          />
        </el-form-item>
        <el-form-item :label="t('common.status')">
          <el-switch v-model="form.enabled" :active-text="t('common.enabled')" />
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
  import { computed, nextTick, onMounted, reactive, ref } from 'vue'
  import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
  import type { AlertContact, AlertContactPayload } from '@dataviz/shared-types'
  import { rowsOf, totalOf } from '@dataviz/shared-types'
  import { createContact, deleteContact, pageContacts, toggleContact, updateContact } from '@dataviz/api-client'
  import { useI18n } from 'vue-i18n'
  import { hasSessionPermission } from '@dataviz/permission'

  const { t } = useI18n()
  const canWrite = hasSessionPermission('alert:write')

  const loading = ref(false)
  const saving = ref(false)
  const list = ref<AlertContact[]>([])
  const total = ref(0)
  const pageNum = ref(1)
  const pageSize = ref(20)
  const keyword = ref('')
  const togglingId = ref<string | number>('')

  const dialogVisible = ref(false)
  const editingId = ref<string | number>('')
  const formRef = ref<FormInstance>()
  const form = reactive({ name: '', email: '', mobile: '', remark: '', enabled: true })

  /** 与后端 AlertContactServiceImpl 的同名判据保持一致（后端还会去掉空格和横线） */
  const EMAIL_RE = /^[^\s@,;]+@[^\s@,;]+\.[^\s@,;]+$/
  const MOBILE_RE = /^\+?[\d\- ]{5,24}$/

  /**
   * 「至少填一个」是跨字段的判据，async-validator 没有这种东西的原子表达：
   * 所以把它写成两个字段共用一个校验器 —— 空邮箱在手机号已填时必须通过，反之亦然，
   * 而两个都空时两个框下面同时出红字（漏填的人不该自己去猜是哪个栏位的问题）。
   */
  function requireAddress(_rule: unknown, _value: unknown, callback: (error?: Error) => void) {
    if (!form.email.trim() && !form.mobile.trim()) {
      callback(new Error(t('contact.reachableRequired')))
      return
    }
    callback()
  }

  const rules = computed<FormRules>(() => ({
    name: [{ required: true, message: t('contact.nameRequired'), trigger: 'blur' }],
    email: [
      { validator: requireAddress, trigger: 'blur' },
      { pattern: EMAIL_RE, message: t('contact.emailInvalid'), trigger: 'blur' },
    ],
    mobile: [
      { validator: requireAddress, trigger: 'blur' },
      { pattern: MOBILE_RE, message: t('contact.mobileInvalid'), trigger: 'blur' },
    ],
  }))

  function groupsOf(row: AlertContact): number {
    return Number(row.groupCount ?? 0)
  }

  async function load() {
    loading.value = true
    try {
      const res = await pageContacts({
        pageNum: pageNum.value,
        pageSize: pageSize.value,
        keyword: keyword.value || undefined,
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
    reload()
  }

  function openCreate() {
    editingId.value = ''
    form.name = ''
    form.email = ''
    form.mobile = ''
    form.remark = ''
    form.enabled = true
    showDialog()
  }

  function openEdit(row: AlertContact) {
    editingId.value = row.id
    form.name = row.name ?? ''
    form.email = row.email ?? ''
    form.mobile = row.mobile ?? ''
    form.remark = row.remark ?? ''
    form.enabled = row.enabled !== false
    showDialog()
  }

  /** 校验态要跟着这次编辑走：上一次的红字留在下一次打开的弹窗里，等于在说一件这次没发生的事 */
  function showDialog() {
    dialogVisible.value = true
    nextTick(() => formRef.value?.clearValidate())
  }

  function payload(): AlertContactPayload {
    // 空栏位归一成 undefined：后端对邮箱/手机/备注三栏总是显式 SET（见 AlertContactServiceImpl#update），
    // 所以这里"不填"就真的会写成 null —— 表单是唯一事实来源，清空必须是可用的操作
    return {
      id: editingId.value || undefined,
      name: form.name.trim(),
      email: form.email.trim() || undefined,
      mobile: form.mobile.trim() || undefined,
      remark: form.remark.trim() || undefined,
      enabled: form.enabled,
    }
  }

  async function onSubmit() {
    const valid = await formRef.value?.validate().catch(() => false)
    if (!valid) return
    saving.value = true
    try {
      if (editingId.value) await updateContact(payload())
      else await createContact(payload())
      ElMessage.success(t('contact.saved'))
      dialogVisible.value = false
      load()
    } catch (e) {
      ElMessage.error((e as Error).message)
    } finally {
      saving.value = false
    }
  }

  async function onToggle(row: AlertContact, enabled: boolean) {
    togglingId.value = row.id
    try {
      await toggleContact(row.id, enabled)
    } catch (e) {
      ElMessage.error((e as Error).message)
    } finally {
      togglingId.value = ''
      // 失败也要弹回库里的真实状态：这一列的开关是一次写操作，不是本地偏好
      load()
    }
  }

  async function onDelete(row: AlertContact) {
    if (groupsOf(row) > 0) {
      // 组数在列表里就看得见，所以这里是"当场说明为什么删不掉"，而不是等后端回一条 400。
      // 真正的守卫仍在服务层（这一行的数字可能是上次加载留下的），前端挡不住它。
      await ElMessageBox.alert(t('contact.deleteTip'), t('common.warning'), { type: 'warning' }).catch(() => false)
      return
    }
    const confirmed = await ElMessageBox.confirm(
      t('common.confirmDelete', { name: row.name }),
      t('common.warning'),
      { type: 'warning' },
    ).catch(() => false)
    if (!confirmed) return
    try {
      await deleteContact(row.id)
      ElMessage.success(t('contact.deleted'))
      load()
    } catch (e) {
      ElMessage.error((e as Error).message)
    }
  }

  onMounted(load)
</script>

<style lang="scss" scoped>
  .addr {
    font-size: var(--dv-font-sm);
  }
  .hint {
    font-size: var(--dv-font-sm);
    line-height: 1.6;
  }
</style>
