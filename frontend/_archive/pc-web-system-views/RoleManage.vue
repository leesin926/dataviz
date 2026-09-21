<template>
  <div class="role-manage">
    <div class="page-header">
      <h2>角色管理</h2>
      <el-button type="primary" @click="handleCreate">新增</el-button>
    </div>
    <el-card shadow="never">
      <el-table :data="list" v-loading="loading" stripe>
        <el-table-column prop="roleName" label="角色名称" />
        <el-table-column prop="roleKey" label="角色标识" />
        <el-table-column prop="description" label="描述" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'">{{ row.status === 1 ? '启用' : '禁用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220">
          <template #default="{ row }">
            <el-button link type="primary">编辑</el-button>
            <el-button link type="primary">权限</el-button>
            <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
  import { ref, onMounted } from 'vue'
  import { ElMessage, ElMessageBox } from 'element-plus'
  import type { Role } from '@dataviz/shared-types'
  import { listRoles, deleteRole } from '@dataviz/api-client'

  const loading = ref(false)
  const list = ref<Role[]>([])

  async function loadData() {
    loading.value = true
    try {
      const res = await listRoles()
      list.value = res.list
    } catch (e) {
      ElMessage.error((e as Error).message)
    } finally {
      loading.value = false
    }
  }

  function handleCreate() {
    ElMessage.info('新增角色')
  }

  async function handleDelete(row: Role) {
    await ElMessageBox.confirm(`确认删除角色 "${row.roleName}"?`, '警告', { type: 'warning' })
    try {
      await deleteRole(row.id)
      ElMessage.success('删除成功')
      loadData()
    } catch (e) {
      ElMessage.error((e as Error).message)
    }
  }

  onMounted(() => loadData())
</script>

<style lang="scss" scoped>
  .role-manage {
    .page-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 16px;
      h2 {
        margin: 0;
        font-size: 18px;
      }
    }
  }
</style>
