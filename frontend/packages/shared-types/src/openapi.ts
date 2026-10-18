/**
 * 开放平台域类型（后端 openapi-service）
 *
 * 这个域有**两套并行的凭据面**，字段名和取值口径都不一样，前端必须分开建模：
 * - `/openapi/key/*`：API 客户端。状态是字符串 `ACTIVE`/`DISABLED`，IP 白名单**入参是数组、
 *   回显是逗号分隔字符串**，且有 `expireTime`；关闭客户端只有 `disable`，没有重新启用。
 * - `/openapi/app/*`：开放应用。状态是数字（1 起、没有改状态的端点，只有删除与重造密钥），
 *   IP 白名单**从头到尾是一个字符串**（列名 `ipWhitelist`，不是 `allowedIps`）。
 *
 * 另外三条口径：
 * 1. `appSecret` 只在创建/重置那一次由 `/key/generate`、`/key/{id}/reset-secret` 返回；
 *    客户端列表 (`ApiClientVO`) 里根本没有这个字段。但 **`/app/*` 的 `AppVO` 是带密钥回显的**
 *    ——列表和详情都会吐 `appSecret`，这是后端现有行为，不是前端能假装修好了的。
 * 2. `AppController`、`WebhookController` 返回**裸数据**（没有 R 信封、没有 code 字段），
 *   `ApiKeyController` 才是 R 包裹的。api-client 里两种取法不同。
 * 3. `OpenApiController` 的代理转发仍是未实现桩，`CallbackController` 是机器回调入口，
 *    两者都不给前端封装 —— 页面上"调用日志"因此没有数据来源，不能画成有数据的样子。
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
