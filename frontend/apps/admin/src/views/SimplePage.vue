<template>
  <div class="simple-page">
    <p class="code">{{ code }}</p>
    <h2>{{ title }}</h2>
    <p class="desc">{{ desc }}</p>
    <el-button v-if="backTarget" type="primary" @click="router.replace(backTarget)">
      {{ t('common.backHome') }}
    </el-button>
    <el-button v-else type="primary" @click="signOut">{{ t('common.logout') }}</el-button>
  </div>
</template>

<script setup lang="ts">
  import { computed } from 'vue'
  import { useRouter } from 'vue-router'
  import { useI18n } from 'vue-i18n'
  import { clearToken } from '@dataviz/api-client'
  import { removeLocal } from '@dataviz/shared-utils'
  import { canEnterRouteMeta, readSessionPermissions } from '@dataviz/permission'
  import { menuRoutes } from '@/router'

  const props = defineProps<{ code: number }>()
  const router = useRouter()
  const { t } = useI18n()

  const title = computed(() => (props.code === 403 ? t('common.forbidden') : t('common.notFound')))
  const desc = computed(() =>
    props.code === 403 ? t('common.forbiddenDesc') : t('common.notFoundDesc')
  )

  /**
   * 回哪儿，取决于当前会话真进得去哪儿 —— 以前固定写 `/dashboard`，
   * 而停在 403 页的人恰恰就是进不去 `/dashboard`（它要 `platform:read`）的那个，
   * 于是按钮看起来"点了没反应"：导航在同一秒发生又被守卫弹回 403。
   * 可见性口径与侧边栏、路由守卫同一份（hidden + meta.permission），不再第四处各写一遍。
   */
  const backTarget = computed<string | null>(() => {
    const permissions = readSessionPermissions()
    const reachable = menuRoutes.find((route) => {
      const meta = (route.meta ?? {}) as Record<string, unknown>
      return !meta.hidden && canEnterRouteMeta(route.meta, permissions)
    })
    return reachable ? `/${String(reachable.path)}` : null
  })

  function signOut() {
    clearToken()
    removeLocal('user')
    removeLocal('permissions')
    router.push('/login')
  }
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
