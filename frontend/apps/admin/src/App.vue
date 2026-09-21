<template>
  <el-config-provider :locale="epLocale">
    <router-view />
  </el-config-provider>
</template>

<script setup lang="ts">
  import { computed, onMounted, onBeforeUnmount } from 'vue'
  import { useI18n } from 'vue-i18n'
  import { useRouter } from 'vue-router'
  import { ElMessage } from 'element-plus'
  import zhCn from 'element-plus/es/locale/lang/zh-cn'
  import en from 'element-plus/es/locale/lang/en'

  const router = useRouter()
  const { locale, t } = useI18n()
  const epLocale = computed(() => (locale.value === 'en-US' ? en : zhCn))

  function onSessionExpired(e: Event) {
    e.preventDefault()
    ElMessage.error(t('common.sessionExpired'))
    if (router.currentRoute.value.path !== '/login') {
      router.replace({ path: '/login', query: { redirect: router.currentRoute.value.fullPath } })
    }
  }

  function onDemoMode() {
    ElMessage.warning(t('common.demoMode'))
  }

  onMounted(() => {
    window.addEventListener('dv:session-expired', onSessionExpired)
    window.addEventListener('dv:demo-mode', onDemoMode)
  })

  onBeforeUnmount(() => {
    window.removeEventListener('dv:session-expired', onSessionExpired)
    window.removeEventListener('dv:demo-mode', onDemoMode)
  })
</script>

<style>
  html,
  body,
  #app {
    margin: 0;
    padding: 0;
    height: 100%;
  }
</style>
