<template>
  <div class="menu-manage">
    <div class="page-header">
      <h2>菜单权限</h2>
      <el-button type="primary" @click="handleCreate">新增</el-button>
    </div>
    <el-card shadow="never">
      <el-table :data="list" v-loading="loading" row-key="id" :tree-props="{ children: 'children' }">
        <el-table-column prop="name" label="菜单名称" />
        <el-table-column prop="type" label="类型" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="menuTypeTag(row.type)">{{ row.type }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="code" label="权限标识" />
        <el-table-column prop="path" label="路由路径" />
        <el-table-column prop="icon" label="图标" width="80" />
        <el-table-column prop="sort" label="排序" width="80" />
        <el-table-column label="操作" width="200">
          <template #default="{ row }">
            <el-button link type="primary">编辑</el-button>
            <el-button link type="danger">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
  import { ref } from 'vue'
  import type { Permission } from '@dataviz/shared-types'

  const loading = ref(false)
  const list = ref<Permission[]>([
    {
      id: 1,
      type: 'menu',
      name: '仪表盘',
      code: 'dashboard:list',
      path: '/dashboard',
      sort: 1,
      children: [
        { id: 11, parentId: 1, type: 'button', name: '新建仪表盘', code: 'dashboard:create', sort: 1 },
        { id: 12, parentId: 1, type: 'button', name: '编辑仪表盘', code: 'dashboard:edit', sort: 2 },
      ],
    },
    {
      id: 2,
      type: 'menu',
      name: '系统管理',
      code: 'system',
      path: '/system',
      sort: 10,
    },
  ])

  function menuTypeTag(type: string) {
    const map: Record<string, string> = { menu: 'primary', button: 'success', api: 'warning' }
    return map[type] || 'info'
  }

  function handleCreate() {
    // TODO: 打开对话框
  }
</script>

<style lang="scss" scoped>
  .menu-manage {
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
