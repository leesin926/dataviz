<template>
  <div class="dv-page">
    <div class="dv-page-head">
      <div>
        <h2>{{ t('notifyGroup.title') }}</h2>
        <p class="dv-page-desc">{{ t('notifyGroup.desc') }}</p>
      </div>
      <div class="dv-page-actions">
        <el-button v-permission="'alert:write'" type="primary" @click="openCreate">
          {{ t('notifyGroup.create') }}
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
        <el-table-column prop="name" :label="t('notifyGroup.name')" min-width="150" show-overflow-tooltip />
        <el-table-column :label="t('notifyGroup.members')" min-width="260">
          <template #default="{ row }">
            <span v-if="!membersOf(row).length" class="dv-text-3">{{ t('notifyGroup.memberEmpty') }}</span>
            <div v-else class="members">
              <el-tag
                v-for="m in shown(row)"
                :key="m.id"
                size="small"
                :type="m.enabled === false ? 'info' : undefined"
                disable-transitions
              >
                {{ m.name }}
              </el-tag>
              <el-tooltip v-if="membersOf(row).length > MAX_TAGS" :content="allNames(row)" placement="top">
                <span class="more dv-text-3">+{{ membersOf(row).length - MAX_TAGS }}</span>
              </el-tooltip>
            </div>
          </template>
        </el-table-column>
        <el-table-column :label="t('notifyGroup.reachable')" width="190">
          <template #default="{ row }">
            <span v-if="!reachable(row).length" class="dv-text-3">—</span>
            <span v-else class="reach">{{ t('notifyGroup.reachableDetail', reachable(row)) }}</span>
          </template>
        </el-table-column>
        <el-table-column :label="t('notifyGroup.ruleCount')" width="130">
          <template #default="{ row }">
            <span v-if="rulesOf(row) > 0">{{ t('notifyGroup.ruleCountValue', { n: rulesOf(row) }) }}</span>
            <span v-else class="dv-text-3">{{ t('notifyGroup.ruleCountNone') }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="description" :label="t('notifyGroup.description')" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.description">{{ row.description }}</span>
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

    <el-dialog
      v-model="dialogVisible"
      :title="editingId ? t('notifyGroup.editTitle') : t('notifyGroup.createTitle')"
      width="620px"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item :label="t('notifyGroup.name')" prop="name">
          <el-input v-model="form.name" :placeholder="t('notifyGroup.namePlaceholder')" maxlength="128" show-word-limit />
        </el-form-item>
        <el-form-item :label="t('notifyGroup.description')" prop="description">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="2"
            maxlength="512"
            show-word-limit
            :placeholder="t('notifyGroup.descriptionPlaceholder')"
          />
        </el-form-item>
        <el-form-item :label="t('notifyGroup.memberPicker')" prop="contactIds">
          <el-select
            v-model="form.contactIds"
            multiple
            filterable
            :filter-method="filterMembers"
            style="width: 100%"
            :placeholder="t('notifyGroup.memberSearch')"
            @visible-change="(v: boolean) => v && (memberFilter = '')"
          >
            <el-option
              v-for="c in filteredContacts"
              :key="c.id"
              :label="c.name"
              :value="String(c.id)"
              :disabled="c.enabled === false"
            >
              <span>{{ c.name }}</span>
              <span class="opt-addr">{{ c.email || c.mobile || t('contact.noAddress') }}</span>
              <el-tag v-if="c.enabled === false" type="info" size="small" disable-transitions class="opt-tag">
                {{ t('common.disabled') }}
              </el-tag>
            </el-option>
          </el-select>
        </el-form-item>

        <!-- "这组今天到底发得出去吗"必须在勾选的当场就能看见，而不是等告警触发了才发现名单是空的 -->
        <el-form-item :label="t('notifyGroup.reachable')">
          <span v-if="!dialogReachable.email && !dialogReachable.mobile" class="dv-text-3 warn">
            {{ t('notifyGroup.unreachableWarning') }}
          </span>
          <span v-else class="reach">{{ t('notifyGroup.reachableDetail', dialogReachable) }}</span>
        </el-form-item>

        <el-form-item :label="t('common.status')">
          <el-switch v-model="form.enabled" :active-text="t('common.enabled')" />
          <span v-if="!form.enabled" class="dv-text-3 hint">{{ t('notifyGroup.disabledTip') }}</span>
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
  import type { AlertContact, AlertNotifyGroup, AlertNotifyGroupPayload } from '@dataviz/shared-types'
  import { rowsOf, totalOf } from '@dataviz/shared-types'
  import {
    createNotifyGroup,
    deleteNotifyGroup,
    listContactOptions,
    pageNotifyGroups,
    toggleNotifyGroup,
    updateNotifyGroup,
  } from '@dataviz/api-client'
  import { useI18n } from 'vue-i18n'
  import { hasSessionPermission } from '@dataviz/permission'

  const { t } = useI18n()
  const canWrite = hasSessionPermission('alert:write')

  /** 一屏成员标签的上限：多于这个数就折成 +N，鼠标移上去看全名（表格行高不该被名单撑开） */
  const MAX_TAGS = 4

  const loading = ref(false)
  const saving = ref(false)
  const list = ref<AlertNotifyGroup[]>([])
  const total = ref(0)
  const pageNum = ref(1)
  const pageSize = ref(20)
  const keyword = ref('')
  const togglingId = ref<string | number>('')

  const contacts = ref<AlertContact[]>([])
  const memberFilter = ref('')

  const dialogVisible = ref(false)
  const editingId = ref<string | number>('')
  const formRef = ref<FormInstance>()
  const form = reactive<{ name: string; description: string; enabled: boolean; contactIds: string[] }>({
    name: '',
    description: '',
    enabled: true,
    contactIds: [],
  })

  const rules = computed<FormRules>(() => ({
    name: [{ required: true, message: t('notifyGroup.nameRequired'), trigger: 'blur' }],
  }))

  /**
   * 与后端同一套计数（见 AlertNotifyGroupServiceImpl#toOptions）：停用的人算进 memberCount，
   * 但<b>不</b>算进可达数 —— 派发时确实会跳过停用联系人，界面把两者混成一个数字就是在说谎。
   */
  function reachableOf(members: AlertContact[]): { email: number; mobile: number } {
    let email = 0
    let mobile = 0
    for (const m of members) {
      if (m.enabled === false) continue
      if (m.email) email++
      if (m.mobile) mobile++
    }
    return { email, mobile }
  }

  function membersOf(row: AlertNotifyGroup): AlertContact[] {
    return row.members ?? []
  }

  function reachable(row: AlertNotifyGroup) {
    return reachableOf(membersOf(row))
  }

  function shown(row: AlertNotifyGroup): AlertContact[] {
    return membersOf(row).slice(0, MAX_TAGS)
  }

  function allNames(row: AlertNotifyGroup): string {
    return membersOf(row).map((m) => m.name).join('、')
  }

  function rulesOf(row: AlertNotifyGroup): number {
    return Number(row.ruleCount ?? 0)
  }

  const filteredContacts = computed(() => {
    const text = memberFilter.value.trim().toLowerCase()
    if (!text) return contacts.value
    return contacts.value.filter((c) => {
      const haystack = [c.name, c.email, c.mobile, c.remark].filter(Boolean).join(' ').toLowerCase()
      return haystack.includes(text)
    })
  })

  const dialogReachable = computed(() => {
    const picked = new Set(form.contactIds)
    return reachableOf(contacts.value.filter((c) => picked.has(String(c.id))))
  })

  function filterMembers(text: string) {
    memberFilter.value = text
  }

  async function load() {
    loading.value = true
    try {
      const res = await pageNotifyGroups({
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

  async function loadContacts() {
    try {
      contacts.value = await listContactOptions()
    } catch (e) {
      // 选择器空了必须说为什么空：静默的下拉框会被读成"这个系统没有联系人功能"，
      // 而真实原因可能是联系人接口挂了或者没有 alert:read
      ElMessage.error((e as Error).message)
      contacts.value = []
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

  function showDialog() {
    dialogVisible.value = true
    loadContacts()
    nextTick(() => formRef.value?.clearValidate())
  }

  function openCreate() {
    editingId.value = ''
    form.name = ''
    form.description = ''
    form.enabled = true
    form.contactIds = []
    showDialog()
  }

  function openEdit(row: AlertNotifyGroup) {
    editingId.value = row.id
    form.name = row.name ?? ''
    form.description = row.description ?? ''
    form.enabled = row.enabled !== false
    form.contactIds = membersOf(row).map((m) => String(m.id))
    showDialog()
  }

  function payload(): AlertNotifyGroupPayload {
    // contactIds 一定回传（连空数组也回传）：表单里成员是完整事实，"清空成员"必须是可做的操作。
    // 后端的 null=不改 语义留给"只改名字/启停"那条路（启停走的是独立端点，不经过这里）
    return {
      id: editingId.value || undefined,
      name: form.name.trim(),
      description: form.description.trim() || undefined,
      enabled: form.enabled,
      contactIds: form.contactIds.map((id) => Number(id)),
    }
  }

  async function onSubmit() {
    const valid = await formRef.value?.validate().catch(() => false)
    if (!valid) return
    saving.value = true
    try {
      if (editingId.value) await updateNotifyGroup(payload())
      else await createNotifyGroup(payload())
      ElMessage.success(t('notifyGroup.saved'))
      dialogVisible.value = false
      load()
    } catch (e) {
      ElMessage.error((e as Error).message)
    } finally {
      saving.value = false
    }
  }

  async function onToggle(row: AlertNotifyGroup, enabled: boolean) {
    togglingId.value = row.id
    try {
      await toggleNotifyGroup(row.id, enabled)
    } catch (e) {
      ElMessage.error((e as Error).message)
    } finally {
      togglingId.value = ''
      load()
    }
  }

  async function onDelete(row: AlertNotifyGroup) {
    if (rulesOf(row) > 0) {
      await ElMessageBox.alert(t('notifyGroup.deleteTip'), t('common.warning'), { type: 'warning' }).catch(() => false)
      return
    }
    const confirmed = await ElMessageBox.confirm(
      t('common.confirmDelete', { name: row.name }),
      t('common.warning'),
      { type: 'warning' },
    ).catch(() => false)
    if (!confirmed) return
    try {
      await deleteNotifyGroup(row.id)
      ElMessage.success(t('notifyGroup.deleted'))
      load()
    } catch (e) {
      ElMessage.error((e as Error).message)
    }
  }

  onMounted(() => {
    load()
    loadContacts()
  })
</script>

<style lang="scss" scoped>
  .members {
    display: flex;
    align-items: center;
    gap: 4px;
    flex-wrap: wrap;
  }
  .more {
    font-size: var(--dv-font-sm);
  }
  .reach {
    font-size: var(--dv-font-sm);
  }
  .warn {
    font-size: var(--dv-font-sm);
    line-height: 1.6;
  }
  .hint {
    display: block;
    font-size: var(--dv-font-sm);
    line-height: 1.6;
  }
  .opt-addr {
    float: right;
    margin-left: 12px;
    color: var(--dv-text-3);
    font-family: var(--dv-font-mono, monospace);
    font-size: var(--dv-font-xs);
  }
  .opt-tag {
    margin-left: 6px;
  }
</style>
