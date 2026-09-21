<template>
  <div class="dv-page">
    <div class="dv-page-head">
      <div>
        <h2>{{ t('screen.list') }}</h2>
        <p class="dv-page-desc">{{ t('screen.desc') }}</p>
      </div>
      <div class="dv-page-actions">
        <el-button type="primary" @click="handleCreate">
          <el-icon><Plus /></el-icon>
          {{ t('screen.create') }}
        </el-button>
      </div>
    </div>

    <div class="dv-toolbar">
      <el-input
        v-model="keyword"
        :placeholder="t('screen.searchPlaceholder')"
        clearable
        style="width: 240px"
        @keyup.enter="reload"
        @clear="reload"
      />
      <el-select v-model="statusFilter" :placeholder="t('common.status')" clearable style="width: 130px" @change="reload">
        <el-option :label="t('screen.draft')" value="draft" />
        <el-option :label="t('screen.published')" value="published" />
      </el-select>
      <el-button type="primary" :loading="loading" @click="reload">{{ t('common.search') }}</el-button>
      <el-button @click="onReset">{{ t('common.reset') }}</el-button>
    </div>

    <div v-loading="loading" class="dv-grid">
      <div v-for="s in list" :key="s.id" class="dv-item-card screen-card">
        <div class="cover" :style="{ backgroundImage: s.cover ? `url(${s.cover})` : undefined }">
          <span v-if="!s.cover" class="cover-placeholder">
            <el-icon><Picture /></el-icon>
          </span>
          <el-tag class="cover-tag" :type="s.status === 'published' ? 'success' : 'info'" size="small" effect="dark">
            {{ s.status === 'published' ? t('screen.published') : t('screen.draft') }}
          </el-tag>
        </div>
        <div class="meta">
          <div class="name dv-ellipsis">{{ s.name }}</div>
          <div class="sub dv-ellipsis">{{ s.description || '-' }}</div>
          <div class="foot">
            <span class="dv-mono size">{{ s.width }}×{{ s.height }}</span>
            <span class="time">{{ s.updatedAt || s.createdAt }}</span>
          </div>
        </div>
        <div class="ops">
          <el-button link type="primary" @click="handleEdit(s)">{{ t('common.edit') }}</el-button>
          <el-button link type="primary" @click="handlePreview(s)">{{ t('common.preview') }}</el-button>
          <el-button v-if="s.status !== 'published'" link type="primary" @click="handlePublish(s)">{{
            t('common.publish')
          }}</el-button>
          <el-button v-else link @click="handleUnpublish(s)">{{ t('common.unpublish') }}</el-button>
          <el-button link type="success" @click="handleShare(s)">{{ t('screen.shareLink') }}</el-button>
          <el-dropdown trigger="click" @command="(c: string) => onCommand(c, s)">
            <el-button link><el-icon><More /></el-icon></el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="clone">{{ t('common.clone') }}</el-dropdown-item>
                <el-dropdown-item command="delete" divided>{{ t('common.delete') }}</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </div>

      <el-empty v-if="!loading && !list.length" :description="t('common.empty')" />
    </div>

    <el-pagination
      v-if="total > pageSize"
      class="dv-pager"
      :current-page="pageNum"
      :page-size="pageSize"
      :total="total"
      layout="total, prev, pager, next"
      @current-change="handlePageChange"
    />

    <el-dialog v-model="shareVisible" :title="t('screen.shareLink')" width="560px">
      <p class="share-name">{{ sharing?.name }}</p>
      <el-input v-model="shareUrl" readonly>
        <template #append>
          <el-button @click="copy(shareUrl)">{{ t('screen.copyLink') }}</el-button>
        </template>
      </el-input>
      <p class="share-tip">{{ t('screen.shareEmbedTip') }}</p>
      <el-input :model-value="embedCode" readonly type="textarea" :rows="3" class="dv-mono embed" />
      <template #footer>
        <el-button @click="shareVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="copy(embedCode)">{{ t('screen.copyEmbed') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
  import { computed, ref, onMounted } from 'vue'
  import { useRouter } from 'vue-router'
  import { ElMessage, ElMessageBox } from 'element-plus'
  import { More, Picture, Plus } from '@element-plus/icons-vue'
  import { useI18n } from 'vue-i18n'
  import type { Screen } from '@dataviz/shared-types'
  import { rowsOf, totalOf } from '@dataviz/shared-types'
  import {
    cloneScreen,
    deleteScreen,
    publishScreen,
    shareScreen,
    listScreens,
    unpublishScreen,
  } from '@dataviz/api-client'

  const router = useRouter()
  const { t } = useI18n()

  const loading = ref(false)
  const list = ref<Screen[]>([])
  const total = ref(0)
  const pageNum = ref(1)
  const pageSize = ref(12)
  const keyword = ref('')
  const statusFilter = ref('')

  const shareVisible = ref(false)
  const sharing = ref<Screen | null>(null)
  const shareUrl = ref('')

  const embedCode = computed(() => {
    if (!shareUrl.value) return ''
    return `<iframe src="${shareUrl.value}" width="1920" height="1080" frameborder="0" allowfullscreen></iframe>`
  })

  async function loadData() {
    loading.value = true
    try {
      const res = await listScreens({
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

  function handlePageChange(p: number) {
    pageNum.value = p
    loadData()
  }

  function handleCreate() {
    router.push('/screen/editor')
  }

  function handleEdit(s: Screen) {
    router.push(`/screen/editor/${s.id}`)
  }

  function handlePreview(s: Screen) {
    router.push(`/screen/preview/${s.id}`)
  }

  async function handlePublish(s: Screen) {
    try {
      await publishScreen(s.id)
      ElMessage.success(t('screen.publishSuccess'))
      loadData()
    } catch (e) {
      ElMessage.error((e as Error).message)
    }
  }

  async function handleUnpublish(s: Screen) {
    try {
      await unpublishScreen(s.id)
      ElMessage.success(t('screen.unpublishSuccess'))
      loadData()
    } catch (e) {
      ElMessage.error((e as Error).message)
    }
  }

  async function handleClone(s: Screen) {
    try {
      await cloneScreen(s.id, `${s.name} ${t('screen.cloneSuffix')}`)
      ElMessage.success(t('screen.cloneSuccess'))
      loadData()
    } catch (e) {
      ElMessage.error((e as Error).message)
    }
  }

  async function handleDelete(s: Screen) {
    const confirmed = await ElMessageBox.confirm(t('common.confirmDelete', { name: s.name }), t('common.warning'), {
      type: 'warning',
    }).catch(() => false)
    if (!confirmed) return
    try {
      await deleteScreen(s.id)
      ElMessage.success(t('screen.deleteSuccess'))
      loadData()
    } catch (e) {
      ElMessage.error((e as Error).message)
    }
  }

  async function handleShare(s: Screen) {
    if (s.status !== 'published') {
      ElMessage.warning(t('screen.publishFirst'))
      return
    }
    try {
      const token = s.shareToken || (await shareScreen(s.id))
      sharing.value = s
      shareUrl.value = `${window.location.origin}/s/${token}`
      shareVisible.value = true
    } catch (e) {
      ElMessage.error((e as Error).message)
    }
  }

  async function copy(text: string) {
    if (!text) return
    try {
      await navigator.clipboard.writeText(text)
      ElMessage.success(t('screen.linkCopied'))
    } catch {
      const ta = document.createElement('textarea')
      ta.value = text
      ta.style.position = 'fixed'
      ta.style.opacity = '0'
      document.body.appendChild(ta)
      ta.select()
      document.execCommand('copy')
      document.body.removeChild(ta)
      ElMessage.success(t('screen.linkCopied'))
    }
  }

  function onCommand(cmd: string, s: Screen) {
    if (cmd === 'clone') handleClone(s)
    else if (cmd === 'delete') handleDelete(s)
  }

  onMounted(loadData)
</script>

<style lang="scss" scoped>
  .screen-card {
    display: flex;
    flex-direction: column;
    padding: 0;
    overflow: hidden;

    .cover {
      position: relative;
      height: 150px;
      background: var(--dv-grad-glow), var(--dv-dark-bg);
      background-size: cover;
      background-position: center;
      display: grid;
      place-items: center;
    }

    .cover-placeholder {
      font-size: 28px;
      color: rgba(184, 210, 245, 0.4);
    }

    .cover-tag {
      position: absolute;
      top: var(--dv-space-sm);
      right: var(--dv-space-sm);
    }

    .meta {
      flex: 1;
      padding: var(--dv-space-md) var(--dv-space-lg) 0;

      .name {
        font-size: var(--dv-font-lg);
        font-weight: 600;
        color: var(--dv-text-1);
      }

      .sub {
        margin-top: 2px;
        font-size: var(--dv-font-xs);
        color: var(--dv-text-3);
        min-height: 18px;
      }

      .foot {
        display: flex;
        justify-content: space-between;
        align-items: center;
        margin-top: var(--dv-space-sm);
        font-size: var(--dv-font-xs);
        color: var(--dv-text-3);

        .size {
          color: var(--dv-primary);
        }
      }
    }

    .ops {
      display: flex;
      align-items: center;
      gap: 2px;
      padding: var(--dv-space-sm) var(--dv-space-lg);
      border-top: 1px solid var(--dv-border-soft);
    }
  }

  .share-name {
    margin: 0 0 var(--dv-space-md);
    font-weight: 600;
    color: var(--dv-text-1);
  }

  .share-tip {
    margin: var(--dv-space-md) 0 var(--dv-space-sm);
    font-size: var(--dv-font-sm);
    color: var(--dv-text-3);
  }

  .embed {
    margin-top: var(--dv-space-sm);
  }
</style>
