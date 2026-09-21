import type { R } from '@dataviz/shared-types'
import { BASE_URL, request } from '../request'

/** 与后端 FileUploadResultVO 对齐 */
export interface FileUploadResult {
  id: number
  originalName: string
  storedName: string
  /** 后端给出免登直读地址：image/* 为 /api/file/view/{id}，其余为 /api/file/download/{id} */
  fileUrl: string
  fileSize: number
  contentType: string
  storageType: string
  createTime: string
}

/** 上传文件（multipart，字段名 file） */
export async function uploadFile(
  file: File,
  options?: { bizType?: string; bizId?: string | number; bucketName?: string },
  onProgress?: (percent: number) => void,
): Promise<FileUploadResult> {
  const form = new FormData()
  form.append('file', file)
  if (options?.bizType) form.append('bizType', options.bizType)
  if (options?.bizId !== undefined && options?.bizId !== null) form.append('bizId', String(options.bizId))
  if (options?.bucketName) form.append('bucketName', options.bucketName)
  const res = await request.post<R<FileUploadResult>>('/file/upload', form, {
    headers: { 'Content-Type': 'multipart/form-data' },
    onUploadProgress: (e) => {
      if (onProgress && e.total) {
        onProgress(Math.round((e.loaded / e.total) * 100))
      }
    },
  })
  return res.data.data
}

/** 批量上传（后端路径为 /uploadBatch） */
export async function uploadFiles(files: File[]): Promise<FileUploadResult[]> {
  const form = new FormData()
  files.forEach((f) => form.append('files', f))
  const res = await request.post<R<FileUploadResult[]>>('/file/uploadBatch', form, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
  return res.data.data
}

/** 下载文件（附件语义，需鉴权） */
export async function downloadFile(fileId: string | number): Promise<Blob> {
  const res = await request.get(`/file/download/${fileId}`, { responseType: 'blob' })
  return res.data as Blob
}

/** 文件信息 */
export async function getFileinfo(fileId: string | number): Promise<Record<string, unknown>> {
  const res = await request.get<R<Record<string, unknown>>>(`/file/info/${fileId}`)
  return res.data.data
}

/** 删除文件（同时删除服务端物理文件） */
export async function deleteFile(fileId: string | number): Promise<void> {
  await request.delete(`/file/${fileId}`)
}

/** 从上传返回的图片/文件地址反解记录 ID，非本系统地址返回空串 */
export function parseFileId(url: string | undefined | null): string {
  if (!url) return ''
  const m = /\/file\/(?:view|download)\/(\d+)/.exec(url)
  return m ? m[1] : ''
}

/** 图片直读地址（免登，供 img / CSS 背景使用） */
export function fileViewUrl(fileId: string | number): string {
  return `${BASE_URL}/file/view/${fileId}`
}
