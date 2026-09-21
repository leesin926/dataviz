<template>
  <header class="header-bar">
    <div class="header-left">
      <el-icon class="icon-btn" @click="appStore.toggleSidebar">
        <Expand v-if="appStore.sidebarCollapsed" />
        <Fold v-else />
      </el-icon>
      <h1 class="page-title">{{ pageTitle }}</h1>
    </div>
    <div class="header-right">
      <el-tooltip :content="t('common.fullscreen')" placement="bottom">
        <el-icon class="icon-btn" @click="toggleFullScreen"><FullScreen /></el-icon>
      </el-tooltip>
      <el-tooltip :content="appStore.theme === 'dark' ? t('common.themeLight') : t('common.themeDark')" placement="bottom">
        <el-icon class="icon-btn" @click="toggleTheme">
          <Moon v-if="appStore.theme === 'light'" />
          <Sunny v-else />
        </el-icon>
      </el-tooltip>
      <el-dropdown trigger="click" @command="onLocale">
        <span class="locale-btn">{{ locale === 'zh-CN' ? '中' : 'EN' }}</span>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="zh-CN">简体中文</el-dropdown-item>
            <el-dropdown-item command="en-US">English</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
      <el-dropdown trigger="click" @command="handleCommand">
        <div class="user-info">
          <el-avatar :size="28" :src="userStore.user?.avatar">
            {{ userStore.user?.nickname?.[0] || userStore.user?.username?.[0] || 'U' }}
          </el-avatar>
          <span class="username">{{ userStore.user?.nickname || userStore.user?.username }}</span>
        </div>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="profile">{{ t('common.profile') }}</el-dropdown-item>
            <el-dropdown-item command="settings">{{ t('common.settings') }}</el-dropdown-item>
            <el-dropdown-item command="logout" divided>{{ t('common.logout') }}</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </header>
</template>

<script setup lang="ts">
  import { computed } from 'vue'
  import { useRoute } from 'vue-router'
  import { Expand, Fold, FullScreen, Moon, Sunny } from '@element-plus/icons-vue'
  import { useI18n } from 'vue-i18n'
  import { useAppStore } from '@/stores/app'
  import { useUserStore } from '@/stores/user'
  import { switchLocale, getStoredLocale, type AppLocale } from '@/locales'
  import { ref } from 'vue'

  const route = useRoute()
  const appStore = useAppStore()
  const userStore = useUserStore()
  const { t } = useI18n()
  const locale = ref<AppLocale>(getStoredLocale())

  const pageTitle = computed(() => {
    const key = route.meta?.titleKey as string | undefined
    return key ? t(key) : t('common.appName')
  })

  function onLocale(cmd: string | number | object) {
    const l = cmd as AppLocale
    locale.value = l
    switchLocale(l)
  }

  function toggleFullScreen() {
    if (document.fullscreenElement) document.exitFullscreen()
    else document.documentElement.requestFullscreen()
  }

  function toggleTheme() {
    appStore.setTheme(appStore.theme === 'light' ? 'dark' : 'light')
  }

  function handleCommand(cmd: string) {
    if (cmd === 'logout') userStore.logout()
  }
</script>

<style lang="scss" scoped>
  .header-bar {
    height: 56px;
    flex-shrink: 0;
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 0 20px;
    background: var(--dv-surface-1);
    border-bottom: 1px solid var(--dv-border-soft);
    backdrop-filter: blur(8px);

    &.fixed {
      position: sticky;
      top: 0;
      z-index: 100;
    }
  }

  .header-left {
    display: flex;
    align-items: center;
    gap: 14px;
  }

  .page-title {
    margin: 0;
    font-size: 16px;
    font-weight: 600;
    letter-spacing: 0.3px;
    color: var(--dv-text-1);
  }

  .header-right {
    display: flex;
    align-items: center;
    gap: 14px;
  }

  .icon-btn {
    font-size: 17px;
    cursor: pointer;
    color: var(--dv-text-2);
    transition: color 0.2s;
    &:hover {
      color: var(--dv-accent);
    }
  }

  .locale-btn {
    font-size: 12px;
    font-weight: 600;
    color: var(--dv-text-2);
    cursor: pointer;
    padding: 2px 9px;
    border: 1px solid var(--dv-border);
    border-radius: 6px;
    &:hover {
      color: var(--dv-accent);
      border-color: var(--dv-accent);
    }
  }

  .user-info {
    display: flex;
    align-items: center;
    gap: 8px;
    cursor: pointer;
    .username {
      font-size: 13px;
      color: var(--dv-text-1);
    }
  }
</style>
