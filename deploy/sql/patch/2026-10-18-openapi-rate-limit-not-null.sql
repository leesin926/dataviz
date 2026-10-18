-- =============================================================================
-- 【草案，未执行】openapi_client.rate_limit 收口为 NOT NULL DEFAULT 60
-- 任务：AX5（2026-10-18）　决策口径：D30（数据变更执行前必须先读现状）/ D72（"待执行"是一种状态，不是事实）
-- 执行者：等用户点头。本轮助手只落盘、不跑。
-- =============================================================================
--
-- ## 一、为什么只有这一条
-- 阶段 AX 给 openapi-service 补单元测试时，`ApiAuthServiceImplTest` 打出一个真缺陷：
--   库里 `rate_limit` 可空 ⇒ `int limit = client.getRateLimit()` 自动拆箱直接 NPE，
--   鉴权这条路 500，症状是"某个老客户端突然全部 502/500"，而不是"配额超了"。
-- 代码侧已经修（NULL 或非正数一律按 `DEFAULT_RATE_LIMIT = 60`），本脚本修的是**库侧同一口径**：
-- 让"可空"这个状态本身不再存在，否则每个新读点都得重复那一句判空。
--
-- ## 二、执行前读到的现状（2026-10-18 实测，读数来自 information_schema + COUNT(*)，不用 TABLE_ROWS 估算）
--   COLUMN  : `db_openapi.openapi_client`.`rate_limit`  int  IS_NULLABLE=YES  COLUMN_DEFAULT=60
--   行数    : `total_clients` = 2，`rate_limit IS NULL` = 0，`rate_limit <= 0` = 0
--   ⇒ 缺陷是**潜伏**的，不是正在流血：现有两行都有值。所以这份补丁不急，
--     但"默认值只对新行生效"这条性质不会因为今天没脏行就消失。
--   索引    : `uk_client_app_key`(app_key 唯一) / `idx_tenant_status`(tenant_id,status) 均已在位
--   ⇒ 本轮同时核对了 collab 三张表与 monitor 三张表的索引，`service_instance(status,last_heartbeat)`、
--     `monitor_metric(service_name,metric_name)`、`collab_comment(target_type,target_id)` 都已覆盖新加的查询，
--     **没有需要补的索引**。写补丁前先证明"缺"，不然就是凭印象加 DDL。
--
-- ## 三、文档与库的分叉（本脚本选哪一边）
-- `docs/09-后端服务开发文档.md` 第 2474 行写的是 `rate_limit INT NOT NULL DEFAULT 1000 COMMENT '每分钟限制'`，
-- 列名还写成了 `ip_whitelist`（库内实际是 `allowed_ips`）。**这里以库为准**：
--   · 默认值取 **60**（= 库里既有 DEFAULT，= 代码里的 DEFAULT_RATE_LIMIT，= 过滤器缺省），
--     不把库改成 1000 —— 那等于悄悄把每个客户端的配额放大 16 倍，是业务决定不是 DDL 决定。
--   · 文档那两行（1000 / ip_whitelist）由台账轮次就地订正，不在本脚本里动。
--
-- =============================================================================
-- 0. 字符集：建会话先钉死，否则中文注释在 Windows 客户端下会变 `?`（不可逆）
SET NAMES utf8mb4;

-- 1. 前置核对：期望 nullable = 'YES'。若已是 NO，说明这份跑过了，直接停在这里
SELECT TABLE_SCHEMA, COLUMN_NAME, IS_NULLABLE, COLUMN_DEFAULT, COLUMN_TYPE
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = 'db_openapi'
  AND TABLE_NAME = 'openapi_client'
  AND COLUMN_NAME = 'rate_limit';

-- 2. 兜底回填：当前读数是 0 行，保留这一步是因为"读现状"与"执行"之间可能有新行落进来
--    口径与代码一致：NULL 与非正数都归 60，不发明新值
UPDATE `db_openapi`.`openapi_client`
SET `rate_limit` = 60
WHERE `rate_limit` IS NULL OR `rate_limit` <= 0;

-- 3. 收口列定义：NOT NULL + DEFAULT 60，注释说清"每分钟"和"缺失时按 60"
ALTER TABLE `db_openapi`.`openapi_client`
    MODIFY COLUMN `rate_limit` INT NOT NULL DEFAULT 60
    COMMENT '每分钟请求上限；NULL 已禁止，缺失/非正数由服务端按 60 处理';

-- 4. 校验（三条都要对上期望值再离开）
--   4.1 列属性：IS_NULLABLE=NO / COLUMN_DEFAULT=60
SELECT COLUMN_NAME, IS_NULLABLE, COLUMN_DEFAULT, COLUMN_COMMENT
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = 'db_openapi' AND TABLE_NAME = 'openapi_client' AND COLUMN_NAME = 'rate_limit';

--   4.2 脏行必须为 0
SELECT COUNT(*) AS bad_rows
FROM `db_openapi`.`openapi_client`
WHERE `rate_limit` IS NULL OR `rate_limit` <= 0;

--   4.3 全表配额视图（执行前后各跑一次，比对每行的值有没有被动到；期望：**与执行前完全一致**）
SELECT `id`, `app_key`, `rate_limit`, `status`, `allowed_ips`
FROM `db_openapi`.`openapi_client`
ORDER BY `id`;

-- 5. 回滚（只放开约束，不改任何数据值）
-- ALTER TABLE `db_openapi`.`openapi_client`
--     MODIFY COLUMN `rate_limit` INT NULL DEFAULT 60
--     COMMENT '每分钟限制';

-- =============================================================================
-- 6. 刻意**不在**这份脚本里的东西（不是遗漏）
--   · `role-grants-draft` / `api30-wildcard` 那两条等拍板的授权数据 —— "谁能干什么"不由助手代拍。
--   · `allowed_ips` 的通配语义（代码现在是精确匹配 + 单个 `*` 放行；改成 CIDR 是功能变更，不是 DDL）。
--   · `app_secret` 的比较方式（明文 + 非恒定时间 `equals`，已登记为缺陷 API-侧待拍板）：
--     要动的是代码与凭据存储方案，改列长度/加哈希属于另一轮设计，不塞进这条补丁混做。
-- 7. 执行方式（沿用既有口令不外泄口径：口令只在容器内展开，全程不读不打印）
--   docker exec -i dataviz-mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot --default-character-set=utf8mb4 -t' \
--     < deploy/sql/patch/2026-10-18-openapi-rate-limit-not-null.sql
--   建议先在第 2、3 步外套 START TRANSACTION / ROLLBACK 做一次 dry-run（DDL 会隐式提交，
--   所以 dry-run 只能验第 2 步与校验查询，第 3 步要真验就得接受它已提交）。
-- =============================================================================
