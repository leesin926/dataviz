<template>
  <div class="default-layout">
    <SidebarMenu />
    <div class="layout-main">
      <HeaderBar />
      <BreadcrumbNav v-if="appStore.settings.showBreadcrumb" />
      <main class="layout-content">
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
  import { useAppStore } from '@/stores/app'
  import SidebarMenu from '@/components/SidebarMenu.vue'
  import HeaderBar from '@/components/HeaderBar.vue'
  import BreadcrumbNav from '@/components/BreadcrumbNav.vue'

  const appStore = useAppStore()
</script>

<style lang="scss" scoped>
  .default-layout {
    display: flex;
    height: 100%;
    width: 100%;
    overflow: hidden;
    background: var(--dv-surface-0);
  }
  .layout-main {
    flex: 1;
    min-width: 0;
    display: flex;
    flex-direction: column;
    overflow: hidden;
  }
  .layout-content {
    flex: 1;
    overflow: auto;
    background: var(--dv-surface-0);
  }
</style>
