<template>
  <div class="desktop-shell">
    <header class="shell-head">
      <div class="brand">
        <h1>{{ t('common.appName') }}</h1>
        <p>{{ t('common.appSubtitle') }}</p>
      </div>
      <div class="head-actions">
        <el-tag v-if="!api" type="warning" effect="light">{{ t('desktop.browserMode') }}</el-tag>
        <el-button text size="small" :icon="Link" @click="openBaseDialog">
          {{ t('desktop.serverBase') }}: {{ base }}
        </el-button>
        <el-dropdown trigger="click" @command="onLocale">
          <span class="locale-switch">{{ localeLabel }} ▾</span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="zh-CN">简体中文</el-dropdown-item>
              <el-dropdown-item command="en-US">English</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
        <el-button text :icon="SwitchButton" @click="handleLogout">{{ t('desktop.logout') }}</el-button>
      </div>
    </header>

    <main class="shell-body">
      <el-alert v-if="unusableBase" :title="t('desktop.baseUnusableTitle')" type="error" :closable="false" show-icon class="gap">
        {{ t('desktop.baseUnusableDesc', { base }) }}
      </el-alert>
      <el-alert v-if="!api" :title="t('desktop.browserModeTitle')" type="info" :closable="false" show-icon class="gap">
        {{ t('desktop.browserModeDesc') }}
      </el-alert>

      <el-tabs v-model="activeTab" class="shell-tabs">
        <el-tab-pane :label="t('desktop.tabCapability')" name="capability">
          <CapabilityPane :api="api" />
        </el-tab-pane>
        <el-tab-pane :label="t('desktop.tabScreens')" name="screens">
          <ScreenPane :api="api" />
        </el-tab-pane>
        <el-tab-pane :label="t('desktop.tabUpdate')" name="update">
          <UpdatePane :api="api" />
        </el-tab-pane>
      </el-tabs>
    </main>

    <el-dialog v-model="baseVisible" :title="t('desktop.baseDialogTitle')" width="520px">
      <el-input v-model="baseDraft" :placeholder="t('desktop.baseDialogPlaceholder')" />
      <p class="base-tip">{{ t('desktop.baseDialogTip') }}</p>
      <template #footer>
        <el-button @click="baseVisible = false">{{ t('desktop.cancel') }}</el-button>
        <el-button type="primary" @click="saveBase">{{ t('desktop.save') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
  import { computed, onMounted, ref } from 'vue'
  import { useRouter } from 'vue-router'
  import { ElMessage } from 'element-plus'
  import { Link, SwitchButton } from '@element-plus/icons-vue'
  import { clearToken } from '@dataviz/api-client'
  import { removeLocal } from '@dataviz/shared-utils'
  import type { DesktopApi } from '@/desktop/electronApi'
  import { applyBase, baseLooksUnusable, currentBase } from '@/desktop/apiBase'
  import CapabilityPane from '@/views/desktop/CapabilityPane.vue'
  import ScreenPane from '@/views/desktop/ScreenPane.vue'
  import UpdatePane from '@/views/desktop/UpdatePane.vue'
  import { t, switchLocale, getStoredLocale, type AppLocale } from '@/locales'

  const router = useRouter()
  const api: DesktopApi | null = window.electronAPI ?? null
  const activeTab = ref<'capability' | 'screens' | 'update'>('capability')

  const base = ref(currentBase())
  const baseVisible = ref(false)
  const baseDraft = ref('')
  const unusableBase = ref(baseLooksUnusable())

  const locale = ref<AppLocale>(getStoredLocale())
  const localeLabel = computed(() => (locale.value === 'en-US' ? 'English' : '简体中文'))

  function onLocale(cmd: string | number | object) {
    const next = cmd as AppLocale
    locale.value = next
    switchLocale(next)
  }

  function openBaseDialog() {
    baseDraft.value = base.value
    baseVisible.value = true
  }

  function saveBase() {
    try {
      base.value = applyBase(baseDraft.value)
      unusableBase.value = baseLooksUnusable()
      baseVisible.value = false
      ElMessage.success(t('desktop.baseSaved'))
    } catch (e) {
      ElMessage.error((e as Error).message)
    }
  }

  function handleLogout() {
    clearToken()
    removeLocal('user')
    removeLocal('permissions')
    router.push('/login')
  }

  onMounted(() => {
    // 打包态没配服务地址就直接停在"大屏与离线"页签：那里有改地址的入口，用户不用先去找
    if (unusableBase.value) activeTab.value = 'screens'
  })
</script>

<style scoped lang="scss">
  .desktop-shell {
    display: flex;
    flex-direction: column;
    width: 100%;
    height: 100%;
    background: var(--dv-bg);
    color: var(--dv-text);
  }

  .shell-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 14px 22px;
    background: var(--dv-surface-1);
    border-bottom: 1px solid var(--dv-border);

    .brand h1 {
      font-size: var(--dv-font-xl);
      font-weight: 650;
    }

    .brand p {
      margin-top: 2px;
      font-size: var(--dv-font-xs);
      color: var(--dv-text-3);
    }
  }

  .head-actions {
    display: flex;
    align-items: center;
    gap: 12px;
  }

  .locale-switch {
    font-size: var(--dv-font-sm);
    color: var(--dv-text-2);
    cursor: pointer;
  }

  .shell-body {
    flex: 1;
    min-height: 0;
    padding: 16px 22px 22px;
    overflow: auto;
  }

  .gap {
    margin-bottom: 12px;
  }

  .shell-tabs :deep(.el-tabs__content) {
    overflow: visible;
  }

  .base-tip {
    margin-top: 8px;
    font-size: var(--dv-font-xs);
    color: var(--dv-text-3);
  }
</style>
