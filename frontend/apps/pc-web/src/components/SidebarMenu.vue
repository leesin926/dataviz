<template>
  <aside class="sidebar" :class="{ collapsed: appStore.sidebarCollapsed }">
    <div class="sidebar-brand">
      <span class="brand-mark">DV</span>
      <span v-if="!appStore.sidebarCollapsed" class="brand-text">{{ t('common.appName') }}</span>
    </div>
    <el-scrollbar class="sidebar-scroll">
      <el-menu
        :default-active="activeMenu"
        :collapse="appStore.sidebarCollapsed"
        :collapse-transition="false"
        :router="true"
        class="sidebar-menu"
      >
        <template v-for="group in groups" :key="group.key">
          <div v-if="!appStore.sidebarCollapsed" class="menu-group-label">{{ t(group.key) }}</div>
          <template v-for="item in group.items" :key="item.path">
            <el-sub-menu v-if="item.children?.length" :index="item.path">
              <template #title>
                <el-icon><component :is="item.icon" /></el-icon>
                <span>{{ t(item.titleKey) }}</span>
              </template>
              <el-menu-item v-for="child in item.children" :key="child.path" :index="child.path">
                {{ t(child.titleKey) }}
              </el-menu-item>
            </el-sub-menu>
            <el-menu-item v-else :index="item.path">
              <el-icon><component :is="item.icon" /></el-icon>
              <template #title>{{ t(item.titleKey) }}</template>
            </el-menu-item>
          </template>
        </template>
      </el-menu>
    </el-scrollbar>
    <div class="sidebar-foot" @click="appStore.toggleSidebar">
      <el-icon><component :is="appStore.sidebarCollapsed ? 'Expand' : 'Fold'" /></el-icon>
      <span v-if="!appStore.sidebarCollapsed">{{ t('common.collapse') }}</span>
    </div>
  </aside>
</template>

<script setup lang="ts">
  import { computed } from 'vue'
  import { useRoute } from 'vue-router'
  import { useI18n } from 'vue-i18n'
  import { useAppStore } from '@/stores/app'
  import { usePermissionStore } from '@/stores/permission'
  import { menuRoutes } from '@/router'

  const route = useRoute()
  const { t } = useI18n()
  const appStore = useAppStore()
  const permissionStore = usePermissionStore()

  const activeMenu = computed(() => '/' + (route.path.split('/')[1] || 'dashboard'))

  interface MenuItem {
    path: string
    titleKey: string
    group: string
    icon?: string
    children?: MenuItem[]
  }

  const GROUP_ORDER = ['nav.groupDesign', 'nav.groupData', 'nav.groupOps']

  const DEFAULT_GROUP = 'nav.groupOps'

  function toItem(prefix: string, r: (typeof menuRoutes)[number], parentGroup = DEFAULT_GROUP): MenuItem | null {
    const meta = (r.meta ?? {}) as Record<string, unknown>
    if (meta.hidden || !meta.titleKey) return null
    const perm = meta.permission as string | undefined
    if (perm && !permissionStore.hasPermission(perm)) return null
    const group = (meta.group as string) ?? parentGroup
    const children = ((r.children ?? []) as (typeof menuRoutes)[number][])
      .map((c) => toItem(`${prefix}/${String(r.path)}`, c, group))
      .filter((c): c is MenuItem => c !== null)
    return {
      path: `${prefix}/${r.path}`.replace(/\/+$/, ''),
      titleKey: meta.titleKey as string,
      group,
      icon: meta.icon as string | undefined,
      children: children.length ? children : undefined,
    }
  }

  const groups = computed(() => {
    const items = menuRoutes
      .map((r) => toItem('', r))
      .filter((i): i is MenuItem => i !== null)
    return GROUP_ORDER.map((key) => ({ key, items: items.filter((i) => i.group === key) })).filter((g) => g.items.length)
  })
</script>

<style lang="scss" scoped>
  .sidebar {
    width: var(--dv-sidebar-w);
    flex-shrink: 0;
    height: 100%;
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

  .sidebar-brand {
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
      letter-spacing: 0.5px;
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

  .sidebar-scroll {
    flex: 1;
    min-height: 0;
  }

  .menu-group-label {
    padding: 16px 18px 6px;
    font-size: 11px;
    letter-spacing: 1.2px;
    color: rgba(184, 210, 245, 0.42);
    text-transform: uppercase;
  }

  .sidebar-menu {
    border-right: none;
    background: transparent;
    padding-bottom: 12px;

    :deep(.el-menu-item),
    :deep(.el-sub-menu__title) {
      height: 42px;
      line-height: 42px;
      margin: 2px 10px;
      border-radius: 8px;
      color: rgba(214, 232, 255, 0.72);
      background: transparent;
    }

    :deep(.el-menu-item:hover),
    :deep(.el-sub-menu__title:hover) {
      background: rgba(63, 218, 255, 0.09);
      color: #eaf6ff;
    }

    :deep(.el-menu-item.is-active) {
      color: #fff;
      background: linear-gradient(90deg, rgba(63, 218, 255, 0.22), rgba(63, 218, 255, 0.04));
      box-shadow: inset 2px 0 0 var(--dv-screen-accent);
    }

    :deep(.el-sub-menu .el-menu) {
      background: transparent;
    }
  }

  .sidebar-foot {
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
</style>
