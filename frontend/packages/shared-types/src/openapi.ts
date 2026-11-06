/**
 * 开放平台域类型（后端 openapi-service）
 *
 * 这个域有**两套并行的凭据面**，字段名和取值口径都不一样，前端必须分开建模：
 * - `/openapi/key/*`：API 客户端。状态是字符串 `ACTIVE`/`DISABLED`，IP 白名单**入参是数组、
 *   回显是逗号分隔字符串**，且有 `expireTime`；关闭客户端只有 `disable`，没有重新启用。
 * - `/openapi/app/*`：开放应用。状态是数字（1 起、没有改状态的端点，只有删除与重造密钥），
 *   IP 白名单**从头到尾是一个字符串**（列名 `ipWhitelist`，不是 `allowedIps`）。
 *
 * 另外四条口径：
 * 1. `appSecret` 只在创建/重置那一次由 `/key/generate`、`/key/{id}/reset-secret` 返回；
 *    客户端列表 (`ApiClientVO`) 里根本没有这个字段。但 **`/app/*` 的 `AppVO` 是带密钥回显的**
 *    ——列表和详情都会吐 `appSecret`，这是后端现有行为，不是前端能假装修好了的。
 * 2. `AppController`、`WebhookController` 返回**裸数据**（没有 R 信封、没有 code 字段），
 *   `ApiKeyController` 与 `OpenApiLogController` 才是 R 包裹的。api-client 里两种取法不同。
 * 3. `OpenApiController` 的代理转发（`/openapi/proxy/invoke`）是**对外**入口：调用方是外部系统，
 *    手上没有用户会话，所以不给前端封装（套 `openapi:*` 等于要求外部先有平台账号）。
 *    它的审计落库现在可读 —— 见下面的 `OpenApiLog` / `pageOpenApiLogs`。
 * 4. `CallbackController` 是机器回调入口（验签那一条路），同样不给前端封装。
 */

/** 客户端凭据状态；注意只有关闭动作，没有对应的启用端点 */
export type OpenApiClientStatus = 'ACTIVE' | 'DISABLED'

/** 数字状态：1 = 新建时的默认值，0 目前只能由直接改库产生 */
export type OpenApiAppStatus = number

export interface OpenApiClient {
  id: string | number
  appName: string
  appKey: string
  status: OpenApiClientStatus | string
  /** 每分钟请求数上限；创建时不传按 60 落库 */
  rateLimit?: number | null
  /** 逗号分隔字符串（写入时给数组，见 OpenApiKeyPayload） */
  allowedIps?: string | null
  expireTime?: string | null
  tenantId?: string | number
  createTime?: string
  updateTime?: string
}

/** 铸造/重置密钥的返回。`appSecret` 只在这一份响应里出现一次 */
export interface OpenApiKeyResult {
  clientId: string | number
  appName: string
  appKey: string
  appSecret: string
}

export interface OpenApiApp {
  id: string | number
  tenantId?: string | number
  appName: string
  appKey: string
  /** 见文件头第 1 条：应用面会回显密钥 */
  appSecret?: string
  /** 逗号分隔的权限码串，后端不做校验 */
  permissions?: string | null
  rateLimit?: number | null
  ipWhitelist?: string | null
  status?: OpenApiAppStatus
  createTime?: string
}

export interface OpenApiWebhook {
  id: string | number
  tenantId?: string | number
  appId: string | number
  eventType: string
  url: string
  /** 用于回调签名，创建时由调用方自己填 */
  secret?: string | null
  status?: OpenApiAppStatus
}

/** 创建 API 客户端：`allowedIps` 这里必须是数组 */
export interface OpenApiKeyPayload {
  appName: string
  rateLimit?: number
  allowedIps?: string[]
  expireTime?: string
}

export interface OpenApiAppPayload {
  appName: string
  permissions?: string
  rateLimit?: number
  /** 这里是字符串，不是数组 */
  ipWhitelist?: string
}

export interface OpenApiWebhookPayload {
  /** 修改时必须带：后端 `PUT` 靠它定位要改哪一行 */
  id?: string | number
  appId: string | number
  eventType: string
  url: string
  secret?: string
}

// ───────────────────────── 调用日志（openapi_log，读端点 GET /openapi/log/page） ─────────────────────────

/**
 * 结果分类，取值与后端 `ApiLogServiceImpl` 的常量一一对应（大小写不敏感，后端会 toUpperCase）。
 * 分类按 `response_code` 的区间算：表里没有「成功标志」列，也没有失败原因列。
 * - `SUCCESS` = 2xx；`FAIL` = 非 2xx（含空值）
 * - `CLIENT_ERROR` = 4xx；`SERVER_ERROR` = 5xx；`OTHER` = 剩下的（3xx / 1xx / 异常值 / 空值）
 */
export type OpenApiLogResult = 'SUCCESS' | 'FAIL' | 'CLIENT_ERROR' | 'SERVER_ERROR' | 'OTHER'

/**
 * 一行调用日志 = `openapi_log` 里**可以安全展示**的列，字段名与 `OpenApiLogVO` 逐一对应。
 *
 * 两条不是遗漏的缺口：
 * - 没有 `appSecret`，也没有 `appKey`：日志只冗余记调用方名称（`appName`），要按 key 查用查询条件；
 * - 没有 `requestParams`：代理把 params 原样序列化转发，datasource 的内部查询里那就是 SQL，
 *   所以后端既不投影这一列、也没有详情端点。失败原因（message）同理**不在表里**，
 *   界面只能显示状态码与它的分类。
 *
 * 每个字段都可能为 null：DDL 里除 id 外全部 `DEFAULT NULL`，后端 Jackson 也没配 non-null 省略，
 * 所以既可能是 `null` 也可能整个键缺失 —— 一律 `?: X | null`。
 */
export interface OpenApiLog {
  id: string | number
  /** 调用方客户端 id（`openapi_client.id`，不是 `openapi_app.id`）；历史手工写入的行为 null */
  clientId?: string | number | null
  /** 写入时的名称快照：客户端改名后老行仍是旧名 */
  appName?: string | null
  /** 形如 `/api/<服务代码>/...` */
  apiPath?: string | null
  /** 代理只放行 GET/POST */
  method?: string | null
  /** 上游/代理状态码，2xx 才算成功 */
  responseCode?: number | null
  /** 本次调用耗时（毫秒） */
  executionTime?: number | null
  /** 调用方地址：默认取 TCP 对端，只有配了可信跳数才从 X-Forwarded-For 从右往左数 */
  ip?: string | null
  createTime?: string | null
}

/**
 * `GET /openapi/log/page` 的查询条件。每个条件背后都必须有真实列，因此：
 * - 有 `clientId` / `appKey`（经父表 `openapi_client` 定位）、`startTime` / `endTime`（`create_time`）、
 *   `result` / `responseCode`（`response_code`）、`apiPath`（`api_path` 包含匹配）、
 *   `serviceCode`（**没有这一列**，按 `api_path` 的 `/api/<代码>/` 前缀匹配）；
 * - 没有 `tenantId`（表里没有 tenant_id 列，租户由后端按可见客户端收口，前端不传也不该传）、
 *   没有 `appId`（日志记的是客户端 id，不是开放应用 id，同号会查出错的行）。
 */
export interface OpenApiLogPageQuery {
  pageNum?: number
  pageSize?: number
  clientId?: string | number
  appKey?: string
  /** ISO 本地日期时间，如 `2026-11-06T00:00:00`（与 monitor 的历史查询同口径，不带时区） */
  startTime?: string
  endTime?: string
  result?: OpenApiLogResult
  responseCode?: number
  apiPath?: string
  serviceCode?: string
}
