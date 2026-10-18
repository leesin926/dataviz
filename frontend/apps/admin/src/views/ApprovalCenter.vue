<template>
  <div class="dv-page">
    <div class="dv-page-head">
      <div>
        <h2>{{ t('approval.title') }}</h2>
        <p class="dv-page-desc">{{ t('approval.desc') }}</p>
      </div>
      <div class="dv-page-actions">
        <el-button :loading="loading" @click="load">{{ t('common.refresh') }}</el-button>
      </div>
    </div>

    <div class="dv-toolbar">
      <el-radio-group v-model="scope" @change="reload">
        <el-radio-button value="todo">{{ t('approval.scopeTodo') }}</el-radio-button>
        <el-radio-button value="mine">{{ t('approval.scopeMine') }}</el-radio-button>
        <el-radio-button value="all">{{ t('approval.scopeAll') }}</el-radio-button>
      </el-radio-group>
      <el-select v-model="status" clearable :placeholder="t('approval.statusAll')" style="width: 140px" @change="reload">
        <el-option :label="t('approval.status.0')" :value="0" />
        <el-option :label="t('approval.status.1')" :value="1" />
        <el-option :label="t('approval.status.2')" :value="2" />
      </el-select>
      <el-select v-model="targetType" clearable :placeholder="t('approval.targetAll')" style="width: 150px" @change="reload">
        <el-option :label="t('approval.type.SCREEN')" value="SCREEN" />
        <el-option :label="t('approval.type.DASHBOARD')" value="DASHBOARD" />
        <el-option :label="t('approval.type.DATASET')" value="DATASET" />
      </el-select>
    </div>

    <!-- 裁决只改这张单子的状态：既不会把大屏/看板推上线，也不会通知申请人 -->
    <el-alert class="notice" type="info" :closable="false" :title="t('approval.noAutoPublish')" show-icon />

    <el-card shadow="never">
      <el-table :data="list" v-loading="loading" stripe>
        <el-table-column :label="t('approval.target')" min-width="180">
          <template #default="{ row }">
            <span>{{ targetText(row.targetType) }}</span>
            <span class="mono dv-text-3"> #{{ row.targetId }}</span>
          </template>
        </el-table-column>
        <el-table-column :label="t('approval.applicant')" width="130">
          <template #default="{ row }">
            <span class="mono">{{ row.applicantId }}</span>
            <span v-if="isSelf(row.applicantId)" class="dv-text-3">{{ t('approval.meTag') }}</span>
          </template>
        </el-table-column>
        <el-table-column :label="t('common.status')" width="120">
          <template #default="{ row }">
            <el-tag :type="statusTag(row)" size="small" disable-transitions>{{ statusText(row) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="comment" :label="t('approval.comment')" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.comment">{{ row.comment }}</span>
            <span v-else class="dv-text-3">—</span>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" :label="t('common.createdAt')" width="170" />
        <el-table-column :label="t('approval.approveTime')" width="170">
          <template #default="{ row }">{{ row.approveTime || '—' }}</template>
        </el-table-column>
        <el-table-column :label="t('common.operations')" width="150" fixed="right">
          <template #default="{ row }">
            <template v-if="row.status === 0 && canWrite">
              <el-button link type="success" @click="openDecide(row, true)">{{ t('approval.approve') }}</el-button>
              <el-button link type="danger" @click="openDecide(row, false)">{{ t('approval.reject') }}</el-button>
            </template>
            <span v-else class="dv-text-3">
              {{ row.status !== 0 ? t('approval.decided') : t('approval.readonly') }}
            </span>
          </template>
        </el-table-column>
        <template #empty><span class="dv-text-3">{{ t('common.empty') }}</span></template>
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

    <el-dialog v-model="decideVisible" :title="decidingApprove ? t('approval.approveTitle') : t('approval.rejectTitle')" width="520px">
      <p class="dv-page-desc">{{ t('approval.decideTarget', { type: targetText(decideRow?.targetType), id: decideRow?.targetId }) }}</p>
      <el-input
        v-model="decideComment"
        type="textarea"
        :rows="3"
        maxlength="512"
        show-word-limit
        :placeholder="t('approval.commentPlaceholder')"
      />
      <span class="hint dv-text-3">{{ t('approval.commentSharedColumn') }}</span>
      <template #footer>
        <el-button @click="decideVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button :type="decidingApprove ? 'primary' : 'danger'" :loading="saving" @click="onDecide">
          {{ decidingApprove ? t('approval.approve') : t('approval.reject') }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
  import { onMounted, ref } from 'vue'
  import { ElMessage } from 'element-plus'
  import type { ApprovalRecord } from '@dataviz/shared-types'
  import { rowsOf, totalOf } from '@dataviz/shared-types'
  import { approveApproval, listApprovals, rejectApproval } from '@dataviz/api-client'
  import { useI18n } from 'vue-i18n'
  import { hasSessionPermission } from '@dataviz/permission'
  import { getLocal } from '@dataviz/shared-utils'

  const { t } = useI18n()
  const canWrite = hasSessionPermission('collab:write')

  const loading = ref(false)
  const saving = ref(false)
  const list = ref<ApprovalRecord[]>([])
  const total = ref(0)
  const pageNum = ref(1)
  const pageSize = ref(20)
  const scope = ref<'todo' | 'mine' | 'all'>('todo')
  const status = ref<number | ''>('')
  const targetType = ref('')

  const decideVisible = ref(false)
  const decidingApprove = ref(true)
  const decideRow = ref<ApprovalRecord | null>(null)
  const decideComment = ref('')

  function targetText(value?: string): string {
    if (!value) return '—'
    const key = `approval.type.${value}`
    const text = t(key)
    return text === key ? value : text
  }

  /**
   * 状态文案优先用后端给的 statusLabel 之外的 i18n：
   * 后端已经算了中文标签，但那是服务端文案、英文界面用不了，
   * 所以这里按数字码翻译，翻译不出来（脏数据）才退回后端的 label 原文。
   */
  function statusText(row: ApprovalRecord): string {
    const key = `approval.status.${row.status}`
    const text = t(key)
    return text === key ? row.statusLabel || String(row.status) : text
  }

  function statusTag(row: ApprovalRecord): 'warning' | 'success' | 'danger' | 'info' {
    if (row.status === 0) return 'warning'
    if (row.status === 1) return 'success'
    if (row.status === 2) return 'danger'
    return 'info'
  }

  /** 登录页把用户存在 localStorage 的 user 里（LoginView#saveSession），这里只取 id 用来标"这是我提的单子" */
  function isSelf(userId?: string | number | null): boolean {
    const me = getLocal<{ id?: string | number }>('user')?.id
    if (me === undefined || me === null || userId === undefined || userId === null) return false
    return String(me) === String(userId)
  }

  async function load() {
    loading.value = true
    try {
      const res = await listApprovals({
        pageNum: pageNum.value,
        pageSize: pageSize.value,
        status: status.value === '' ? undefined : status.value,
        targetType: targetType.value || undefined,
        mine: scope.value === 'mine',
        todo: scope.value === 'todo',
      })
      list.value = rowsOf<ApprovalRecord>(res)
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

  function openDecide(row: ApprovalRecord, approve: boolean) {
    decideRow.value = row
    decidingApprove.value = approve
    decideComment.value = ''
    decideVisible.value = true
  }

  async function onDecide() {
    if (!decideRow.value) return
    saving.value = true
    try {
      const payload = decideComment.value.trim() ? { comment: decideComment.value.trim() } : undefined
      if (decidingApprove.value) await approveApproval(decideRow.value.id, payload)
      else await rejectApproval(decideRow.value.id, payload)
      ElMessage.success(t('approval.decidedDone'))
      decideVisible.value = false
      load()
    } catch (e) {
      ElMessage.error((e as Error).message)
    } finally {
      saving.value = false
    }
  }

  onMounted(load)
</script>

<style lang="scss" scoped>
  .notice {
    margin-bottom: 12px;
  }
  .mono {
    font-family: var(--dv-font-mono, monospace);
  }
  .hint {
    display: block;
    font-size: var(--dv-font-xs);
    line-height: 1.6;
  }
</style>
