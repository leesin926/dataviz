<template>
  <div class="dv-page">
    <div class="dv-page-head">
      <div>
        <h2>{{ t('config.title') }}</h2>
        <p class="dv-page-desc">{{ t('config.desc') }}</p>
      </div>
      <div class="dv-page-actions">
        <el-button type="primary" @click="openCreate">{{ t('config.create') }}</el-button>
      </div>
    </div>

    <div class="dv-toolbar">
      <el-input
        v-model="keyword"
        :placeholder="t('common.keywordPlaceholder')"
        clearable
        style="width: 220px"
        @keyup.enter="reload"
        @clear="reload"
      />
      <el-select v-model="configType" :placeholder="t('config.configType')" clearable style="width: 140px" @change="reload">
        <el-option :label="t('config.typeSystem')" value="SYSTEM" />
        <el-option :label="t('config.typeCustom')" value="CUSTOM" />
      </el-select>
      <el-button type="primary" :loading="loading" @click="reload">{{ t('common.search') }}</el-button>
      <el-button @click="onReset">{{ t('common.reset') }}</el-button>
    </div>

    <el-card shadow="never" class="global-switch-card">
      <div class="switch-row">
        <div>
          <p class="switch-title">{{ t('config.mourningTitle') }}</p>
          <p class="switch-desc">{{ t('config.mourningDesc') }}</p>
        </div>
        <el-switch v-model="mourningOn" :loading="mourningSaving" @change="onMourningChange" />
      </div>
    </el-card>

    <el-card shadow="never">
      <el-table :data="list" v-loading="loading" stripe>
        <el-table-column prop="configKey" :label="t('config.configKey')" min-width="200">
          <template #default="{ row }">
            <code class="dv-mono">{{ row.configKey }}</code>
          </template>
        </el-table-column>
        <el-table-column prop="configValue" :label="t('config.configValue')" min-width="200" show-overflow-tooltip />
        <el-table-column :label="t('config.configType')" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="row.configType === 'SYSTEM' ? 'info' : 'primary'" size="small" disable-transitions>
              {{ row.configType === 'SYSTEM' ? t('config.typeSystem') : t('config.typeCustom') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" :label="t('common.remark')" min-width="160" show-overflow-tooltip />
        <el-table-column prop="updateTime" :label="t('common.updatedAt')" width="170" />
        <el-table-column :label="t('common.operations')" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">{{ t('common.edit') }}</el-button>
            <el-button v-if="row.configType !== 'SYSTEM'" link type="danger" @click="onDelete(row)">
              {{ t('common.delete') }}
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <span class="dv-text-3">{{ t('common.empty') }}</span>
        </template>
      </el-table>
      <el-pagination
        class="dv-pager"
        layout="total, sizes, prev, pager, next"
        :total="total"
        :current-page="pageNum"
        :page-size="pageSize"
        @current-change="(p: number) => { pageNum = p; load() }"
        @size-change="(s: number) => { pageSize = s; pageNum = 1; load() }"
      />
    </el-card>

    <el-dialog v-model="dialogVisible" :title="editingId ? t('config.editTitle') : t('config.createTitle')" width="520px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="92px">
        <el-form-item :label="t('config.configKey')" prop="configKey">
          <el-input v-model="form.configKey" :disabled="!!editingId" class="dv-mono" placeholder="screen.export.max-rows" />
        </el-form-item>
        <el-form-item :label="t('config.configValue')" prop="configValue">
          <el-input v-model="form.configValue" type="textarea" :rows="3" :placeholder="t('config.valuePlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('config.configType')">
          <el-radio-group v-model="form.configType" :disabled="systemEditing">
            <el-radio value="SYSTEM">{{ t('config.typeSystem') }}</el-radio>
            <el-radio value="CUSTOM">{{ t('config.typeCustom') }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item :label="t('common.remark')">
          <el-input v-model="form.remark" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="saving" @click="onSubmit">{{ t('common.save') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
  import { computed, onMounted, reactive, ref } from 'vue'
  import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
  import type { SysConfig } from '@dataviz/shared-types'
  import { rowsOf, totalOf } from '@dataviz/shared-types'
  import { setGlobalMourning } from '@dataviz/shared-styles'
  import {
    MOURNING_CONFIG_KEY,
    createConfig,
    deleteConfig,
    getConfigByKey,
    pageConfigs,
    updateConfig,
  } from '@dataviz/api-client'
  import { useI18n } from 'vue-i18n'

  const { t } = useI18n()

  /** 库内备注保持中文，不随管理端界面语言变化 */
  const MOURNING_REMARK = '全局哀悼模式：true 时管理端与设计端/分享页整体灰度'

  const loading = ref(false)
  const saving = ref(false)
  const list = ref<SysConfig[]>([])
  const total = ref(0)
  const pageNum = ref(1)
  const pageSize = ref(20)
  const keyword = ref('')
  const configType = ref('')

  const dialogVisible = ref(false)
  const editingId = ref<string | number>('')
  const systemEditing = ref(false)
  const formRef = ref<FormInstance>()
  const form = reactive({ configKey: '', configValue: '', configType: 'CUSTOM', remark: '' })

  const rules = computed<FormRules>(() => ({
    configKey: [{ required: true, message: t('config.configKey'), trigger: 'blur' }],
    configValue: [{ required: true, message: t('config.configValue'), trigger: 'blur' }],
  }))

  async function load() {
    loading.value = true
    try {
      const res = await pageConfigs({
        pageNum: pageNum.value,
        pageSize: pageSize.value,
        keyword: keyword.value || undefined,
        configType: configType.value || undefined,
      })
      list.value = rowsOf(res)
      total.value = totalOf(res)
    } catch (e) {
      ElMessage.error((e as Error).message)
    } finally {
      loading.value = false
    }
  }

  function reload() {
    pageNum.value = 1
    load()
  }

  function onReset() {
    keyword.value = ''
    configType.value = ''
    reload()
  }

  function openCreate() {
    editingId.value = ''
    systemEditing.value = false
    Object.assign(form, { configKey: '', configValue: '', configType: 'CUSTOM', remark: '' })
    dialogVisible.value = true
  }

  function openEdit(row: SysConfig) {
    editingId.value = row.id
    systemEditing.value = row.configType === 'SYSTEM'
    Object.assign(form, {
      configKey: row.configKey,
      configValue: row.configValue ?? '',
      configType: row.configType ?? 'CUSTOM',
      remark: row.remark ?? '',
    })
    dialogVisible.value = true
  }

  async function onSubmit() {
    const valid = await formRef.value?.validate().catch(() => false)
    if (!valid) return
    saving.value = true
    try {
      if (editingId.value) await updateConfig({ ...form })
      else await createConfig({ ...form })
      ElMessage.success(t('config.saved'))
      dialogVisible.value = false
      load()
    } catch (e) {
      ElMessage.error((e as Error).message)
    } finally {
      saving.value = false
    }
  }

  async function onDelete(row: SysConfig) {
    const confirmed = await ElMessageBox.confirm(
      t('common.confirmDelete', { name: row.configKey }),
      t('common.warning'),
      { type: 'warning' },
    ).catch(() => false)
    if (!confirmed) return
    try {
      await deleteConfig(row.id)
      ElMessage.success(t('config.deleted'))
      load()
    } catch (e) {
      ElMessage.error((e as Error).message)
    }
  }

  onMounted(() => {
    load()
    loadMourning()
  })

  // ---------- 全局哀悼模式：落到系统配置 screen.mourning.enabled，所有端一起灰度 ----------
  const mourningOn = ref(false)
  const mourningSaving = ref(false)
  const mourningConfigured = ref(false)

  async function loadMourning() {
    const value = await getConfigByKey(MOURNING_CONFIG_KEY).catch(() => null)
    mourningConfigured.value = value !== null && value !== undefined
    mourningOn.value = value === 'true'
    setGlobalMourning(mourningOn.value)
  }

  async function onMourningChange(checked: boolean | string | number) {
    const on = !!checked
    mourningSaving.value = true
    try {
      const payload = {
        configKey: MOURNING_CONFIG_KEY,
        configValue: on ? 'true' : 'false',
        configType: 'SYSTEM',
        remark: MOURNING_REMARK,
      }
      if (mourningConfigured.value) await updateConfig(payload)
      else {
        await createConfig(payload)
        mourningConfigured.value = true
      }
      setGlobalMourning(on)
      ElMessage.success(t('config.mourningSaved'))
      load()
    } catch (e) {
      mourningOn.value = !on
      ElMessage.error((e as Error).message)
    } finally {
      mourningSaving.value = false
    }
  }
</script>

<style lang="scss" scoped>
  .global-switch-card {
    margin-bottom: var(--dv-space-lg);
  }
  .switch-row {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: var(--dv-space-lg);
  }
  .switch-title {
    margin: 0;
    font-size: var(--dv-font-md);
    font-weight: 600;
    color: var(--dv-text-1);
  }
  .switch-desc {
    margin: 4px 0 0;
    font-size: var(--dv-font-sm);
    color: var(--dv-text-3);
  }
</style>
