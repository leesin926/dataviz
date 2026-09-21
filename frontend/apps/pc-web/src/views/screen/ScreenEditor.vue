<template>
  <div class="screen-editor">
    <div class="editor-toolbar">
      <el-button size="small" @click="handleBack">{{ t('screen.editor.back') }}</el-button>
      <el-input
        v-model="name"
        :placeholder="t('screen.editor.namePlaceholder')"
        size="small"
        style="width: 220px; margin-left: 12px"
      />
      <el-radio-group
        :model-value="editor.activePlatform.value"
        size="small"
        style="margin-left: 16px"
        @change="onPlatformChange"
      >
        <el-radio-button v-for="p in ALL_PLATFORMS" :key="p" :value="p">{{ platformLabel(p) }}</el-radio-button>
      </el-radio-group>
      <ComponentPanel :editor="editor" />
      <el-dropdown trigger="click" style="margin-left: 12px" @command="onCopyTo">
        <el-button size="small"
          >{{ t('screen.editor.copyTo') }}<el-icon class="el-icon--right"><arrow-down /></el-icon
        ></el-button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item v-for="p in copyTargets" :key="p" :command="p">
              {{ platformLabel(p) }}{{ editor.activePlatform.value === 'pc' ? t('screen.editor.filteredSuffix') : t('screen.editor.fullSuffix') }}
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
      <el-checkbox v-model="showGrid" size="small" style="margin-left: 12px">{{ t('screen.editor.grid') }}</el-checkbox>
      <span class="platform-hint">{{ platformHint }}</span>
      <div class="toolbar-right">
        <el-button size="small" @click="handlePreview">{{ t('screen.editor.previewBtn') }}</el-button>
        <el-button size="small" type="primary" :loading="saving" @click="handleSave">{{ t('screen.editor.save') }}</el-button>
      </div>
    </div>
    <div class="editor-main">
      <GroupPanel :editor="editor" />
      <EditorCanvas :editor="editor" :show-grid="showGrid" />
      <div class="right-col">
        <PropertyPanel :editor="editor" />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
  import { computed, onMounted, ref } from 'vue'
  import { ArrowDown } from '@element-plus/icons-vue'
  import { useRoute, useRouter } from 'vue-router'
  import { ElMessage } from 'element-plus'
  import { useI18n } from 'vue-i18n'
  import type { Screen, ScreenPlatform } from '@dataviz/shared-types'
  import { createScreen, getScreen, saveScreenVariant, updateScreen } from '@dataviz/api-client'
  import ComponentPanel from './editor/ComponentPanel.vue'
  import EditorCanvas from './editor/EditorCanvas.vue'
  import GroupPanel from './editor/GroupPanel.vue'
  import PropertyPanel from './editor/PropertyPanel.vue'
  import { ALL_PLATFORMS, useEditorState } from './editor/editorState'
  import { DRAFT_PREVIEW_ID, getPreviewDraft, setPreviewDraft } from './editor/previewHandoff'

  const { t } = useI18n()
  const route = useRoute()
  const router = useRouter()
  const id = computed(() => (route.params.id as string | undefined) || undefined)

  const editor = useEditorState()
  const name = ref(t('screen.editor.defaultName'))
  const saving = ref(false)
  const showGrid = ref(true)

  function platformLabel(p: ScreenPlatform): string {
    return t(`screen.editor.platform_${p}`)
  }

  const copyTargets = computed<ScreenPlatform[]>(() => {
    const active = editor.activePlatform.value
    if (active === 'pc') return ['mobile', 'tablet']
    return active === 'mobile' ? ['tablet'] : ['mobile']
  })

  const platformHint = computed(() => {
    const p = editor.activePlatform.value
    if (p === 'pc') return t('screen.editor.hintPc')
    return t('screen.editor.hintUni', { p: platformLabel(p) })
  })

  function onPlatformChange(p: ScreenPlatform | string) {
    editor.switchPlatform(p as ScreenPlatform)
  }

  function onCopyTo(p: ScreenPlatform) {
    editor.copyActiveTo(p)
    ElMessage.success(t('screen.editor.copiedTo', { p: platformLabel(p) }))
  }

  async function loadScreen() {
    // 预览页「返回设计器」：沿用同一份预览快照，未保存的改动不丢
    if (route.query.restore === '1') {
      const snap = getPreviewDraft()
      if (snap) {
        editor.adoptScreen(snap)
        name.value = snap.name || name.value
        const p = route.query.platform as ScreenPlatform | undefined
        if (p && p !== 'pc') editor.switchPlatform(p)
        return
      }
    }
    if (!id.value) return
    try {
      const s = await getScreen(id.value)
      editor.adoptScreen(s)
      name.value = s.name
    } catch (e) {
      ElMessage.error((e as Error).message)
    }
  }

  async function handleSave() {
    if (!name.value.trim()) {
      ElMessage.warning(t('screen.editor.nameRequired'))
      return
    }
    saving.value = true
    try {
      editor.normalizeAll()
      const payload = editor.buildPayload(name.value.trim())
      if (id.value) {
        await updateScreen(id.value, payload)
        const variantSaves = (['mobile', 'tablet'] as ScreenPlatform[])
          .filter((p) => payload.variants?.[p])
          .map((p) => saveScreenVariant(id.value!, p, payload.variants[p]!))
        await Promise.all(variantSaves)
        ElMessage.success(t('screen.editor.saved'))
      } else {
        const created = await createScreen(payload)
        if (created?.id) {
          router.replace(`/screen/editor/${created.id}`)
        }
        ElMessage.success(t('screen.editor.created'))
      }
    } catch (e) {
      ElMessage.error((e as Error).message)
    } finally {
      saving.value = false
    }
  }

  function handlePreview() {
    editor.normalizeAll()
    const payload = editor.buildPayload(name.value.trim() || t('screen.editor.defaultName'))
    const platform = editor.activePlatform.value
    // 预览始终走编辑器当前快照：未保存也能看到全部组件与最新改动
    setPreviewDraft({ id: id.value || DRAFT_PREVIEW_ID, ...payload, status: 'draft' } as Screen)
    router.push(`/screen/preview/${id.value || DRAFT_PREVIEW_ID}?platform=${platform}&draft=1`)
  }

  function handleBack() {
    router.push('/screen')
  }

  onMounted(() => loadScreen())
</script>

<style lang="scss" scoped>
  .screen-editor {
    height: 100%;
    display: flex;
    flex-direction: column;
    background: var(--dv-surface-1);

    .editor-toolbar {
      display: flex;
      align-items: center;
      gap: var(--dv-space-sm);
      padding: var(--dv-space-sm) var(--dv-space-lg);
      background: linear-gradient(180deg, var(--dv-surface-2), var(--dv-surface-1));
      border-bottom: 1px solid var(--dv-border);
      box-shadow: var(--dv-shadow-sm);

      :deep(.el-button),
      :deep(.el-checkbox__label) {
        color: var(--dv-text-2);
      }

      .platform-hint {
        margin-left: var(--dv-space-md);
        font-size: var(--dv-font-xs);
        color: var(--dv-text-3);
      }
      .toolbar-right {
        margin-left: auto;
        display: flex;
        gap: var(--dv-space-sm);
      }
    }

    .editor-main {
      flex: 1;
      display: flex;
      overflow: hidden;
    }

    .right-col {
      width: 300px;
      background: var(--dv-surface-1);
      color: var(--dv-text-1);
      border-left: 1px solid var(--dv-border);
      display: flex;
      flex-direction: column;
      overflow: hidden;

      .property-panel {
        flex: 1;
        min-height: 0;
      }
    }
  }
</style>
