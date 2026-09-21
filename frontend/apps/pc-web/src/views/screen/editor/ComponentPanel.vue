<template>
  <el-popover
    v-model:visible="popoverVisible"
    :width="360"
    trigger="click"
    placement="bottom-start"
    popper-class="component-popover"
  >
    <template #reference>
      <el-button size="small" style="margin-left: 12px">{{ t('screen.editor.addComp') }}</el-button>
    </template>
    <div class="pop-body">
      <p class="pop-tip">{{ t('screen.editor.dragTip') }}</p>
      <div v-for="group in groups" :key="group.category" class="comp-group">
        <div class="group-title">{{ t(`screen.editor.cat_${group.category}`) }}</div>
        <div class="group-items">
          <div
            v-for="def in group.defs"
            :key="def.type"
            class="comp-item"
            draggable="true"
            @dragstart="onDragStart($event, def.type)"
            @dragend="onDragEnd"
          >
            {{ compLabel(def.type, def.label) }}
          </div>
        </div>
      </div>
    </div>
  </el-popover>
</template>

<script setup lang="ts">
  import { computed, ref } from 'vue'
  import { useI18n } from 'vue-i18n'
  import type { ScreenComponentCategory } from '@dataviz/shared-types'
  import type { EditorState } from './editorState'

  const { t, te } = useI18n()
  const props = defineProps<{ editor: EditorState }>()

  const popoverVisible = ref(false)

  /** 组件名取 i18n（comp.<type>），缺省回落到定义里的中文 label */
  function compLabel(type: string, fallback: string): string {
    const key = `comp.${type}`
    return te(key) ? t(key) : fallback
  }

  const groups = computed(() => {
    // reactive 依赖：activePlatform 决定白名单
    const defs = props.editor.panelDefinitions()
    const byCat = new Map<ScreenComponentCategory, typeof defs>()
    for (const d of defs) {
      if (!byCat.has(d.category)) byCat.set(d.category, [])
      byCat.get(d.category)!.push(d)
    }
    return Array.from(byCat.entries()).map(([category, defs]) => ({ category, defs }))
  })

  function onDragStart(e: DragEvent, type: string) {
    if (!e.dataTransfer) return
    e.dataTransfer.setData('application/x-screen-comp', type)
    e.dataTransfer.effectAllowed = 'copy'
  }

  function onDragEnd() {
    // 拖拽结束后收起面板，让落点创建的组件立即可见
    popoverVisible.value = false
  }
</script>

<style lang="scss">
  .component-popover {
    .pop-body {
      // 组件类型多，面板内滚动而不是无限增高
      max-height: 62vh;
      overflow-y: auto;
    }
    .pop-tip {
      margin: 0 0 var(--dv-space-sm);
      font-size: var(--dv-font-xs);
      color: var(--dv-text-3);
    }
    .group-title {
      font-size: var(--dv-font-xs);
      letter-spacing: 0.6px;
      color: var(--dv-text-3);
      margin: var(--dv-space-md) 0 4px;
      text-transform: uppercase;
    }
    .group-items {
      display: flex;
      flex-wrap: wrap;
      gap: 6px;
    }
    .comp-item {
      padding: 5px 12px;
      background: var(--dv-surface-2);
      border: 1px solid var(--dv-border);
      border-radius: var(--dv-radius-sm);
      cursor: grab;
      font-size: var(--dv-font-xs);
      user-select: none;
      transition: border-color 0.18s ease, color 0.18s ease, box-shadow 0.18s ease;
      &:active {
        cursor: grabbing;
      }
      &:hover {
        color: var(--dv-primary);
        border-color: var(--dv-primary);
        box-shadow: 0 0 0 3px var(--dv-primary-soft);
      }
    }
  }
</style>
