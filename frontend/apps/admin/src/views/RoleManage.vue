<template>
  <div class="dv-page">
    <div class="dv-page-head">
      <div>
        <h2>{{ t('role.title') }}</h2>
        <p class="dv-page-desc">{{ t('common.appSubtitle') }}</p>
      </div>
      <div class="dv-page-actions">
        <el-button v-permission="'system:role:add'" type="primary" @click="openCreate">{{ t('role.create') }}</el-button>
      </div>
    </div>

    <el-card shadow="never">
      <el-table :data="list" v-loading="loading" stripe>
        <el-table-column prop="roleName" :label="t('role.roleName')" min-width="140" />
        <el-table-column prop="roleCode" :label="t('role.roleCode')" min-width="150">
          <template #default="{ row }">
            <code class="dv-mono">{{ row.roleCode }}</code>
          </template>
        </el-table-column>
        <el-table-column prop="description" :label="t('common.description')" min-width="180" show-overflow-tooltip />
        <el-table-column prop="sortOrder" :label="t('role.sort')" width="80" align="center" />
        <el-table-column :label="t('common.status')" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" disable-transitions>
              {{ row.status === 1 ? t('common.enabled') : t('common.disabled') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('common.operations')" width="240" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="'system:role:edit'" link type="primary" @click="openEdit(row)">{{ t('common.edit') }}</el-button>
            <!-- .all 是必须的：抽屉要打两个控制器的接口 —— 回显/保存是 /role/{id}/permissions（system:role:edit），
                 但那棵树本身在 /permission/tree 上，它的类级码是 system:menu:list（PermissionController:26）。
                 只判 role:edit 时，一个"有编辑权但没菜单读权"的角色会点开一个必定报错的抽屉；
                 而这种角色恰恰就是本抽屉自己发得出来的，所以它不是假想情况。 -->
            <el-button v-permission.all="['system:role:edit', 'system:menu:list']" link type="primary" @click="openAssign(row)">{{ t('role.assign') }}</el-button>
            <el-button v-permission="'system:role:delete'" link type="danger" @click="onDelete(row)">{{ t('common.delete') }}</el-button>
          </template>
        </el-table-column>
      </el-table>
      <p class="dv-stat-label dv-mt">{{ t('common.total', { n: list.length }) }}</p>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="editingId ? t('role.editTitle') : t('role.createTitle')" width="480px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="96px">
        <el-form-item :label="t('role.roleName')" prop="roleName">
          <el-input v-model="form.roleName" />
        </el-form-item>
        <el-form-item :label="t('role.roleCode')" prop="roleCode">
          <el-input
            v-model="form.roleCode"
            :disabled="!!editingId"
            :placeholder="editingId ? t('role.codeLocked') : t('role.codePlaceholder')"
          />
        </el-form-item>
        <el-form-item :label="t('common.description')">
          <el-input v-model="form.description" type="textarea" :rows="2" />
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

    <el-drawer v-model="drawerVisible" :title="t('role.assignTitle', { name: assignTarget?.roleName ?? '' })" size="560px">
      <div v-loading="assigning">
        <el-alert class="assign-alert" type="info" :closable="false" show-icon :title="t('role.assignHint')" />
        <p v-if="orphanIds.length" class="dv-stat-label assign-orphan">
          {{ t('role.assignOrphans', { n: orphanIds.length }) }}
        </p>
        <el-empty v-if="!assigning && !permissionTree.length" :description="t('role.assignEmptyTree')" />
        <el-tree
          v-else
          ref="treeRef"
          :data="permissionTree"
          :props="{ label: 'permissionName', children: 'children' }"
          node-key="id"
          show-checkbox
          :render-after-expand="false"
          default-expand-all
          @check="onCheck"
        >
          <template #default="{ data }">
            <span class="assign-node">
              <span>{{ data.permissionName }}</span>
              <el-tag v-if="parentOnlyIds.includes(String(data.id))" size="small" type="warning" disable-transitions>
                {{ t('role.assignParentOnly') }}
              </el-tag>
              <code class="dv-mono assign-node-code">{{ data.permissionCode }}</code>
            </span>
          </template>
        </el-tree>
      </div>
      <template #footer>
        <div class="assign-footer">
          <span class="dv-stat-label">{{ t('role.assignSelected', { n: checkedCount }) }}</span>
          <span>
            <el-button @click="drawerVisible = false">{{ t('common.cancel') }}</el-button>
            <el-button type="primary" :loading="savingAssign" @click="onAssignSubmit">{{ t('common.save') }}</el-button>
          </span>
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
  import { computed, nextTick, onMounted, reactive, ref } from 'vue'
  import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
  import type { PermissionNode, Role } from '@dataviz/shared-types'
  import { rowsOf } from '@dataviz/shared-types'
  import {
    assignRolePermissions,
    createRole,
    deleteRole,
    getPermissionTree,
    getRolePermissionIds,
    listRoles,
    updateRole,
  } from '@dataviz/api-client'
  import { useI18n } from 'vue-i18n'

  const { t } = useI18n()

  const loading = ref(false)
  const saving = ref(false)
  const list = ref<Role[]>([])
  const dialogVisible = ref(false)
  const editingId = ref<string | number>('')
  const formRef = ref<FormInstance>()
  const form = reactive({ roleName: '', roleCode: '', description: '', status: 1 as 0 | 1 })

  const rules = computed<FormRules>(() => ({
    roleName: [{ required: true, message: t('role.nameRequired'), trigger: 'blur' }],
    roleCode: [{ required: true, message: t('role.codeRequired'), trigger: 'blur' }],
  }))

  async function load() {
    loading.value = true
    try {
      list.value = rowsOf(await listRoles())
    } catch (e) {
      ElMessage.error((e as Error).message)
    } finally {
      loading.value = false
    }
  }

  function openCreate() {
    editingId.value = ''
    Object.assign(form, { roleName: '', roleCode: '', description: '', status: 1 })
    dialogVisible.value = true
  }

  function openEdit(row: Role) {
    editingId.value = row.id
    Object.assign(form, {
      roleName: row.roleName,
      roleCode: row.roleCode,
      description: row.description ?? '',
      status: row.status,
    })
    dialogVisible.value = true
  }

  async function onSubmit() {
    const valid = await formRef.value?.validate().catch(() => false)
    if (!valid) return
    saving.value = true
    try {
      if (editingId.value) await updateRole(editingId.value, { ...form })
      else await createRole({ ...form })
      ElMessage.success(t('common.success'))
      dialogVisible.value = false
      load()
    } catch (e) {
      ElMessage.error((e as Error).message)
    } finally {
      saving.value = false
    }
  }

  async function onDelete(row: Role) {
    const confirmed = await ElMessageBox.confirm(t('common.confirmDelete', { name: row.roleName }), t('common.warning'), {
      type: 'warning',
    }).catch(() => false)
    if (!confirmed) return
    try {
      await deleteRole(row.id)
      ElMessage.success(t('role.deleted'))
      load()
    } catch (e) {
      ElMessage.error((e as Error).message)
    }
  }

  // ————————————————————————————————— 分配权限（抽屉）—————————————————————————————————
  // el-tree 的实例只用到这两个方法，声明成局部接口而不是 import 组件类型（本 app 的构建不带类型门禁）
  interface PermissionTreeExpose {
    setCheckedKeys: (keys: Array<string | number>) => void
    getCheckedKeys: () => Array<string | number>
  }

  const drawerVisible = ref(false)
  const assigning = ref(false)
  const savingAssign = ref(false)
  const assignTarget = ref<Role | null>(null)
  const treeRef = ref<PermissionTreeExpose>()
  const permissionTree = ref<PermissionNode[]>([])
  const checkedCount = ref(0)
  /** 后端回来的原始授权 id（字符串化后用于比对与保留） */
  const assignedIds = ref<string[]>([])
  const orphanIds = ref<string[]>([])
  /** 库里"只授了本级、其下子项一个都没授"的项：联动树上它无法既勾中父级又不扩出子级，故用标签显形 */
  const parentOnlyIds = ref<string[]>([])

  /** 键一律字符串化：后端 id 是 Long（JSON 里是数字），而我们所有的比对都在字符串域里做 */
  const parentOf = new Map<string, string>()
  const originalOf = new Map<string, string | number>()
  const childKeys = new Set<string>()
  /** 每个节点自身 + 其整棵子树的键；用来判定"用户刚点的这个节点跟那枚「仅本级」标签有没有关系" */
  const subtreeOf = new Map<string, string[]>()

  function indexTree(nodes: PermissionNode[], parentId = ''): string[] {
    const keys: string[] = []
    nodes.forEach((node) => {
      const key = String(node.id)
      originalOf.set(key, node.id)
      if (parentId) parentOf.set(key, parentId)
      const descendants = node.children?.length ? indexTree(node.children, key) : []
      if (descendants.length) childKeys.add(key)
      subtreeOf.set(key, [key, ...descendants])
      keys.push(key, ...descendants)
    })
    return keys
  }

  /** 已勾项的祖先一律并入 payload：只授叶子码会让"接口放行、菜单看不见"（D57 记过的那个反方向） */
  function withAncestors(keys: string[]): string[] {
    const out = new Set(keys)
    keys.forEach((key) => {
      let cursor = parentOf.get(key)
      while (cursor && !out.has(cursor)) {
        out.add(cursor)
        cursor = parentOf.get(cursor)
      }
    })
    return Array.from(out)
  }

  async function openAssign(row: Role) {
    assignTarget.value = row
    checkedCount.value = 0
    orphanIds.value = []
    parentOnlyIds.value = []
    drawerVisible.value = true
    assigning.value = true
    try {
      const [tree, ids] = await Promise.all([
        permissionTree.value.length ? Promise.resolve(permissionTree.value) : getPermissionTree(),
        getRolePermissionIds(row.id),
      ])
      permissionTree.value = tree
      originalOf.clear()
      parentOf.clear()
      childKeys.clear()
      subtreeOf.clear()
      indexTree(tree)
      assignedIds.value = ids.map((id) => String(id))
      orphanIds.value = assignedIds.value.filter((id) => !originalOf.has(id))
      // 联动模式下喂给 el-tree 的键只能是叶子：tree-store.mjs:190-194 里非叶节点会 `setChecked(true, true)`
      // 向下扩，并且 cacheCheckedChild 让后续"未授的子项"再也撤不回 false —— 拿父级码去回显等于替角色扩权。
      // 反过来只喂叶子时，父级由 reInitChecked 自动算成全勾/半勾，正好是我们要的形状。
      const granted = new Set(assignedIds.value)
      const ancestorsOfGranted = new Set<string>()
      granted.forEach((id) => {
        let cursor = parentOf.get(id)
        while (cursor) {
          ancestorsOfGranted.add(cursor)
          cursor = parentOf.get(cursor)
        }
      })
      parentOnlyIds.value = assignedIds.value.filter(
        (id) => originalOf.has(id) && childKeys.has(id) && !ancestorsOfGranted.has(id)
      )
      const echoKeys = assignedIds.value
        .filter((id) => originalOf.has(id) && !childKeys.has(id))
        .map((id) => originalOf.get(id)!)
      await nextTick()
      treeRef.value?.setCheckedKeys(echoKeys)
      onCheck()
    } catch (e) {
      ElMessage.error((e as Error).message)
      drawerVisible.value = false
    } finally {
      assigning.value = false
    }
  }

  /** 将提交的那一份：勾中的 + 其半勾祖先 + 只授本级项 + 孤儿项。半勾的父级不在 getCheckedKeys 里（tree-store.mjs:128 默认不含 indeterminate），所以必须自己补。 */
  function currentPayloadKeys(): string[] {
    const checked = (treeRef.value?.getCheckedKeys() ?? []).map((id) => String(id))
    return Array.from(new Set(withAncestors(checked).concat(parentOnlyIds.value, orphanIds.value)))
  }

  /** @check 的第一个参数是刚被点的那个节点的数据对象；openAssign 回显完成后是无参调用，只用来重算计数 */
  function onCheck(node?: PermissionNode) {
    if (node) {
      // 用户一动到那枚「仅本级」标签所在的子树（点它本身、点它的祖先、点它的后代），这条保留就作废：
      // 他的意图已经从"别动我库里那份"变成了"这一片我要重新安排"。不清的话这条授权永远撤不掉。
      const clicked = String(node.id)
      const clickedSubtree = subtreeOf.get(clicked) ?? []
      parentOnlyIds.value = parentOnlyIds.value.filter(
        (id) => !clickedSubtree.includes(id) && !(subtreeOf.get(id) ?? []).includes(clicked)
      )
    }
    checkedCount.value = currentPayloadKeys().length
  }

  async function onAssignSubmit() {
    const target = assignTarget.value
    if (!target) return
    const keys = currentPayloadKeys()
    if (!keys.length && assignedIds.value.length) {
      const confirmed = await ElMessageBox.confirm(
        t('role.assignEmptyConfirm', { n: assignedIds.value.length }),
        t('common.warning'),
        { type: 'warning' },
      ).catch(() => false)
      if (!confirmed) return
    }
    // 送回后端的是树里的原始 id（数字），不是字符串化的键 —— 后端签名是 List<Long>
    const payload = keys.map((key) => originalOf.get(key) ?? key)
    savingAssign.value = true
    try {
      await assignRolePermissions(target.id, payload)
      ElMessage.success(t('role.assignSaved'))
      drawerVisible.value = false
    } catch (e) {
      ElMessage.error((e as Error).message)
    } finally {
      savingAssign.value = false
    }
  }

  onMounted(load)
</script>

<style lang="scss" scoped>
  .dv-mt {
    margin-top: var(--dv-space-md);
  }

  .assign-alert {
    margin-bottom: var(--dv-space-md);
  }

  .assign-orphan {
    margin: 0 0 var(--dv-space-sm);
    color: var(--dv-warning, #b8860b);
  }

  .assign-node {
    display: flex;
    align-items: center;
    gap: var(--dv-space-sm);
  }

  .assign-node-code {
    font-size: 12px;
    color: var(--dv-text-muted, #8a97a8);
  }

  .assign-footer {
    display: flex;
    align-items: center;
    justify-content: space-between;
    width: 100%;
  }
</style>
