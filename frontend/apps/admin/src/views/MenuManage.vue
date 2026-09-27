<template>
  <div class="dv-page">
    <div class="dv-page-head">
      <div>
        <h2>{{ t('menu.title') }}</h2>
        <p class="dv-page-desc">{{ t('menu.desc') }}</p>
      </div>
      <div class="dv-page-actions">
        <el-button @click="load">{{ t('common.refresh') }}</el-button>
        <el-button v-permission="'system:menu:add'" type="primary" @click="openCreate()">{{ t('menu.create') }}</el-button>
      </div>
    </div>

    <el-card shadow="never">
      <el-table
        :data="tree"
        v-loading="loading"
        row-key="id"
        default-expand-all
        :tree-props="{ children: 'children' }"
      >
        <el-table-column prop="permissionName" :label="t('menu.menuName')" min-width="180" />
        <el-table-column :label="t('common.type')" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="typeTag(row.type)" size="small" disable-transitions>{{ typeLabel(row.type) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="permissionCode" :label="t('menu.code')" min-width="180">
          <template #default="{ row }">
            <code class="dv-mono">{{ row.permissionCode }}</code>
          </template>
        </el-table-column>
        <el-table-column prop="path" :label="t('menu.path')" min-width="150" show-overflow-tooltip />
        <el-table-column prop="icon" :label="t('menu.icon')" width="110" show-overflow-tooltip />
        <el-table-column prop="sortOrder" :label="t('menu.sort')" width="70" align="center" />
        <el-table-column :label="t('common.status')" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small" disable-transitions>
              {{ row.status === 1 ? t('common.enabled') : t('common.disabled') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('common.operations')" width="200" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.type !== 3" v-permission="'system:menu:add'" link type="primary" @click="openCreate(row)">
              {{ t('menu.addChild') }}
            </el-button>
            <el-button v-permission="'system:menu:edit'" link type="primary" @click="openEdit(row)">{{ t('common.edit') }}</el-button>
            <el-button v-permission="'system:menu:delete'" link type="danger" @click="onDelete(row)">{{ t('common.delete') }}</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <span class="dv-text-3">{{ t('common.empty') }}</span>
        </template>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="editingId ? t('menu.editTitle') : t('menu.createTitle')" width="520px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="96px">
        <el-form-item :label="t('menu.parent')">
          <el-tree-select
            v-model="form.parentId"
            :data="parentOptions"
            :props="{ label: 'permissionName', children: 'children', value: 'id' }"
            node-key="id"
            check-strictly
            :render-after-expand="false"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item :label="t('common.type')">
          <el-radio-group v-model="form.type">
            <el-radio :value="1">{{ t('menu.typeCatalog') }}</el-radio>
            <el-radio :value="2">{{ t('menu.typeMenu') }}</el-radio>
            <el-radio :value="3">{{ t('menu.typeButton') }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item :label="t('menu.menuName')" prop="permissionName">
          <el-input v-model="form.permissionName" />
        </el-form-item>
        <el-form-item :label="t('menu.code')" prop="permissionCode">
          <el-input v-model="form.permissionCode" class="dv-mono" />
        </el-form-item>
        <el-form-item v-if="form.type !== 3" :label="t('menu.path')">
          <el-input v-model="form.path" placeholder="/screen" />
        </el-form-item>
        <el-form-item v-if="form.type !== 3" :label="t('menu.icon')">
          <el-input v-model="form.icon" placeholder="DataLine" />
        </el-form-item>
        <el-form-item :label="t('menu.sort')">
          <el-input-number v-model="form.sortOrder" :min="0" :max="999" controls-position="right" />
        </el-form-item>
        <el-form-item :label="t('common.status')">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" />
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
  import type { PermissionNode } from '@dataviz/shared-types'
  import { createPermission, deletePermission, getPermissionTree, updatePermission } from '@dataviz/api-client'
  import { useI18n } from 'vue-i18n'

  const { t } = useI18n()

  const loading = ref(false)
  const saving = ref(false)
  const dialogVisible = ref(false)
  const editingId = ref<string | number>('')
  const tree = ref<PermissionNode[]>([])
  const formRef = ref<FormInstance>()
  const form = reactive({
    parentId: '0' as string | number,
    permissionName: '',
    permissionCode: '',
    type: 2,
    path: '',
    icon: '',
    sortOrder: 0,
    status: 1,
  })

  const rules = computed<FormRules>(() => ({
    permissionName: [{ required: true, message: t('menu.nameRequired'), trigger: 'blur' }],
    permissionCode: [{ required: true, message: t('menu.codeRequired'), trigger: 'blur' }],
  }))

  /** 按钮不能作为上级，故从选项中剔除 */
  const parentOptions = computed<PermissionNode[]>(() => {
    const root = { id: '0', permissionName: t('menu.root'), children: filterMenuable(tree.value) } as unknown as PermissionNode
    return [root]
  })

  function filterMenuable(nodes: PermissionNode[]): PermissionNode[] {
    return nodes
      .filter((node) => node.type !== 3)
      .map((node) => ({ ...node, children: filterMenuable(node.children ?? []) }))
  }

  function typeLabel(type: number): string {
    if (type === 1) return t('menu.typeCatalog')
    if (type === 3) return t('menu.typeButton')
    return t('menu.typeMenu')
  }

  function typeTag(type: number): 'info' | 'primary' | 'success' {
    if (type === 1) return 'info'
    if (type === 3) return 'success'
    return 'primary'
  }

  async function load() {
    loading.value = true
    try {
      tree.value = await getPermissionTree()
    } catch (e) {
      ElMessage.error((e as Error).message)
    } finally {
      loading.value = false
    }
  }

  function reset(parentId: string | number, type = 2) {
    editingId.value = ''
    Object.assign(form, { parentId, permissionName: '', permissionCode: '', type, path: '', icon: '', sortOrder: 0, status: 1 })
  }

  function openCreate(parent?: PermissionNode) {
    reset(parent?.id ?? '0', parent ? (parent.type === 1 ? 2 : 3) : 2)
    dialogVisible.value = true
  }

  function openEdit(row: PermissionNode) {
    editingId.value = row.id
    Object.assign(form, {
      parentId: row.parentId ?? '0',
      permissionName: row.permissionName,
      permissionCode: row.permissionCode,
      type: row.type,
      path: row.path ?? '',
      icon: row.icon ?? '',
      sortOrder: row.sortOrder ?? 0,
      status: row.status ?? 1,
    })
    dialogVisible.value = true
  }

  async function onSubmit() {
    const valid = await formRef.value?.validate().catch(() => false)
    if (!valid) return
    saving.value = true
    try {
      const payload = { ...form, parentId: form.parentId || '0' }
      if (editingId.value) await updatePermission(editingId.value, payload)
      else await createPermission(payload)
      ElMessage.success(t('common.success'))
      dialogVisible.value = false
      load()
    } catch (e) {
      ElMessage.error((e as Error).message)
    } finally {
      saving.value = false
    }
  }

  async function onDelete(row: PermissionNode) {
    const confirmed = await ElMessageBox.confirm(t('common.confirmDelete', { name: row.permissionName }), t('common.warning'), {
      type: 'warning',
    }).catch(() => false)
    if (!confirmed) return
    try {
      await deletePermission(row.id)
      ElMessage.success(t('menu.deleted'))
      load()
    } catch (e) {
      ElMessage.error((e as Error).message)
    }
  }

  onMounted(load)
</script>
