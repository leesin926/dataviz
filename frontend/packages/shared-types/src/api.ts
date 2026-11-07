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
  /**
   * 分片读路径专用：这一页与 total 都不完整（扇出被服务端 shard.max-shards-per-read 截断）。
   * undefined = 这个接口不分片。别把 undefined 当 false 用 —— 画面上"没说"与"完整"是两回事。
   */
  truncated?: boolean
  /** 本次实际扫描的物理分片数；配合 truncated 才能告诉用户"少了多少范围" */
  shards?: number
  /** 调用方没给时间范围、服务端按默认窗口收窄了查询。为真时页面要把"这不是全部历史"说出来。 */
  windowDefaulted?: boolean
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

/** 结果是否被明确宣告为不完整（undefined 一律按"没这个概念"处理，不当 true 也不当 false 用） */
export function isTruncated(page?: PageResult<unknown> | null): boolean {
  return page?.truncated === true
}

/** 服务端是否补了时间窗 */
export function isWindowDefaulted(page?: PageResult<unknown> | null): boolean {
  return page?.windowDefaulted === true
}

/** 扫了多少个分片；没报就是 0（0 与"未分片"在画面上都不该显示成数字） */
export function shardsOf(page?: PageResult<unknown> | null): number {
  return page?.shards ?? 0
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
