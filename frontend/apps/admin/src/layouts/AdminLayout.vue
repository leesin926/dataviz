<template>
  <div class="admin-shell">
    <aside class="shell-side" :class="{ collapsed: appStore.sidebarCollapsed }">
      <div class="side-brand">
        <span class="brand-mark">DA</span>
        <span v-if="!appStore.sidebarCollapsed" class="brand-text">{{ t('common.appName') }}</span>
      </div>
      <el-scrollbar class="side-scroll">
        <el-menu :default-active="activeMenu" :collapse="appStore.sidebarCollapsed" :collapse-transition="false" router class="side-menu">
          <template v-for="group in groups" :key="group.key">
            <div v-if="!appStore.sidebarCollapsed" class="menu-group">{{ t(group.key) }}</div>
            <el-menu-item v-for="item in group.items" :key="item.path" :index="item.path">
              <el-icon><component :is="item.icon" /></el-icon>
              <template #title>{{ t(item.titleKey) }}</template>
            </el-menu-item>
          </template>
        </el-menu>
      </el-scrollbar>
      <div class="side-foot" @click="appStore.toggleSidebar">
        <el-icon><component :is="appStore.sidebarCollapsed ? 'Expand' : 'Fold'" /></el-icon>
        <span v-if="!appStore.sidebarCollapsed">{{ t('common.collapse') }}</span>
      </div>
    </aside>

    <div class="shell-main">
      <header class="shell-head">
        <div class="head-left">
          <el-breadcrumb separator="/">
            <el-breadcrumb-item :to="{ path: '/dashboard' }">{{ t('common.home') }}</el-breadcrumb-item>
            <el-breadcrumb-item v-if="pageTitle">{{ pageTitle }}</el-breadcrumb-item>
          </el-breadcrumb>
        </div>
        <div class="head-right">
          <el-dropdown trigger="click" @command="onTheme">
            <span class="head-act">
              <el-icon><component :is="appStore.theme === 'dark' ? 'Sunny' : 'Moon'" /></el-icon>
              <span>{{ t(appStore.theme === 'dark' ? 'common.themeLight' : 'common.themeDark') }}</span>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="light">{{ t('common.themeLight') }}</el-dropdown-item>
                <el-dropdown-item command="dark">{{ t('common.themeDark') }}</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>

          <el-dropdown trigger="click" @command="onLocale">
            <span class="head-act">
              <el-icon><Position /></el-icon>
              <span>{{ locale === 'zh-CN' ? '中文' : 'EN' }}</span>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="zh-CN">中文</el-dropdown-item>
                <el-dropdown-item command="en-US">English</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>

          <el-dropdown trigger="click" @command="onUser">
            <span class="head-user">
              <el-avatar :size="28">{{ avatarText }}</el-avatar>
              <span class="head-name">{{ userName }}</span>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="logout" divided>{{ t('common.logout') }}</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>

      <main class="shell-body">
        <router-view v-slot="{ Component }">
          <transition name="fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </main>
    </div>
  </div>
</template>

<script setup lang="ts">
  import { computed, onMounted } from 'vue'
  import { useRoute, useRouter } from 'vue-router'
  import { useI18n } from 'vue-i18n'
  import { Position } from '@element-plus/icons-vue'
  import { clearToken } from '@dataviz/api-client'
  import { matchesPermissionCode } from '@dataviz/permission'
  import { getLocal, removeLocal } from '@dataviz/shared-utils'
  import { useAppStore } from '@/stores/app'
  import { menuRoutes } from '@/router'
  import { switchLocale, type AppLocale } from '@/locales'

  const route = useRoute()
  const router = useRouter()
  const { t, locale } = useI18n()
  const appStore = useAppStore()

  const activeMenu = computed(() => '/' + (route.path.split('/')[1] || 'dashboard'))
  const pageTitle = computed(() => {
    const key = route.meta?.titleKey as string | undefined
    return key ? t(key) : ''
  })

  const permissions = computed(() => getLocal<string[]>('permissions') ?? [])
  const userName = computed(() => {
    const stored = getLocal<{ nickname?: string; username?: string }>('user')
    return stored?.nickname || stored?.username || t('login.username')
  })
  const avatarText = computed(() => (userName.value || 'A').slice(0, 1).toUpperCase())

  interface MenuEntry {
    path: string
    titleKey: string
    icon: string
    groupKey: string
  }

  const GROUP_ORDER = ['nav.groupOverview', 'nav.groupPlatform', 'nav.groupSystem']

  const groups = computed(() => {
    const entries: MenuEntry[] = menuRoutes
      .map((r) => {
        const meta = (r.meta ?? {}) as Record<string, unknown>
        const perm = meta.permission as string | undefined
        // 菜单可见性必须与路由守卫、后端 PermissionInterceptor 同一套匹配语义：
        // 裸 includes 只认精确码，被授予 `system:*` 的角色会"接口通、菜单消失"（API-26 同族）
        if (perm && !matchesPermissionCode(perm, permissions.value)) return null
        return {
          path: `/${String(r.path)}`,
          titleKey: meta.titleKey as string,
          icon: (meta.icon as string) || 'Document',
          groupKey: (meta.group as string) || 'nav.groupSystem',
        }
      })
      .filter((x): x is MenuEntry => x !== null)
    return GROUP_ORDER.map((key) => ({ key, items: entries.filter((e) => e.groupKey === key) })).filter(
      (g) => g.items.length,
    )
  })

  function onTheme(value: 'light' | 'dark') {
    appStore.setTheme(value)
  }

  function onLocale(value: AppLocale) {
    switchLocale(value)
  }

  function onUser(value: string) {
    if (value !== 'logout') return
    clearToken()
    removeLocal('user')
    removeLocal('permissions')
    router.push('/login')
  }

  onMounted(() => appStore.initialize())
</script>

<style lang="scss" scoped>
  .admin-shell {
    display: flex;
    height: 100vh;
    overflow: hidden;
    background: var(--dv-surface-0);
  }

  .shell-side {
    width: var(--dv-sidebar-w);
    flex-shrink: 0;
    display: flex;
    flex-direction: column;
    background: linear-gradient(180deg, #0b1220 0%, #0d1526 100%);
    border-right: 1px solid rgba(120, 180, 255, 0.12);
    transition: width 0.28s ease;
    overflow: hidden;

    &.collapsed {
      width: 64px;
    }
  }

  .side-brand {
    height: 56px;
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 0 16px;
    border-bottom: 1px solid rgba(120, 180, 255, 0.12);

    .brand-mark {
      width: 30px;
      height: 30px;
      flex-shrink: 0;
      border-radius: 9px;
      display: grid;
      place-items: center;
      font-size: 12px;
      font-weight: 700;
      color: #04121f;
      background: linear-gradient(135deg, var(--dv-screen-accent), #7ef0c8);
      box-shadow: 0 0 16px rgba(63, 218, 255, 0.45);
    }

    .brand-text {
      font-size: 15px;
      font-weight: 600;
      color: #e8f3ff;
      white-space: nowrap;
    }
  }

  .side-scroll {
    flex: 1;
    min-height: 0;
  }

  .menu-group {
    padding: 16px 18px 6px;
    font-size: 11px;
    letter-spacing: 1.2px;
    color: rgba(184, 210, 245, 0.42);
    text-transform: uppercase;
  }

  .side-menu {
    border-right: none;
    background: transparent;
    padding-bottom: 12px;

    :deep(.el-menu-item) {
      height: 42px;
      line-height: 42px;
      margin: 2px 10px;
      border-radius: 8px;
      color: rgba(214, 232, 255, 0.72);
      background: transparent;
    }

    :deep(.el-menu-item:hover) {
      background: rgba(63, 218, 255, 0.09);
      color: #eaf6ff;
    }

    :deep(.el-menu-item.is-active) {
      color: #fff;
      background: linear-gradient(90deg, rgba(63, 218, 255, 0.22), rgba(63, 218, 255, 0.04));
      box-shadow: inset 2px 0 0 var(--dv-screen-accent);
    }
  }

  .side-foot {
    height: 44px;
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 0 18px;
    font-size: 12px;
    color: rgba(184, 210, 245, 0.55);
    border-top: 1px solid rgba(120, 180, 255, 0.12);
    cursor: pointer;

    &:hover {
      color: var(--dv-screen-accent);
    }
  }

  .shell-main {
    flex: 1;
    min-width: 0;
    display: flex;
    flex-direction: column;
  }

  .shell-head {
    height: 56px;
    flex-shrink: 0;
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 0 20px;
    background: var(--dv-glass);
    backdrop-filter: blur(10px);
    border-bottom: 1px solid var(--dv-border-soft);

    .head-right {
      display: flex;
      align-items: center;
      gap: 18px;
    }

    .head-act {
      display: flex;
      align-items: center;
      gap: 6px;
      font-size: 13px;
      color: var(--dv-text-2);
      cursor: pointer;
      outline: none;

      &:hover {
        color: var(--dv-primary);
      }
    }

    .head-user {
      display: flex;
      align-items: center;
      gap: 8px;
      cursor: pointer;
      outline: none;

      .head-name {
        font-size: 13px;
        color: var(--dv-text-2);
      }
    }
  }

  .shell-body {
    flex: 1;
    min-height: 0;
    overflow-y: auto;
  }

  .fade-enter-active,
  .fade-leave-active {
    transition: opacity 0.2s ease;
  }

  .fade-enter-from,
  .fade-leave-to {
    opacity: 0;
  }
</style>
