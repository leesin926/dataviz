<template>
  <div class="dv-page file-page">
    <div class="dv-page-head">
      <div>
        <h2>{{ t('file.title') }}</h2>
        <p class="dv-page-desc">{{ t('file.desc') }}</p>
      </div>
      <div class="dv-page-actions">
        <el-upload v-permission="'file:write'" :show-file-list="false" :http-request="handleUpload" :multiple="true">
          <el-button type="primary" :loading="uploading">
            <el-icon><Upload /></el-icon>
            {{ t('file.upload') }}
          </el-button>
        </el-upload>
      </div>
    </div>

    <div class="dv-toolbar">
      <el-input v-model="keyword" :placeholder="t('file.keyword')" clearable style="width: 180px" @keyup.enter="reload" @clear="reload" />
      <el-input v-model="bizType" :placeholder="t('file.bizType')" clearable style="width: 150px" @keyup.enter="reload" @clear="reload" />
      <el-input v-model="uploaderId" :placeholder="t('file.uploaderId')" clearable style="width: 140px" @keyup.enter="reload" @clear="reload" />
      <el-select v-model="pageSize" style="width: 120px" @change="reload">
        <el-option v-for="s in PAGE_SIZES" :key="s" :label="`${s} / ${t('file.perPage')}`" :value="s" />
      </el-select>
      <el-button type="primary" :loading="loading" @click="reload">{{ t('common.search') }}</el-button>
      <el-button @click="onReset">{{ t('common.reset') }}</el-button>
    </div>

    <el-alert class="view-note" type="info" :closable="false" show-icon :title="t('file.publicNote')" />

    <el-card shadow="never">
      <el-table :data="list" v-loading="loading" stripe>
        <el-table-column :label="t('file.name')" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">
            <el-button v-if="isImage(row)" link type="primary" @click="preview(row)">{{ row.fileName }}</el-button>
            <span v-else>{{ row.fileName }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="fileType" :label="t('file.mime')" width="160" show-overflow-tooltip class-name="dv-mono" />
        <el-table-column :label="t('file.size')" width="110" align="right">
          <template #default="{ row }">{{ formatBytes(row.fileSize ?? 0) }}</template>
        </el-table-column>
        <el-table-column prop="bucket" :label="t('file.bucket')" width="140" show-overflow-tooltip />
        <el-table-column :label="t('file.uploader')" width="110" align="right">
          <template #default="{ row }">{{ row.createBy ?? '-' }}</template>
        </el-table-column>
        <el-table-column prop="createTime" :label="t('common.createdAt')" width="180" />
        <el-table-column :label="t('common.operations')" width="190" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="download(row)">{{ t('file.download') }}</el-button>
            <el-button v-permission="'file:write'" link type="danger" @click="remove(row)">{{ t('common.delete') }}</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <span class="dv-text-3">{{ t('common.empty') }}</span>
        </template>
      </el-table>
      <el-pagination
        v-if="total > 0"
        class="dv-pager"
        :current-page="pageNum"
        :page-size="pageSize"
        :total="total"
        :page-sizes="PAGE_SIZES"
        layout="total, sizes, prev, pager, next"
        @current-change="handlePageChange"
        @size-change="handleSizeChange"
      />
    </el-card>

    <el-dialog v-model="previewVisible" :title="previewName" width="640px">
      <img v-if="previewUrl" :src="previewUrl" class="preview-img" alt="" />
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
  import { onMounted, ref } from 'vue'
  import { ElMessage, ElMessageBox, type UploadRequestOptions } from 'element-plus'
  import { Upload } from '@element-plus/icons-vue'
  import { useI18n } from 'vue-i18n'
  import { rowsOf, totalOf } from '@dataviz/shared-types'
  import { downloadByBlob, formatBytes } from '@dataviz/shared-utils'
  import {
    deleteFile,
    downloadFile,
    fileViewUrl,
    listFiles,
    uploadFile,
    type FileInfo,
  } from '@dataviz/api-client'

  const { t } = useI18n()
  const PAGE_SIZES = [20, 50, 100, 200]

  const list = ref<FileInfo[]>([])
  const total = ref(0)
  const pageNum = ref(1)
  const pageSize = ref(20)
  const keyword = ref('')
  const bizType = ref('')
  const uploaderId = ref('')
  const loading = ref(false)
  const uploading = ref(false)

  const previewVisible = ref(false)
  const previewUrl = ref('')
  const previewName = ref('')

  function isImage(row: FileInfo) {
    return (row.fileType || '').startsWith('image/')
  }

  async function loadData() {
    loading.value = true
    try {
      const res = await listFiles({
        pageNum: pageNum.value,
        pageSize: pageSize.value,
        keyword: keyword.value || undefined,
        bizType: bizType.value || undefined,
        uploaderId: uploaderId.value || undefined,
      })
      list.value = rowsOf<FileInfo>(res)
      total.value = totalOf(res)
    } catch (e) {
      ElMessage.error((e as Error).message)
    } finally {
      loading.value = false
    }
  }

  function reload() {
    pageNum.value = 1
    loadData()
  }

  function onReset() {
    keyword.value = ''
    bizType.value = ''
    uploaderId.value = ''
    reload()
  }

  function handlePageChange(p: number) {
    pageNum.value = p
    loadData()
  }

  function handleSizeChange(s: number) {
    pageSize.value = s
    reload()
  }

  async function handleUpload(options: UploadRequestOptions) {
    uploading.value = true
    try {
      await uploadFile(options.file, { bizType: bizType.value || undefined })
      ElMessage.success(t('file.uploaded'))
      if (pageNum.value !== 1) pageNum.value = 1
      await loadData()
    } catch (e) {
      ElMessage.error((e as Error).message)
    } finally {
      uploading.value = false
    }
  }

  async function download(row: FileInfo) {
    try {
      const blob = await downloadFile(row.id)
      downloadByBlob(blob, row.fileName)
    } catch (e) {
      ElMessage.error((e as Error).message)
    }
  }

  function preview(row: FileInfo) {
    // /view/{id} 是免登直读地址，img 标签带不上 JWT，所以只能用这个地址而不是 download
    previewUrl.value = fileViewUrl(row.id)
    previewName.value = row.fileName
    previewVisible.value = true
  }

  async function remove(row: FileInfo) {
    try {
      await ElMessageBox.confirm(t('file.confirmDeleteRow', { name: row.fileName }), t('common.warning'), { type: 'warning' })
    } catch {
      return
    }
    try {
      await deleteFile(row.id)
      ElMessage.success(t('common.success'))
      await loadData()
    } catch (e) {
      ElMessage.error((e as Error).message)
    }
  }

  onMounted(loadData)
</script>

<style lang="scss" scoped>
  .view-note {
    margin-bottom: 12px;
  }
  .preview-img {
    width: 100%;
    display: block;
  }
</style>
