<template>
  <div class="user-manage">
    <div class="page-header">
      <h2>用户管理</h2>
      <el-button type="primary" @click="handleCreate">新增</el-button>
    </div>
    <el-row :gutter="16">
      <el-col :span="6">
        <el-card shadow="never">
          <el-tree :data="deptTree" :props="{ label: 'name', children: 'children' }" @node-click="handleDeptClick" default-expand-all />
        </el-card>
      </el-col>
      <el-col :span="18">
        <el-card shadow="never">
          <el-table :data="list" v-loading="loading" stripe>
            <el-table-column prop="username" label="用户名" />
            <el-table-column prop="nickname" label="昵称" />
            <el-table-column prop="email" label="邮箱" />
            <el-table-column prop="phone" label="手机" />
            <el-table-column prop="status" label="状态" width="100">
              <template #default="{ row }">
                <el-switch v-model="row.status" :active-value="1" :inactive-value="0" />
              </template>
            </el-table-column>
            <el-table-column label="操作" width="200">
              <template #default="{ row }">
                <el-button link type="primary" @click="handleEdit(row)">编辑</el-button>
                <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
  import { ref, onMounted } from 'vue'
  import { ElMessage, ElMessageBox } from 'element-plus'
  import type { User, Dept } from '@dataviz/shared-types'
  import { listUsers, deleteUser, getDeptTree } from '@dataviz/api-client'

  const loading = ref(false)
  const list = ref<User[]>([])
  const deptTree = ref<Dept[]>([])
  const currentDeptId = ref<string | number>('')

  async function loadData() {
    loading.value = true
    try {
      const res = await listUsers({ pageNum: 1, pageSize: 100, deptId: currentDeptId.value || undefined })
      list.value = res.list
    } catch (e) {
      ElMessage.error((e as Error).message)
    } finally {
      loading.value = false
    }
  }

  async function loadDeptTree() {
    try {
      deptTree.value = await getDeptTree()
    } catch (e) {
      ElMessage.error((e as Error).message)
    }
  }

  function handleDeptClick(data: Dept) {
    currentDeptId.value = data.id
    loadData()
  }

  function handleCreate() {
    ElMessage.info('新增用户')
  }

  function handleEdit(_row: User) {
    ElMessage.info('编辑用户')
  }

  async function handleDelete(row: User) {
    await ElMessageBox.confirm(`确认删除用户 "${row.username}"?`, '警告', { type: 'warning' })
    try {
      await deleteUser(row.id)
      ElMessage.success('删除成功')
      loadData()
    } catch (e) {
      ElMessage.error((e as Error).message)
    }
  }

  onMounted(() => {
    loadData()
    loadDeptTree()
  })
</script>

<style lang="scss" scoped>
  .user-manage {
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
