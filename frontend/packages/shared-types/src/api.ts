/**
 * 通用 API 响应结构
 */

/**
 * 统一响应体
 */
export interface R<T = unknown> {
  code: number
  message: string
  data: T
  timestamp?: number
}

/**
 * 分页参数
 */
export interface PageQuery {
  pageNum: number
  pageSize: number
  orderBy?: string
  order?: 'asc' | 'desc'
}

/**
 * 分页结果。
 * 后端存在两种口径：common-core PageResult 下发 records/totalPages，
 * 个别服务（如 screen-service）直接下发 list/pages，故两者都声明为可选，
 * 取值一律走 rowsOf()/totalOf()，不要在调用方直接读 list 或 records。
 */
export interface PageResult<T> {
  list?: T[]
  records?: T[]
  total: number
  pageNum?: number
  pageSize?: number
  pages?: number
  totalPages?: number
}

/** 从分页响应取行数据，兼容 records / list 两种口径 */
export function rowsOf<T>(page?: PageResult<T> | T[] | null): T[] {
  if (!page) return []
  if (Array.isArray(page)) return page
  return page.records ?? page.list ?? []
}

/** 从分页响应取总数 */
export function totalOf<T>(page?: PageResult<T> | T[] | null): number {
  if (!page) return 0
  if (Array.isArray(page)) return page.length
  return page.total ?? 0
}

/**
 * 空结果
 */
export interface VoidResult {
  success: boolean
}

/**
 * 下拉选项
 */
export interface OptionItem<K = string | number, L = string> {
  value: K
  label: L
  disabled?: boolean
  children?: OptionItem<K, L>[]
}

/**
 * 树形节点
 */
export interface TreeNode<T = unknown> {
  id: string | number
  parentId?: string | number
  label: string
  children?: TreeNode<T>[]
  data?: T
}

/**
 * 键值对
 */
export interface KeyValue<K = string, V = unknown> {
  key: K
  value: V
}
