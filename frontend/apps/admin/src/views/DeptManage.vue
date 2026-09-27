<template>
  <div class="dv-page">
    <div class="dv-page-head">
      <div>
        <h2>{{ t('dept.title') }}</h2>
        <p class="dv-page-desc">{{ t('dept.desc') }}</p>
      </div>
      <div class="dv-page-actions">
        <el-button @click="load">{{ t('common.refresh') }}</el-button>
        <el-button v-permission="'system:dept:add'" type="primary" @click="openCreate()">{{ t('dept.create') }}</el-button>
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
        <el-table-column prop="deptName" :label="t('dept.name')" min-width="200" />
        <el-table-column prop="leader" :label="t('dept.leader')" width="130" show-overflow-tooltip />
        <el-table-column prop="phone" :label="t('dept.phone')" width="140" show-overflow-tooltip />
        <el-table-column prop="sortOrder" :label="t('dept.sort')" width="80" align="center" />
        <el-table-column :label="t('common.status')" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small" disable-transitions>
              {{ row.status === 1 ? t('common.enabled') : t('common.disabled') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('common.operations')" width="210" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="'system:dept:add'" link type="primary" @click="openCreate(row)">
              {{ t('dept.addChild') }}
            </el-button>
            <el-button v-permission="'system:dept:edit'" link type="primary" @click="openEdit(row)">{{ t('common.edit') }}</el-button>
            <el-button v-permission="'system:dept:delete'" link type="danger" @click="onDelete(row)">{{ t('common.delete') }}</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <span class="dv-text-3">{{ t('common.empty') }}</span>
        </template>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="editingId ? t('dept.editTitle') : t('dept.createTitle')" width="520px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="96px">
        <el-form-item :label="t('dept.parent')">
          <el-tree-select
            v-model="form.parentId"
            :data="parentOptions"
            :props="{ label: 'deptName', children: 'children', value: 'id' }"
            node-key="id"
            check-strictly
            :render-after-expand="false"
            :placeholder="t('dept.root')"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item :label="t('dept.name')" prop="deptName">
          <el-input v-model="form.deptName" maxlength="64" show-word-limit />
        </el-form-item>
        <el-form-item :label="t('dept.leader')">
          <el-input v-model="form.leader" maxlength="64" />
        </el-form-item>
        <el-form-item :label="t('dept.phone')">
          <el-input v-model="form.phone" maxlength="20" />
        </el-form-item>
        <el-form-item :label="t('dept.sort')">
          <el-input-number v-model="form.sortOrder" :min="0" :max="999" controls-position="right" />
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
  import type { Dept } from '@dataviz/shared-types'
  import { createDept, deleteDept, getDeptTree, updateDept } from '@dataviz/api-client'
  import { useI18n } from 'vue-i18n'

  const { t } = useI18n()

  /** 后端把顶级统一落成 0；表单里用同一个值，不再让 null 和 0 两种写法并存 */
  const ROOT = '0'

  const loading = ref(false)
  const saving = ref(false)
  const dialogVisible = ref(false)
  const editingId = ref<string | number>('')
  const tree = ref<Dept[]>([])
  const formRef = ref<FormInstance>()
  const form = reactive({
    parentId: ROOT as string | number,
    deptName: '',
    leader: '',
    phone: '',
    sortOrder: 0,
  })

  const rules = computed<FormRules>(() => ({
    deptName: [{ required: true, message: t('dept.nameRequired'), trigger: 'blur' }],
  }))

  /**
   * 编辑时把「自己 + 自己的整棵子树」从上级选项里剔掉。
   * 不剔的话下拉里就摆着一个点了必然被后端拒掉的选项（服务端成环校验是最后一道，不是给用户当提示用的）。
   */
  const parentOptions = computed<Dept[]>(() => {
    const root = { id: ROOT, deptName: t('dept.root'), children: pickable(tree.value) } as unknown as Dept
    return [root]
  })

  function pickable(nodes: Dept[]): Dept[] {
    return nodes
      .filter((node) => String(node.id) !== String(editingId.value))
      .map((node) => ({ ...node, children: pickable(node.children ?? []) }))
  }

  async function load() {
    loading.value = true
    try {
      tree.value = await getDeptTree()
    } catch (e) {
      ElMessage.error((e as Error).message)
    } finally {
      loading.value = false
    }
  }

  function openCreate(parent?: Dept) {
    editingId.value = ''
    Object.assign(form, { parentId: parent ? String(parent.id) : ROOT, deptName: '', leader: '', phone: '', sortOrder: 0 })
    dialogVisible.value = true
  }

  function openEdit(row: Dept) {
    editingId.value = row.id
    Object.assign(form, {
      parentId: row.parentId == null ? ROOT : String(row.parentId),
      deptName: row.deptName,
      leader: row.leader ?? '',
      phone: row.phone ?? '',
      sortOrder: row.sortOrder ?? 0,
    })
    dialogVisible.value = true
  }

  async function onSubmit() {
    const valid = await formRef.value?.validate().catch(() => false)
    if (!valid) return
    saving.value = true
    try {
      const payload = { ...form, parentId: Number(form.parentId) }
      if (editingId.value) await updateDept(editingId.value, payload)
      else await createDept(payload)
      ElMessage.success(t('common.success'))
      dialogVisible.value = false
      load()
    } catch (e) {
      ElMessage.error((e as Error).message)
    } finally {
      saving.value = false
    }
  }

  async function onDelete(row: Dept) {
    const confirmed = await ElMessageBox.confirm(t('common.confirmDelete', { name: row.deptName }), t('common.warning'), {
      type: 'warning',
    }).catch(() => false)
    if (!confirmed) return
    try {
      await deleteDept(row.id)
      ElMessage.success(t('dept.deleted'))
      load()
    } catch (e) {
      ElMessage.error((e as Error).message)
    }
  }

  onMounted(load)
</script>
