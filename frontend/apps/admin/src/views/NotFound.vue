<template>
  <div class="not-found">
    <el-result icon="warning" title="404" :sub-title="t('common.notFoundDesc')">
      <template #extra>
        <el-button type="primary" @click="$router.push('/admin/dashboard')">{{ t('common.backHome') }}</el-button>
      </template>
    </el-result>
  </div>
</template>

<script setup lang="ts">
  import { useI18n } from 'vue-i18n'

  const { t } = useI18n()
</script>

<style scoped>
.not-found {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
}
</style>
