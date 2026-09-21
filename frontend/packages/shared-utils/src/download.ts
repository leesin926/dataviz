/**
 * 文件下载工具
 */

/**
 * 通过 URL 下载文件
 */
export function downloadByUrl(url: string, filename?: string): void {
  const link = document.createElement('a')
  link.href = url
  link.download = filename || ''
  link.style.display = 'none'
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
}

/**
 * 通过 Blob 下载文件
 */
export function downloadByBlob(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob)
  downloadByUrl(url, filename)
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}

/**
 * 下载 JSON 文件
 */
export function downloadJson(data: unknown, filename: string): void {
  const json = JSON.stringify(data, null, 2)
  const blob = new Blob([json], { type: 'application/json' })
  downloadByBlob(blob, filename)
}

/**
 * 下载 CSV 文件 (支持中文)
 */
export function downloadCsv(rows: Array<Record<string, unknown>>, filename: string): void {
  if (rows.length === 0) return
  const headers = Object.keys(rows[0])
  const lines: string[] = [headers.join(',')]
  for (const row of rows) {
    const line = headers.map((h) => {
      const v = row[h]
      const s = v === null || v === undefined ? '' : String(v)
      // 处理包含逗号或引号的字段
      if (s.includes(',') || s.includes('"') || s.includes('\n')) {
        return `"${s.replace(/"/g, '""')}"`
      }
      return s
    })
    lines.push(line.join(','))
  }
  // 添加 BOM 以支持 Excel 识别 UTF-8
  const csv = '\ufeff' + lines.join('\n')
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' })
  downloadByBlob(blob, filename)
}

/**
 * 读取文件为文本
 */
export function readFileAsText(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(reader.result as string)
    reader.onerror = reject
    reader.readAsText(file)
  })
}

/**
 * 读取文件为 ArrayBuffer
 */
export function readFileAsArrayBuffer(file: File): Promise<ArrayBuffer> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(reader.result as ArrayBuffer)
    reader.onerror = reject
    reader.readAsArrayBuffer(file)
  })
}
