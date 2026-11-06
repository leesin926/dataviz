import type {
  OpenApiApp,
  OpenApiAppPayload,
  OpenApiClient,
  OpenApiKeyPayload,
  OpenApiKeyResult,
  OpenApiLog,
  OpenApiLogPageQuery,
  OpenApiWebhook,
  OpenApiWebhookPayload,
  PageResult,
  R,
} from '@dataviz/shared-types'
import { request } from '../request'
import { unwrap } from '../unwrap'

/**
 * 开放平台（后端 openapi-service）。
 *
 * 这个模块里 **两种响应形状并存**，写的时候很容易踩：
 * - `/openapi/key/*`（API 客户端）走统一信封 R，取 `.data`；
 * - `/openapi/app/*` 与 `/openapi/webhook/*` 直接返回裸对象/裸数组/裸 id，没有 code 字段。
 * `unwrap()` 对两者都成立，所以这里统一用它，但注释保留形状差异，别在页面上假设能拿到 message。
 *
 * 三条不给前端递入口的东西：
 * 1. `GET /openapi/app/list?tenantId=` 允许按入参翻别租户的应用 —— 属 API-40 那族跨主体读，
 *    这里**不暴露 tenantId 参数**，列表只取会话租户（open_api_* 表带 tenant_id，由拦截器自动收口）。
 * 2. 客户端只有 `disable`，**没有重新启用的端点**；界面因此只能提供"停用"，别画成开关。
 * 3. `OpenApiController`（`/openapi/proxy/invoke`）与 `CallbackController` 的主体都是外部系统：
 *    前者用请求体里的 appKey/appSecret 自证、后者用 X-Signature 验签，两边手上都没有用户会话，
 *    套 `openapi:*` 等于要求外部先有平台账号 —— 所以不给前端封装。
 *    代理的审计（openapi_log）另有一条登录态读端点，见下面的 `pageOpenApiLogs`：
 *    "开放接口调用日志"自此有真实数据源；只有分页读端点，**没有详情端点**
 *    （详情能多给的只有 request_params，那是代理原样转发的参数，可能带 SQL/凭据内容）。
 */

// ───────────────────────── API 客户端（R 信封，status 为字符串） ─────────────────────────

/** 客户端列表。返回体不含 appSecret —— 它只在铸造/重置那一次出现 */
export async function listClients(): Promise<OpenApiClient[]> {
  const res = await request.get('/openapi/key/clients')
  return unwrap<OpenApiClient[]>(res.data) ?? []
}

/**
 * 铸造一对密钥。
 * `appSecret` 只在这一次响应里出现，之后任何接口都拿不回来，所以界面必须当场展示并让用户复制。
 */
export async function createClient(payload: OpenApiKeyPayload): Promise<OpenApiKeyResult> {
  const res = await request.post('/openapi/key/generate', payload)
  return unwrap<OpenApiKeyResult>(res.data)
}

/** 重置密钥：旧 secret 当场失效，同样只回一次 */
export async function resetClientSecret(clientId: string | number): Promise<OpenApiKeyResult> {
  const res = await request.post(`/openapi/key/${clientId}/reset-secret`)
  return unwrap<OpenApiKeyResult>(res.data)
}

/** 停用客户端（单向，无启用端点） */
export async function disableClient(clientId: string | number): Promise<void> {
  await request.post(`/openapi/key/${clientId}/disable`)
}

// ───────────────────────── 开放应用（裸返回，status 为数字） ─────────────────────────

/** 当前租户下的应用列表（不接受 tenantId 入参，见文件头第 1 条） */
export async function listApps(): Promise<OpenApiApp[]> {
  const res = await request.get('/openapi/app/list')
  return unwrap<OpenApiApp[]>(res.data) ?? []
}

export async function getApp(id: string | number): Promise<OpenApiApp> {
  const res = await request.get(`/openapi/app/${id}`)
  return unwrap<OpenApiApp>(res.data)
}

/** 新建应用，返回自增 id；appKey/appSecret 由后端生成 */
export async function createApp(payload: OpenApiAppPayload): Promise<string | number> {
  const res = await request.post('/openapi/app', payload)
  return unwrap<string | number>(res.data)
}

export async function deleteApp(id: string | number): Promise<void> {
  await request.delete(`/openapi/app/${id}`)
}

/**
 * 重造应用密钥。
 * 注意应用面与客户端面不同：`AppVO` 本身带 `appSecret`，
 * 详情/列表都会回显它（后端现有行为，不是"只回一次"的密钥），界面默认打码。
 */
export async function regenerateAppSecret(id: string | number): Promise<OpenApiApp> {
  const res = await request.post(`/openapi/app/${id}/regenerate-secret`)
  return unwrap<OpenApiApp>(res.data)
}

// ───────────────────────── 回调地址（裸返回） ─────────────────────────

export async function listWebhooks(appId: string | number): Promise<OpenApiWebhook[]> {
  const res = await request.get(`/openapi/webhook/list/${appId}`)
  return unwrap<OpenApiWebhook[]>(res.data) ?? []
}

export async function getWebhook(id: string | number): Promise<OpenApiWebhook> {
  const res = await request.get(`/openapi/webhook/${id}`)
  return unwrap<OpenApiWebhook>(res.data)
}

export async function createWebhook(payload: OpenApiWebhookPayload): Promise<string | number> {
  const res = await request.post('/openapi/webhook', payload)
  return unwrap<string | number>(res.data)
}

/** 修改回调：payload 必须带 id，否则后端 400「修改回调地址必须带 id」 */
export async function updateWebhook(payload: OpenApiWebhookPayload & { id: string | number }): Promise<void> {
  await request.put('/openapi/webhook', payload)
}

export async function deleteWebhook(id: string | number): Promise<void> {
  await request.delete(`/openapi/webhook/${id}`)
}

// ───────────────────────── 调用日志（R 信封 + common-core PageResult） ─────────────────────────

/**
 * 「开放接口调用日志」分页。对应后端 `OpenApiLogController#page`（`openapi:read`）。
 *
 * 形状是 R 包裹的 `PageResult`（`records` / `total` / `pageNum` / `pageSize` / `totalPages`），
 * 与本文件的 `/app/*`、`/webhook/*` 裸返回不同 —— 走信封时 axios 拦截器已经把
 * `code ∉ {0,200}` 变成 `Error(message)`，所以这里**不要**再自己造第二个错误形状：
 * 后端把条件打错（客户端不存在/不在当前租户、result 非法、时间区间倒置）时报的是 400/404，
 * 消息点名是哪个条件；而「真的没有调用」是 `total = 0` 的正常响应，两者在界面上必须是两件事。
 *
 * 行里没有 appSecret，也没有 request_params（后端不投影，详情端点也不存在）——
 * 失败原因表里没有列，页面只能给状态码与分类。
 */
export async function pageOpenApiLogs(query: OpenApiLogPageQuery): Promise<PageResult<OpenApiLog>> {
  const res = await request.get<R<PageResult<OpenApiLog>>>('/openapi/log/page', { params: query })
  return unwrap<PageResult<OpenApiLog>>(res.data) ?? { records: [], total: 0 }
}
