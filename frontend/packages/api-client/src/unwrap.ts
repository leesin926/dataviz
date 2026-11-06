import type { PageResult, R } from '@dataviz/shared-types'
import { rowsOf } from '@dataviz/shared-types'
/**
 * 后端各服务对统一响应信封 R 的使用并不一致：
 * - 多数服务返回 R<T>
 * - dashboard-service 直接返回裸对象/裸 Page
 * - 部分服务分页字段口径不同（common-core 用 records/totalPages，screen-service 用 list/pages，
 *   MyBatis-Plus Page 用 records/current/size）
 * 这里统一收口，调用方只依赖 PageResult 的 rowsOf()/totalOf()。
 */

interface LoosePage<T> {
  list?: T[]
  records?: T[]
  total?: number
  pageNum?: number
  pageSize?: number
  pages?: number
  totalPages?: number
  current?: number
  size?: number
}

/** 取出业务数据：兼容 R 信封与裸响应 */
export function unwrap<T>(payload: unknown): T {
  const p = payload as (Partial<R<T>> & Record<string, unknown>) | null | undefined
  if (p && typeof p === 'object' && typeof p.code === 'number' && 'data' in p) {
    // 拆包层是信封成败的权威判据（API-151）：code ∉ {0, 200} 时即使带 data 也当业务错误抛出，
    // 否则后端把空壳 data（如 SQL 报错时 data:{rows:[]}）连同非 0 code 一起返回，就会被静默当成成功。
    //
    // 与 request.ts 响应拦截器的分工：拦截器只对"经 axios 发出"的请求生效，命中非 0 code 会先 reject；
    // 这里保留对 code 的判定，是因为 unwrap 还被直接调用在缓存/内部拼装等旁路响应上，那些没经过拦截器。
    // 两层不是重复造轮子——拦截器管网络出口，unwrap 管"任何信封的入口"，各拦一段才能杜绝半个检查。
    if (p.code !== 0 && p.code !== 200) {
      const message = typeof p.message === 'string' && p.message ? p.message : '请求失败'
      throw new Error(message)
    }
    return p.data as T
  }
  return payload as T
}

/** 归一化分页响应（records / list / MyBatis-Plus Page 皆可） */
export function asPage<T>(payload: unknown): PageResult<T> {
  const raw = unwrap<LoosePage<T> | T[]>(payload)
  if (raw == null) return { records: [], total: 0 }
  if (Array.isArray(raw)) {
    return { records: raw, total: raw.length, pageNum: 1, pageSize: raw.length, pages: 1 }
  }
  const rows = raw.records ?? raw.list ?? []
  return {
    records: rows,
    total: raw.total ?? rows.length,
    pageNum: raw.pageNum ?? raw.current ?? 1,
    pageSize: raw.pageSize ?? raw.size ?? rows.length,
    pages: raw.pages ?? raw.totalPages,
  }
}

/**
 * 分页换形：只替换行数据，保留分页元信息。
 * 不能写 `{ ...page, records: page.records.map(fn) }`——展开会把旧元素类型的 list 一起带进来，
 * 且 records 在 PageResult 上是可选属性（TS 报 possibly undefined）。
 */
export function mapPage<From, To>(page: PageResult<From>, fn: (row: From) => To): PageResult<To> {
  return {
    records: rowsOf(page).map(fn),
    total: page.total,
    pageNum: page.pageNum,
    pageSize: page.pageSize,
    pages: page.pages,
  }
}
