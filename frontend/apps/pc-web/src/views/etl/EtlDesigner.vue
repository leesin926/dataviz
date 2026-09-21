<template>
  <div class="etl-designer-view">
    <EtlDesigner :task="task" @save="handleSave" @run="handleRun" />
  </div>
</template>

<script setup lang="ts">
  import { ref, onMounted } from 'vue'
  import { useRoute } from 'vue-router'
  import { ElMessage } from 'element-plus'
  import { useI18n } from 'vue-i18n'
  import type { EtlTask } from '@dataviz/shared-types'
  import { EtlDesigner } from '@dataviz/etl-designer'
  import { getEtlTask, updateEtlTask, executeEtlTask } from '@dataviz/api-client'

  const { t } = useI18n()
  const route = useRoute()
  const id = route.query.id as string | undefined
  const task = ref<EtlTask | undefined>()

  async function load() {
    if (!id) return
    try {
      task.value = await getEtlTask(id)
    } catch (e) {
      ElMessage.error((e as Error).message)
    }
  }

  async function handleSave(task: EtlTask) {
    try {
      if (id) {
        await updateEtlTask(id, task)
      }
      ElMessage.success(t('etl.saved'))
    } catch (e) {
      ElMessage.error((e as Error).message)
    }
  }

  async function handleRun(task: EtlTask) {
    try {
      await executeEtlTask(task.id)
      ElMessage.success(t('etl.submitted'))
    } catch (e) {
      ElMessage.error((e as Error).message)
    }
  }

  onMounted(() => load())
</script>

<style scoped>
  .etl-designer-view {
    width: 100%;
    height: calc(100vh - 120px);
  }
</style>
