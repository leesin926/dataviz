<template>
  <div class="dv-page">
    <div class="dv-page-head">
      <div>
        <h2>{{ t('collab.myTitle') }}</h2>
        <p class="dv-page-desc">{{ t('collab.myDesc') }}</p>
      </div>
    </div>

    <el-alert :title="t('collab.noPushTitle')" type="warning" :closable="false" show-icon class="head-alert">
      <template #default>
        <p class="alert-body">{{ t('collab.noPushDesc') }}</p>
      </template>
    </el-alert>

    <el-tabs v-model="tab" @tab-change="onTabChange">
      <el-tab-pane :label="t('collab.tabTodo')" name="todo" />
      <el-tab-pane :label="t('collab.tabMine')" name="mine" />
      <el-tab-pane :label="t('collab.tabSubs')" name="subs" />
    </el-tabs>

    <el-card v-if="tab !== 'subs'" shadow="never">
      <el-table :data="approvals" v-loading="loading" stripe>
        <el-table-column :label="t('collab.target')" min-width="180">
          <template #default="{ row }">
            <el-tag size="small" disable-transitions>{{ typeLabel(row.targetType) }}</el-tag>
            <span class="dv-mono tid">#{{ row.targetId }}</span>
          </template>
        </el-table-column>
        <el-table-column :label="t('collab.applicant')" width="120">
          <template #default="{ row }">{{ isSelf(row.applicantId) ? t('collab.me') : `#${row.applicantId}` }}</template>
        </el-table-column>
        <el-table-column :label="t('common.status')" width="110">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status)" size="small" disable-transitions>{{ statusLabel(row) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('collab.comment')" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.comment">{{ row.comment }}</span>
            <span v-else class="dv-text-3">—</span>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" :label="t('collab.appliedAt')" width="170" />
        <el-table-column :label="t('common.operations')" :width="canWrite ? 190 : 110" fixed="right">
          <template #default="{ row }">
            <template v-if="row.status === 0 && canWrite">
              <el-button link type="primary" @click="openDecide(row, true)">{{ t('collab.approve') }}</el-button>
              <el-button link type="danger" @click="openDecide(row, false)">{{ t('collab.reject') }}</el-button>
            </template>
            <el-button link type="primary" @click="openResource(row.targetType, row.targetId)">
              {{ t('collab.openResource') }}
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <span class="dv-text-3">{{ t('common.empty') }}</span>
        </template>
      </el-table>
      <el-pagination
        v-if="total > pageSize"
        class="dv-pager"
        layout="total, prev, pager, next"
        :total="total"
        :current-page="pageNum"
        :page-size="pageSize"
        @current-change="
          (p: number) => {
            pageNum = p
            loadApprovals()
          }
        "
      />
    </el-card>

    <el-card v-else shadow="never">
      <el-table :data="subs" v-loading="loading" stripe>
        <el-table-column :label="t('collab.target')" min-width="180">
          <template #default="{ row }">
            <el-tag size="small" disable-transitions>{{ typeLabel(row.targetType) }}</el-tag>
            <span class="dv-mono tid">#{{ row.targetId }}</span>
          </template>
        </el-table-column>
        <el-table-column :label="t('collab.notifyTypes')" min-width="150">
          <template #default="{ row }">
            <span v-if="row.notifyTypes">{{ row.notifyTypes }}</span>
            <span v-else class="dv-text-3">{{ t('collab.notifyTypesNone') }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" :label="t('common.createdAt')" width="170" />
        <el-table-column :label="t('common.operations')" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openResource(row.targetType, row.targetId)">
              {{ t('collab.openResource') }}
            </el-button>
            <el-button link type="primary" @click="openCollab(row)">
              {{ t('collab.drawerShort') }}
            </el-button>
            <el-button v-if="canWrite" link type="danger" :loading="unsubId === row.id" @click="dropSub(row)">
              {{ t('collab.unsubscribe') }}
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <span class="dv-text-3">{{ t('collab.noSubs') }}</span>
        </template>
      </el-table>
    </el-card>

    <el-dialog v-model="decideVisible" :title="approving ? t('collab.approveTitle') : t('collab.rejectTitle')" width="480px">
      <p class="dv-text-3">
        {{ t('collab.decideTarget', { type: typeLabel(decideRow?.targetType ?? ''), id: decideRow?.targetId ?? '' }) }}
      </p>
      <el-input
        v-model="decideComment"
        type="textarea"
        :rows="3"
        :maxlength="512"
        show-word-limit
        :placeholder="t('collab.commentPlaceholder')"
      />
      <p class="dv-text-3 shared">{{ t('collab.commentSharedColumn') }}</p>
      <template #footer>
        <el-button @click="decideVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="decideBusy" @click="submitDecide">{{ t('common.confirm') }}</el-button>
      </template>
    </el-dialog>

    <CollabDrawer
      v-model="collabVisible"
      :target-type="collabRow?.targetType ?? ''"
      :target-id="collabRow?.targetId ?? ''"
    />
  </div>
</template>

<script setup lang="ts">
  /**
   * 我的协作：待办审批（M09-03-04）、我发起的审批、我的关注。
   *
   * 这个页面不能承诺的两件事，都写成界面上的固定文案而不是埋在注释里：
   * - 审批通过不会把资源推上线，也不会通知申请人（后端 approve 只改那一行的 status）；
   * - 关注登记不会推送任何消息（notify_types 全后端零读取方，所以这里连填都不填）。
   * 权限码 collab:read / collab:write 在 deploy/sql/patch/2026-10-16-r1-permission-codes.sql 里，
   * 补丁没执行前除超管外这些接口一律 403，页面上的空态可能就是 403 而不是"真的没有单子"。
   */
  import { ref, onMounted } from 'vue'
  import { useRouter } from 'vue-router'
  import { ElMessage } from 'element-plus'
  import { useI18n } from 'vue-i18n'
  import type { ApprovalRecord, CollabSubscription } from '@dataviz/shared-types'
  import { rowsOf, totalOf } from '@dataviz/shared-types'
  import { getLocal } from '@dataviz/shared-utils'
  import { hasSessionPermission } from '@dataviz/permission'
  import { approveApproval, listApprovals, listMySubscriptions, rejectApproval, unsubscribe } from '@dataviz/api-client'
  import CollabDrawer from '@/components/CollabDrawer.vue'

  const router = useRouter()
  const { t } = useI18n()
  const canWrite = hasSessionPermission('collab:write')

  type TabKey = 'todo' | 'mine' | 'subs'

  const tab = ref<TabKey>('todo')
  const loading = ref(false)
  const approvals = ref<ApprovalRecord[]>([])
  const subs = ref<CollabSubscription[]>([])
  const total = ref(0)
  const pageNum = ref(1)
  const pageSize = ref(10)
  const unsubId = ref<string | number>('')

  const decideVisible = ref(false)
  const approving = ref(true)
  const decideRow = ref<ApprovalRecord | null>(null)
  const decideBusy = ref(false)
  const decideComment = ref('')

  const collabVisible = ref(false)
  const collabRow = ref<CollabSubscription | null>(null)

  function typeLabel(targetType: string): string {
    if (targetType === 'SCREEN') return t('collab.type.SCREEN')
    if (targetType === 'DASHBOARD') return t('collab.type.DASHBOARD')
    if (targetType === 'DATASET') return t('collab.type.DATASET')
    // 后端只给审批对象类型设了闭集，评论/关注的 targetType 没有校验：脏值原样显示比硬翻成某个类型诚实
    return targetType || '—'
  }

  function statusLabel(row: ApprovalRecord): string {
    const n = Number(row.status)
    if (n === 1) return t('collab.status.1')
    if (n === 2) return t('collab.status.2')
    if (n === 0) return t('collab.status.0')
    return row.statusLabel || String(row.status)
  }

  function statusTag(status: number): 'success' | 'danger' | 'warning' {
    if (Number(status) === 1) return 'success'
    if (Number(status) === 2) return 'danger'
    return 'warning'
  }

  function isSelf(userId?: string | number | null): boolean {
    const me = getLocal<{ id?: string | number }>('user')?.id
    return me !== undefined && String(me) === String(userId)
  }

  /** 三种审批对象各有归属页；未知类型不编不存在的路由，直接说明 */
  function openResource(targetType: string, targetId: string | number) {
    if (targetType === 'SCREEN') void router.push(`/screen/editor/${targetId}`)
    else if (targetType === 'DASHBOARD') void router.push(`/dashboard/editor/${targetId}`)
    else if (targetType === 'DATASET') void router.push(`/analysis?datasetId=${targetId}`)
    else ElMessage.warning(t('collab.unknownTarget', { type: targetType || '—' }))
  }

  function openCollab(row: CollabSubscription) {
    collabRow.value = row
    collabVisible.value = true
  }

  async function loadApprovals() {
    loading.value = true
    try {
      const res = await listApprovals({
        pageNum: pageNum.value,
        pageSize: pageSize.value,
        mine: tab.value === 'mine',
        todo: tab.value === 'todo',
      })
      approvals.value = rowsOf(res)
      total.value = totalOf(res)
    } catch (e) {
      ElMessage.error((e as Error).message)
      approvals.value = []
      total.value = 0
    } finally {
      loading.value = false
    }
  }

  async function loadSubs() {
    loading.value = true
    try {
      subs.value = await listMySubscriptions()
    } catch (e) {
      ElMessage.error((e as Error).message)
      subs.value = []
    } finally {
      loading.value = false
    }
  }

  function onTabChange() {
    pageNum.value = 1
    if (tab.value === 'subs') void loadSubs()
    else void loadApprovals()
  }

  function openDecide(row: ApprovalRecord, approve: boolean) {
    decideRow.value = row
    approving.value = approve
    decideComment.value = ''
    decideVisible.value = true
  }

  async function submitDecide() {
    const row = decideRow.value
    if (!row) return
    decideBusy.value = true
    try {
      const payload = decideComment.value.trim() ? { comment: decideComment.value.trim() } : undefined
      if (approving.value) await approveApproval(row.id, payload)
      else await rejectApproval(row.id, payload)
      decideVisible.value = false
      ElMessage.success(t('collab.decidedDone'))
      pageNum.value = 1
      await loadApprovals()
    } catch (e) {
      ElMessage.error((e as Error).message)
    } finally {
      decideBusy.value = false
    }
  }

  async function dropSub(row: CollabSubscription) {
    unsubId.value = row.id
    try {
      await unsubscribe(row.id)
      subs.value = subs.value.filter((s) => String(s.id) !== String(row.id))
      ElMessage.success(t('collab.unsubscribed'))
    } catch (e) {
      ElMessage.error((e as Error).message)
    } finally {
      unsubId.value = ''
    }
  }

  onMounted(() => {
    void loadApprovals()
  })
</script>

<style lang="scss" scoped>
  .head-alert {
    margin-bottom: 8px;
  }
  .alert-body {
    margin: 0;
    line-height: 1.6;
    font-size: var(--dv-font-sm);
  }
  .tid {
    margin-left: 8px;
    font-size: var(--dv-font-xs);
  }
  .shared {
    margin: 8px 0 0;
    line-height: 1.6;
    font-size: var(--dv-font-xs);
  }
</style>
