<template>
  <el-drawer
    :model-value="modelValue"
    :title="t('collab.drawerTitle', { name: targetName || `${targetType} #${targetId}` })"
    size="560px"
    @update:model-value="(v: boolean) => emit('update:modelValue', v)"
  >
    <div v-loading="loading" class="collab">
      <!-- 关注：后端只有登记，没有任何推送方。这一行承诺的只是"我出现在别人的关注清单里" -->
      <div class="bar">
        <el-button
          :type="subscription ? 'warning' : 'primary'"
          :plain="!!subscription"
          :disabled="!canWrite"
          :loading="subToggling"
          size="small"
          @click="toggleSubscribe"
        >
          {{ subscription ? t('collab.unsubscribe') : t('collab.subscribe') }}
        </el-button>
        <span class="dv-text-3 tip">{{ t('collab.subscribeTip') }}</span>
      </div>
      <el-alert v-if="!canWrite" :title="t('collab.readonlyTip')" type="info" :closable="false" class="alert" />

      <div v-if="canWrite" class="composer">
        <el-input
          v-model="draft"
          type="textarea"
          :rows="3"
          :maxlength="2000"
          show-word-limit
          :placeholder="t('collab.composePlaceholder')"
        />
        <div class="compose-foot">
          <span class="dv-text-3 tip">{{ t('collab.mentionTip') }}</span>
          <el-button type="primary" size="small" :loading="posting" :disabled="!draft.trim()" @click="submit">
            {{ t('collab.post') }}
          </el-button>
        </div>
        <div v-if="draftMentions.length" class="mentions">
          <span class="dv-text-3">{{ t('collab.mentionPreview') }}</span>
          <el-tag v-for="m in draftMentions" :key="m" size="small" disable-transitions>@{{ m }}</el-tag>
        </div>
      </div>

      <el-empty v-if="!loading && !comments.length" :description="t('collab.noComments')" />

      <div v-for="c in comments" :key="String(c.id)" class="cmt">
        <div class="cmt-head">
          <span class="who">{{ whoLabel(c) }}</span>
          <span class="dv-text-3 time">{{ c.createTime }}</span>
        </div>
        <p v-if="editingId !== c.id" class="body">{{ c.content }}</p>
        <el-input v-else v-model="editDraft" type="textarea" :rows="3" :maxlength="2000" />
        <div v-if="mentionsOf(c).length" class="mentions">
          <el-tag v-for="m in mentionsOf(c)" :key="m" size="small" type="info" disable-transitions>@{{ m }}</el-tag>
        </div>
        <div class="cmt-ops">
          <el-button link type="primary" @click="toggleReplies(c)">{{
            replied[c.id] ? t('collab.hideReplies') : t('collab.replies')
          }}</el-button>
          <el-button v-if="canWrite" link type="primary" @click="startReply(c)">{{ t('collab.reply') }}</el-button>
          <template v-if="canWrite">
            <el-button v-if="editingId !== c.id" link type="primary" @click="startEdit(c)">{{
              t('common.edit')
            }}</el-button>
            <template v-else>
              <el-button link type="primary" :loading="posting" @click="saveEdit(c)">{{ t('common.save') }}</el-button>
              <el-button link @click="editingId = ''">{{ t('common.cancel') }}</el-button>
            </template>
            <el-button link type="danger" @click="remove(c)">{{ t('common.delete') }}</el-button>
          </template>
        </div>

        <div v-if="replyingTo === c.id" class="reply-box">
          <el-input v-model="replyDraft" type="textarea" :rows="2" :maxlength="2000" :placeholder="t('collab.replyPlaceholder')" />
          <div class="compose-foot">
            <span class="dv-text-3 tip">{{ t('collab.replyThreadTip') }}</span>
            <el-button type="primary" size="small" :loading="posting" :disabled="!replyDraft.trim()" @click="submitReply(c)">
              {{ t('collab.post') }}
            </el-button>
          </div>
        </div>

        <div v-if="replied[c.id]" v-loading="!!replyLoading[c.id]" class="replies">
          <el-empty v-if="!repliesOf(c.id).length" :image-size="42" :description="t('collab.noReplies')" />
          <div v-for="r in repliesOf(c.id)" :key="String(r.id)" class="reply">
            <div class="cmt-head">
              <span class="who">{{ whoLabel(r) }}</span>
              <span class="dv-text-3 time">{{ r.createTime }}</span>
            </div>
            <p class="body">{{ r.content }}</p>
            <div v-if="canWrite" class="cmt-ops">
              <el-button link type="danger" @click="remove(r)">{{ t('common.delete') }}</el-button>
            </div>
          </div>
        </div>
      </div>
    </div>
  </el-drawer>
</template>

<script setup lang="ts">
  /**
   * 资源协作面板（评论 / 楼中楼 / 关注）。
   *
   * 三处"界面不能替后端吹牛"的地方，改了这段代码前先读：
   * 1. @提醒只把 `@名字` 原文写进 content，同时把名单快照存进 mentions_json。
   *    后端不解析这个字段、也没有任何推送方，所以文案只能说"记录"，不能说"通知"。
   * 2. 关注（订阅）同理：notifyTypes 全后端零读取方，因此这里根本不填它，
   *    只提供登记与取消登记；界面也不承诺"有更新会告诉你"。
   * 3. 退订需要订阅行 id，而 /subscriptions/check 只回布尔。这里改用
   *    /subscriptions/my（主体取自会话）一次拿全，再按 target 定位，省掉一次拿不到 id 的死路。
   *
   * 另：后端 update/delete 评论不校验作者（见 AT5 缺陷登记），所以 canWrite 时
   * 每条评论都给编辑与删除 —— 界面画的正是服务端真实允许的口径，不去假装"仅本人可改"。
   */
  import { computed, ref, watch } from 'vue'
  import { ElMessage, ElMessageBox } from 'element-plus'
  import { useI18n } from 'vue-i18n'
  import type { CollabComment, CollabSubscription } from '@dataviz/shared-types'
  import { getLocal } from '@dataviz/shared-utils'
  import { hasSessionPermission } from '@dataviz/permission'
  import {
    createComment,
    deleteComment,
    listComments,
    listMySubscriptions,
    listReplies,
    subscribe,
    unsubscribe,
    updateComment,
  } from '@dataviz/api-client'

  const props = defineProps<{
    modelValue: boolean
    targetType: string
    targetId: string | number
    targetName?: string
  }>()
  const emit = defineEmits<{ (e: 'update:modelValue', v: boolean): void }>()

  const { t } = useI18n()
  const canWrite = hasSessionPermission('collab:write')

  const loading = ref(false)
  const posting = ref(false)
  const subToggling = ref(false)
  const comments = ref<CollabComment[]>([])
  const replies = ref<Record<string, CollabComment[]>>({})
  const replied = ref<Record<string, boolean>>({})
  const replyLoading = ref<Record<string, boolean>>({})
  const replyingTo = ref<string | number>('')
  const mySubs = ref<CollabSubscription[]>([])

  const draft = ref('')
  const replyDraft = ref('')
  const editingId = ref<string | number>('')
  const editDraft = ref('')

  const subscription = computed(() =>
    mySubs.value.find((s) => String(s.targetType) === props.targetType && String(s.targetId) === String(props.targetId)),
  )

  /** `@张三 看一下` → ['张三']；只认非空白连续串，和后端的"原文存下来"口径一致 */
  function parseMentions(text: string): string[] {
    const out: string[] = []
    for (const m of text.match(/@([^\s@，。！？!?；;]+)/g) ?? []) {
      const name = m.slice(1)
      if (name && !out.includes(name)) out.push(name)
    }
    return out
  }

  const draftMentions = computed(() => parseMentions(draft.value))

  function mentionsOf(c: CollabComment): string[] {
    const raw = c.mentionsJson
    if (!raw) return []
    try {
      const parsed = JSON.parse(raw) as Array<{ name?: string }>
      return parsed.map((m) => m?.name).filter((n): n is string => !!n)
    } catch {
      // 存量的 mentions_json 是自由文本（后端不校验），解析不了就当没有，不把原文糊到界面上
      return []
    }
  }

  function whoLabel(c: CollabComment): string {
    const me = getLocal<{ id?: string | number }>('user')?.id
    const isMe = me !== undefined && String(me) === String(c.createBy)
    return isMe ? t('collab.me') : `${t('collab.someone')} #${c.createBy ?? '-'}`
  }

  function repliesOf(id: string | number): CollabComment[] {
    return replies.value[String(id)] ?? []
  }

  async function load() {
    loading.value = true
    try {
      comments.value = await listComments(props.targetType, props.targetId)
    } catch (e) {
      ElMessage.error((e as Error).message)
      comments.value = []
    } finally {
      loading.value = false
    }
    try {
      mySubs.value = await listMySubscriptions()
    } catch {
      // 关注清单读不到只是按钮状态未知，不能连带把评论区标成空
      mySubs.value = []
    }
  }

  async function toggleReplies(c: CollabComment) {
    const key = String(c.id)
    if (replied.value[key]) {
      replied.value = { ...replied.value, [key]: false }
      return
    }
    replied.value = { ...replied.value, [key]: true }
    if (replies.value[key]) return
    replyLoading.value = { ...replyLoading.value, [key]: true }
    try {
      replies.value = { ...replies.value, [key]: await listReplies(c.id) }
    } catch (e) {
      ElMessage.error((e as Error).message)
    } finally {
      replyLoading.value = { ...replyLoading.value, [key]: false }
    }
  }

  async function post(parentId?: string | number) {
    const text = (parentId ? replyDraft : draft).value.trim()
    if (!text) return
    posting.value = true
    try {
      await createComment({
        targetType: props.targetType,
        targetId: props.targetId,
        content: text,
        parentId: parentId ?? null,
        mentionsJson: JSON.stringify(parseMentions(text).map((name) => ({ name }))),
      })
      ;(parentId ? replyDraft : draft).value = ''
      if (parentId) {
        replyingTo.value = ''
        replies.value = { ...replies.value, [String(parentId)]: await listReplies(parentId) }
        replied.value = { ...replied.value, [String(parentId)]: true }
      } else {
        await load()
      }
      ElMessage.success(t('collab.posted'))
    } catch (e) {
      ElMessage.error((e as Error).message)
    } finally {
      posting.value = false
    }
  }

  function submit() {
    return post()
  }

  function startReply(c: CollabComment) {
    replyingTo.value = c.id
    replyDraft.value = ''
  }

  function submitReply(c: CollabComment) {
    return post(c.id)
  }

  function startEdit(c: CollabComment) {
    editingId.value = c.id
    editDraft.value = c.content
  }

  async function saveEdit(c: CollabComment) {
    const text = editDraft.value.trim()
    if (!text) {
      ElMessage.warning(t('collab.emptyNotAllowed'))
      return
    }
    posting.value = true
    try {
      // 后端 PUT 只收 content：这条评论原本的 @名单不会跟着正文一起刷新
      await updateComment(c.id, text)
      editingId.value = ''
      await load()
      ElMessage.success(t('collab.updated'))
    } catch (e) {
      ElMessage.error((e as Error).message)
    } finally {
      posting.value = false
    }
  }

  async function remove(c: CollabComment) {
    const confirmed = await ElMessageBox.confirm(t('collab.deleteCommentConfirm'), t('common.warning'), {
      type: 'warning',
    }).catch(() => false)
    if (!confirmed) return
    try {
      await deleteComment(c.id)
      ElMessage.success(t('collab.deletedComment'))
      await load()
    } catch (e) {
      ElMessage.error((e as Error).message)
    }
  }

  async function toggleSubscribe() {
    subToggling.value = true
    try {
      const existing = subscription.value
      if (existing?.id) {
        await unsubscribe(existing.id)
        mySubs.value = mySubs.value.filter((s) => String(s.id) !== String(existing.id))
        ElMessage.success(t('collab.unsubscribed'))
      } else {
        const id = await subscribe({ targetType: props.targetType, targetId: props.targetId })
        mySubs.value = [
          ...mySubs.value,
          { id, userId: '', targetType: props.targetType, targetId: props.targetId },
        ]
        ElMessage.success(t('collab.subscribed'))
      }
    } catch (e) {
      ElMessage.error((e as Error).message)
    } finally {
      subToggling.value = false
    }
  }

  watch(
    () => [props.modelValue, props.targetType, props.targetId] as const,
    (v) => {
      if (!v[0]) {
        replyingTo.value = ''
        editingId.value = ''
        return
      }
      draft.value = ''
      replied.value = {}
      replies.value = {}
      void load()
    },
    { immediate: true },
  )
</script>

<style lang="scss" scoped>
  .collab {
    display: flex;
    flex-direction: column;
    gap: 12px;
  }
  .bar {
    display: flex;
    align-items: center;
    gap: 10px;
  }
  .tip {
    font-size: var(--dv-font-xs);
    line-height: 1.5;
  }
  .alert {
    margin-bottom: 4px;
  }
  .composer {
    border: 1px solid var(--dv-border);
    border-radius: var(--dv-radius-md, 8px);
    padding: 10px;
    display: flex;
    flex-direction: column;
    gap: 8px;
  }
  .compose-foot {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
  }
  .mentions {
    display: flex;
    align-items: center;
    flex-wrap: wrap;
    gap: 6px;
    font-size: var(--dv-font-xs);
  }
  .cmt {
    border-bottom: 1px solid var(--dv-border);
    padding: 10px 0;
  }
  .cmt-head {
    display: flex;
    align-items: baseline;
    justify-content: space-between;
    gap: 10px;
  }
  .who {
    font-size: var(--dv-font-sm);
    font-weight: 600;
  }
  .time {
    font-size: var(--dv-font-xs);
    font-family: var(--dv-font-mono, monospace);
  }
  .body {
    margin: 6px 0 0;
    white-space: pre-wrap;
    word-break: break-word;
    line-height: 1.6;
  }
  .cmt-ops {
    display: flex;
    align-items: center;
    gap: 4px;
    margin-top: 6px;
  }
  .reply-box {
    margin-top: 8px;
    display: flex;
    flex-direction: column;
    gap: 6px;
  }
  .replies {
    margin: 8px 0 0 16px;
    padding-left: 12px;
    border-left: 2px solid var(--dv-border);
  }
  .reply {
    padding: 6px 0;
  }
</style>
