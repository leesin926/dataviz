<template>
  <div class="tenant-manage">
    <div class="page-header">
      <h2>租户管理</h2>
      <el-button type="primary" @click="handleCreate">新增</el-button>
    </div>
    <el-card shadow="never">
      <el-table :data="list" v-loading="loading" stripe>
        <el-table-column prop="code" label="租户编码" />
        <el-table-column prop="name" label="租户名称" />
        <el-table-column prop="contact" label="联系人" />
        <el-table-column prop="phone" label="电话" />
        <el-table-column prop="maxUsers" label="最大用户数" width="120" />
        <el-table-column prop="expireAt" label="到期时间" width="160" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'">{{ row.status === 1 ? '启用' : '禁用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200">
          <template #default>
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
  import type { Tenant } from '@dataviz/shared-types'

  const loading = ref(false)
  const list = ref<Tenant[]>([])

  function handleCreate() {
    // TODO
  }
</script>

<style lang="scss" scoped>
  .tenant-manage {
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
