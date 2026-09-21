<template>
  <div class="group-panel">
    <div class="panel-head">
      <h4>{{ t('screen.editor.layers') }}</h4>
      <el-button link size="small" @click="editor.createGroup()">{{ t('screen.editor.newGroup') }}</el-button>
    </div>

    <div v-for="(g, gi) in groups" :key="g.id" class="group-block">
      <div class="group-row" @click="toggleExpand(g.id)">
        <span class="caret">{{ expanded.has(g.id) ? '▾' : '▸' }}</span>
        <input
          v-if="editingGroupId === g.id"
          ref="groupNameRef"
          v-model="editingName"
          class="name-input"
          @blur="commitRename(g.id)"
          @keyup.enter="commitRename(g.id)"
          @click.stop
        />
        <span v-else class="group-name" @dblclick.stop="startRename(g)">{{ g.name }}（{{ memberCount(g) }}）</span>
        <span class="group-ops" @click.stop>
          <el-button link size="small" @click="editor.toggleGroupVisible(g.id)">{{ g.visible ? '👁' : '─' }}</el-button>
          <el-button link size="small" @click="editor.toggleGroupLock(g.id)">{{ g.locked ? '🔒' : '🔓' }}</el-button>
          <el-button link size="small" :disabled="gi === 0" @click="editor.moveGroup(g.id, 1)">↑</el-button>
          <el-button link size="small" :disabled="gi === groups.length - 1" @click="editor.moveGroup(g.id, -1)">↓</el-button>
          <el-button link size="small" @click="editor.removeGroup(g.id)">✕</el-button>
        </span>
      </div>
      <div v-show="expanded.has(g.id)" class="group-members">
        <div
          v-for="c in members(g)"
          :key="c.id"
          class="comp-row"
          :class="{ active: editor.selectedId.value === c.id }"
          @click="editor.selectedId.value = c.id"
        >
          <span class="comp-name">{{ c.name }}</span>
          <el-select
            :model-value="g.id"
            size="small"
            class="move-select"
            @change="(v: string) => editor.moveComponentToGroup(c.id, v === '__none__' ? null : v)"
          >
            <el-option :label="t('screen.editor.ungrouped')" value="__none__" />
            <el-option v-for="other in groups" :key="other.id" :label="other.name" :value="other.id" />
          </el-select>
          <el-button link size="small" @click.stop="c.visible = !c.visible">{{ c.visible ? '👁' : '─' }}</el-button>
          <el-button link size="small" @click.stop="c.locked = !c.locked">{{ c.locked ? '🔒' : '🔓' }}</el-button>
          <el-button link size="small" @click.stop="editor.removeComponent(c.id)">✕</el-button>
        </div>
      </div>
    </div>

    <div class="group-block">
      <div class="group-row ungrouped" @click="toggleExpand('__none__')">
        <span class="caret">{{ expanded.has('__none__') ? '▾' : '▸' }}</span>
        <span class="group-name">{{ t('screen.editor.ungrouped') }}（{{ ungrouped.length }}）</span>
      </div>
      <div v-show="expanded.has('__none__')" class="group-members">
        <div
          v-for="c in ungrouped"
          :key="c.id"
          class="comp-row"
          :class="{ active: editor.selectedId.value === c.id }"
          @click="editor.selectedId.value = c.id"
        >
          <span class="comp-name">{{ c.name }}</span>
          <el-select
            model-value=""
            size="small"
            class="move-select"
            :placeholder="t('screen.editor.moveIn')"
            @change="(v: string) => v && editor.moveComponentToGroup(c.id, v)"
          >
            <el-option v-for="g in groups" :key="g.id" :label="g.name" :value="g.id" />
          </el-select>
          <el-button link size="small" @click.stop="c.visible = !c.visible">{{ c.visible ? '👁' : '─' }}</el-button>
          <el-button link size="small" @click.stop="c.locked = !c.locked">{{ c.locked ? '🔒' : '🔓' }}</el-button>
          <el-button link size="small" @click.stop="editor.removeComponent(c.id)">✕</el-button>
        </div>
      </div>
    </div>

    <p v-if="!groups.length && !ungrouped.length" class="empty">{{ t('screen.editor.emptyLayers') }}</p>
  </div>
</template>

<script setup lang="ts">
  import { computed, ref } from 'vue'
  import { useI18n } from 'vue-i18n'
  import type { ScreenComponent, ScreenLayer } from '@dataviz/shared-types'
  import type { EditorState } from './editorState'

  const { t } = useI18n()
  const props = defineProps<{ editor: EditorState }>()
  const editor = props.editor

  const expanded = ref(new Set<string>())
  const editingGroupId = ref<string | null>(null)
  const editingName = ref('')

  /** 数组序=底→顶；面板展示顶层在前 */
  const groups = computed(() => [...editor.activeLayers.value].reverse())

  const ungrouped = computed(() => {
    const grouped = new Set<string>()
    for (const g of editor.activeLayers.value) for (const cid of g.componentIds) grouped.add(cid)
    return editor.activeComponents.value.filter((c) => !grouped.has(c.id))
  })

  function members(g: ScreenLayer): ScreenComponent[] {
    return g.componentIds
      .map((cid) => editor.activeComponents.value.find((c) => c.id === cid))
      .filter((c): c is ScreenComponent => !!c)
  }

  function memberCount(g: ScreenLayer): number {
    return members(g).length
  }

  function toggleExpand(id: string) {
    const s = new Set(expanded.value)
    if (s.has(id)) s.delete(id)
    else s.add(id)
    expanded.value = s
  }

  function startRename(g: ScreenLayer) {
    editingGroupId.value = g.id
    editingName.value = g.name
  }

  function commitRename(id: string) {
    if (editingGroupId.value === id && editingName.value.trim()) {
      editor.renameGroup(id, editingName.value.trim())
    }
    editingGroupId.value = null
  }
</script>

<style lang="scss" scoped>
  .group-panel {
    width: 230px;
    background: var(--dv-surface-1);
    color: var(--dv-text-1);
    border-right: 1px solid var(--dv-border);
    padding: var(--dv-space-md) 10px;
    overflow-y: auto;
    .panel-head {
      display: flex;
      align-items: center;
      justify-content: space-between;
      h4 {
        color: var(--dv-text-1);
        margin: 0 0 10px;
        font-size: var(--dv-font-md);
      }
    }
    .group-row {
      display: flex;
      align-items: center;
      gap: 4px;
      padding: 5px 4px;
      border-radius: var(--dv-radius-sm);
      cursor: pointer;
      &:hover {
        background: var(--dv-surface-sunken);
      }
      .caret {
        width: 14px;
        color: var(--dv-text-4);
        font-size: 10px;
        flex-shrink: 0;
      }
      .group-name {
        flex: 1;
        font-size: var(--dv-font-xs);
        color: var(--dv-text-1);
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
      }
      .name-input {
        flex: 1;
        background: var(--dv-primary-soft);
        border: 1px solid var(--dv-primary);
        color: var(--dv-text-1);
        font-size: var(--dv-font-xs);
        padding: 2px 4px;
        border-radius: var(--dv-radius-sm);
        outline: none;
        min-width: 0;
      }
      .group-ops {
        display: flex;
        align-items: center;
        flex-shrink: 0;
      }
    }
    .group-members {
      padding-left: var(--dv-space-lg);
      .comp-row {
        display: flex;
        align-items: center;
        gap: 2px;
        padding: 3px 4px;
        font-size: var(--dv-font-xs);
        border-radius: var(--dv-radius-sm);
        cursor: pointer;
        color: var(--dv-text-2);
        &:hover {
          background: var(--dv-surface-sunken);
        }
        &.active {
          background: var(--dv-primary-soft);
          color: var(--dv-primary);
          box-shadow: inset 2px 0 0 var(--dv-primary);
        }
        .comp-name {
          flex: 1;
          overflow: hidden;
          text-overflow: ellipsis;
          white-space: nowrap;
        }
        .move-select {
          width: 72px;
          flex-shrink: 0;
        }
      }
    }
    .empty {
      color: var(--dv-text-4);
      font-size: var(--dv-font-xs);
    }
    :deep(.el-button.is-link) {
      padding: 2px;
      color: var(--dv-text-3);
    }
  }
</style>
