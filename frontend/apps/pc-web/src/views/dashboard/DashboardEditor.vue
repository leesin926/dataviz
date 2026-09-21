<template>
  <div class="dashboard-editor">
    <div class="editor-header">
      <el-button @click="handleBack">{{ t('dashboard.back') }}</el-button>
      <el-input v-model="name" :placeholder="t('dashboard.namePlaceholder')" style="width: 300px; margin-left: 12px" />
      <div class="editor-actions">
        <el-button @click="handleSave">{{ t('common.save') }}</el-button>
        <el-button type="primary" @click="handlePublish">{{ t('common.publish') }}</el-button>
      </div>
    </div>
    <div class="editor-body">
      <div class="widget-palette">
        <h4>{{ t('dashboard.widgets') }}</h4>
        <el-button v-for="wt in widgetTypes" :key="wt.value" size="small" @click="addWidget(wt.value)">
          {{ t(wt.labelKey) }}
        </el-button>
      </div>
      <div class="editor-canvas" @drop="handleDrop" @dragover.prevent>
        <div
          v-for="w in widgets"
          :key="w.id"
          class="widget-item"
          :style="widgetStyle(w)"
          :class="{ selected: selectedId === w.id }"
          @click="selectWidget(w.id)"
        >
          <div class="widget-title">{{ w.name }}</div>
          <ChartEngine v-if="w.chartConfig" :config="w.chartConfig" :data="emptyData" width="100%" height="calc(100% - 30px)" />
        </div>
      </div>
      <div class="property-panel">
        <h4>{{ t('dashboard.properties') }}</h4>
        <div v-if="selectedWidget">
          <el-form label-width="80px">
            <el-form-item :label="t('common.name')">
              <el-input v-model="selectedWidget.name" />
            </el-form-item>
            <el-form-item label="X">
              <el-input-number v-model="selectedWidget.x" />
            </el-form-item>
            <el-form-item label="Y">
              <el-input-number v-model="selectedWidget.y" />
            </el-form-item>
            <el-form-item label="W">
              <el-input-number v-model="selectedWidget.w" />
            </el-form-item>
            <el-form-item label="H">
              <el-input-number v-model="selectedWidget.h" />
            </el-form-item>
          </el-form>
          <el-button type="danger" @click="removeWidget(selectedWidget.id)">{{ t('common.delete') }}</el-button>
        </div>
        <div v-else class="empty-hint">{{ t('dashboard.selectWidgetHint') }}</div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
  import { ref, computed, onMounted } from 'vue'
  import { useRoute, useRouter } from 'vue-router'
  import { ElMessage } from 'element-plus'
  import { useI18n } from 'vue-i18n'
  import type { Widget } from '@dataviz/shared-types'
  import { ChartEngine } from '@dataviz/chart-engine'
  import { getDashboard, createDashboard, updateDashboard } from '@dataviz/api-client'

  const { t } = useI18n()
  const route = useRoute()
  const router = useRouter()

  const id = route.params.id as string | undefined
  const name = ref('')
  const widgets = ref<Widget[]>([])
  const selectedId = ref<string | null>(null)

  const widgetTypes = [
    { labelKey: 'dashboard.wBar', value: 'chart' },
    { labelKey: 'dashboard.wText', value: 'text' },
    { labelKey: 'dashboard.wImage', value: 'image' },
    { labelKey: 'dashboard.wKpi', value: 'kpi' },
    { labelKey: 'dashboard.wFilter', value: 'filter' },
  ]

  const emptyData = {
    columns: [{ field: 'x', type: 'string' }],
    rows: [],
  }

  const selectedWidget = computed(() => widgets.value.find((w) => w.id === selectedId.value) || null)

  function widgetStyle(w: Widget) {
    return {
      left: `${w.x}px`,
      top: `${w.y}px`,
      width: `${w.w}px`,
      height: `${w.h}px`,
    }
  }

  function addWidget(type: Widget['type']) {
    const id = `w_${Date.now()}`
    widgets.value.push({
      id,
      type,
      name: `${type}-${widgets.value.length + 1}`,
      x: 50,
      y: 50,
      w: 300,
      h: 200,
      config: {},
    })
  }

  function selectWidget(id: string) {
    selectedId.value = id
  }

  function removeWidget(id: string) {
    widgets.value = widgets.value.filter((w) => w.id !== id)
    if (selectedId.value === id) selectedId.value = null
  }

  function handleDrop(e: DragEvent) {
    const type = e.dataTransfer?.getData('widget-type') as Widget['type'] | undefined
    if (type) addWidget(type)
  }

  async function loadDashboard() {
    if (!id) return
    try {
      const d = await getDashboard(id)
      name.value = d.name
      widgets.value = d.widgets
    } catch (e) {
      ElMessage.error((e as Error).message)
    }
  }

  async function handleSave() {
    const payload = { name: name.value, widgets: widgets.value, layout: { width: 1200, cols: 24, rowHeight: 30 } }
    try {
      if (id) {
        await updateDashboard(id, payload)
      } else {
        await createDashboard(payload)
      }
      ElMessage.success(t('dashboard.saved'))
    } catch (e) {
      ElMessage.error((e as Error).message)
    }
  }

  function handleBack() {
    router.push('/dashboard')
  }

  function handlePublish() {
    ElMessage.info(t('dashboard.publishTodo'))
  }

  onMounted(() => {
    loadDashboard()
  })
</script>

<style lang="scss" scoped>
  .dashboard-editor {
    height: 100%;
    display: flex;
    flex-direction: column;
    .editor-header {
      display: flex;
      align-items: center;
      padding: 12px 16px;
      background: #fff;
      border-bottom: 1px solid #eee;
      .editor-actions {
        margin-left: auto;
      }
    }
    .editor-body {
      flex: 1;
      display: flex;
      overflow: hidden;
    }
    .widget-palette {
      width: 200px;
      background: #fff;
      border-right: 1px solid #eee;
      padding: 12px;
      overflow-y: auto;
      h4 {
        margin: 0 0 12px;
      }
    }
    .editor-canvas {
      flex: 1;
      position: relative;
      background: #f0f2f5;
      overflow: auto;
    }
    .widget-item {
      position: absolute;
      background: #fff;
      border: 1px solid #ddd;
      box-sizing: border-box;
      cursor: move;
      &.selected {
        border-color: #409eff;
        box-shadow: 0 0 0 2px rgba(64, 158, 255, 0.2);
      }
      .widget-title {
        padding: 4px 8px;
        font-size: 12px;
        background: #fafafa;
        border-bottom: 1px solid #eee;
      }
    }
    .property-panel {
      width: 280px;
      background: #fff;
      border-left: 1px solid #eee;
      padding: 12px;
      overflow-y: auto;
      h4 {
        margin: 0 0 12px;
      }
      .empty-hint {
        text-align: center;
        color: #999;
        padding: 20px 0;
      }
    }
  }
</style>
