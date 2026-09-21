<template>
  <div class="breadcrumb-nav">
    <el-breadcrumb separator="/">
      <el-breadcrumb-item :to="{ path: '/dashboard' }">{{ t('common.home') }}</el-breadcrumb-item>
      <el-breadcrumb-item v-for="item in breadcrumbs" :key="item.path">
        {{ t(item.titleKey) }}
      </el-breadcrumb-item>
    </el-breadcrumb>
  </div>
</template>

<script setup lang="ts">
  import { computed } from 'vue'
  import { useRoute } from 'vue-router'
  import { useI18n } from 'vue-i18n'

  const route = useRoute()
  const { t } = useI18n()

  const breadcrumbs = computed(() =>
    route.matched
      .filter((r) => (r.meta?.titleKey as string | undefined))
      .map((r) => ({ path: r.path, titleKey: r.meta.titleKey as string })),
  )
</script>

<style lang="scss" scoped>
  .breadcrumb-nav {
    height: 40px;
    display: flex;
    align-items: center;
    padding: 0 20px;
    border-bottom: 1px solid var(--dv-border-soft);
    background: var(--dv-surface-1);
    :deep(.el-breadcrumb__inner) {
      font-size: 12px;
      color: var(--dv-text-3);
      font-weight: 400;
    }
    :deep(.el-breadcrumb__item:last-child .el-breadcrumb__inner) {
      color: var(--dv-text-1);
      font-weight: 500;
    }
  }
</style>
