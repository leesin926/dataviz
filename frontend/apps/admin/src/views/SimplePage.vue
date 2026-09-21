<template>
  <div class="simple-page">
    <p class="code">{{ code }}</p>
    <h2>{{ title }}</h2>
    <p class="desc">{{ desc }}</p>
    <el-button type="primary" @click="router.replace('/dashboard')">{{ t('common.backHome') }}</el-button>
  </div>
</template>

<script setup lang="ts">
  import { computed } from 'vue'
  import { useRouter } from 'vue-router'
  import { useI18n } from 'vue-i18n'

  const props = defineProps<{ code: number }>()
  const router = useRouter()
  const { t } = useI18n()

  const title = computed(() => (props.code === 403 ? t('common.forbidden') : t('common.notFound')))
  const desc = computed(() =>
    props.code === 403 ? t('common.forbiddenDesc') : t('common.notFoundDesc')
  )
</script>

<style lang="scss" scoped>
  .simple-page {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 12px;
    min-height: 100vh;
    background: var(--dv-surface-0);

    .code {
      margin: 0;
      font-size: 72px;
      font-weight: 700;
      letter-spacing: 4px;
      font-family: var(--dv-font-mono);
      background: var(--dv-grad-brand);
      -webkit-background-clip: text;
      background-clip: text;
      color: transparent;
    }

    h2 {
      margin: 0;
      font-size: 18px;
      color: var(--dv-text-1);
    }

    .desc {
      margin: 0 0 8px;
      font-size: 13px;
      color: var(--dv-text-3);
    }
  }
</style>
