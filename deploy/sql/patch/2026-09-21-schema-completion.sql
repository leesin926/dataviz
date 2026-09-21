-- 会话字符集：docker-entrypoint / mysql CLI 默认按 latin1 协商会话，中文会在入库时直接变成 `?`（不可逆）。
-- 命令行执行时另需传 --default-character-set=utf8mb4（两者都指向 utf8mb4 才不会丢字符）。
SET NAMES utf8mb4;

-- =============================================
-- 各服务 schema 补全补丁 (schema-completion)
-- 背景：deploy/sql/init/03-all-services-schema.sql 建库建表后，多个 entity 引用的表/列从未创建，
--       对应接口报 SQL 错误（Table doesn't exist / Unknown column）。本补丁按 entity 逐表核对补齐。
-- 范围：仅 CREATE TABLE IF NOT EXISTS + ALTER TABLE ADD COLUMN（增量），不 DROP、不改既有列、不动数据。
-- 日期：2026-09-21
-- 执行：docker exec -i dataviz-mysql mysql --default-character-set=utf8mb4 -uroot -p < 本文件
-- ⚠ 可重复执行提示：MySQL 8.0 不支持 ADD COLUMN IF NOT EXISTS。CREATE TABLE 段可反复执行；
--   ALTER TABLE 段只能成功执行一次，重复执行会报 ERROR 1060 (Duplicate column name)，
--   此时按报错列名用文末「校验」段确认该列已存在后手工跳过该条 ALTER 即可（语句间相互独立，不会破坏数据）。
-- 约定：create_by/update_by 用 VARCHAR(64)——common-mybatis 的 MetaObjectHandler 以「用户名」填充，
--       entity BaseEntity.createBy 亦为 String；tenant_id 沿用既有表的 BIGINT（TenantEntity.tenantId 是
--       String，数字租户ID 可由 MySQL 隐式转换，非数字租户ID 需另行改造，见末尾 NOTE）。
-- =============================================

USE `db_datasource`;

-- =============================================
-- A. 数据源服务 (datasource-service)
--    Datasource(name/type/config/status/description) 仅缺 config 列
--    datasource_metadata 表已存在且无 entity 映射；代码中不存在 dataset_metadata 引用（仅 IDE 缓存），无需建表
-- =============================================

ALTER TABLE `datasource`
    ADD COLUMN `config` TEXT DEFAULT NULL COMMENT '连接配置JSON：host/port/database/username/password/maxPoolSize/minIdle' AFTER `type`;

USE `db_model`;

-- =============================================
-- B. 数据建模服务 (model-service)
--    ModelDataset / ModelDimension / ModelMetric 均 extends TenantEntity → 需要 deleted + create_time/update_time
-- =============================================

-- ModelDataset extends TenantEntity：name/datasourceId/tableName/sqlQuery/dimensions/metrics/filters + 继承字段
CREATE TABLE IF NOT EXISTS `model_dataset` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `tenant_id` BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID',
    `name` VARCHAR(128) NOT NULL COMMENT '数据集名称',
    `datasource_id` BIGINT DEFAULT NULL COMMENT '数据源ID',
    `table_name` VARCHAR(256) DEFAULT NULL COMMENT '物理表名',
    `sql_query` TEXT DEFAULT NULL COMMENT '自定义SQL查询语句',
    `dimensions` TEXT DEFAULT NULL COMMENT '维度配置JSON数组',
    `metrics` TEXT DEFAULT NULL COMMENT '指标配置JSON数组',
    `filters` TEXT DEFAULT NULL COMMENT '过滤条件JSON数组',
    `create_by` VARCHAR(64) DEFAULT NULL COMMENT '创建人(用户名)',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_by` VARCHAR(64) DEFAULT NULL COMMENT '更新人(用户名)',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除 0-未删除 1-已删除',
    PRIMARY KEY (`id`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_datasource_id` (`datasource_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模型数据集表(ModelDataset)';

-- ModelDimension 实体多出 display_name / datasource_id / table_name / description，且 extends TenantEntity
ALTER TABLE `model_dimension`
    ADD COLUMN `display_name` VARCHAR(128) DEFAULT NULL COMMENT '维度显示名称' AFTER `name`,
    ADD COLUMN `datasource_id` BIGINT DEFAULT NULL COMMENT '数据源ID' AFTER `column_name`,
    ADD COLUMN `table_name` VARCHAR(256) DEFAULT NULL COMMENT '所属表名' AFTER `datasource_id`,
    ADD COLUMN `description` VARCHAR(512) DEFAULT NULL COMMENT '维度描述',
    ADD COLUMN `create_by` VARCHAR(64) DEFAULT NULL COMMENT '创建人(用户名)',
    ADD COLUMN `update_by` VARCHAR(64) DEFAULT NULL COMMENT '更新人(用户名)',
    ADD COLUMN `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除 0-未删除 1-已删除';

-- ModelMetric 实体多出 display_name / aggregation_type / datasource_id / table_name，且 extends TenantEntity
ALTER TABLE `model_metric`
    ADD COLUMN `display_name` VARCHAR(128) DEFAULT NULL COMMENT '指标显示名称' AFTER `name`,
    ADD COLUMN `aggregation_type` VARCHAR(32) DEFAULT NULL COMMENT '聚合类型 SUM/AVG/COUNT/MAX/MIN/CUSTOM' AFTER `expression`,
    ADD COLUMN `datasource_id` BIGINT DEFAULT NULL COMMENT '数据源ID',
    ADD COLUMN `table_name` VARCHAR(256) DEFAULT NULL COMMENT '所属表名',
    ADD COLUMN `create_by` VARCHAR(64) DEFAULT NULL COMMENT '创建人(用户名)',
    ADD COLUMN `update_by` VARCHAR(64) DEFAULT NULL COMMENT '更新人(用户名)',
    ADD COLUMN `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除 0-未删除 1-已删除';

USE `db_etl`;

-- =============================================
-- C. ETL 服务 (etl-service)
--    EtlTaskLog 不继承 BaseEntity（自带 @TableId），故无 tenant_id/deleted
--    etl_task.status 与既有 tinyint 列冲突：本次不加，见末尾 NOTE-3
-- =============================================

CREATE TABLE IF NOT EXISTS `etl_task_log` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `task_id` BIGINT NOT NULL COMMENT 'ETL任务ID',
    `start_time` DATETIME DEFAULT NULL COMMENT '开始时间',
    `end_time` DATETIME DEFAULT NULL COMMENT '结束时间',
    `status` VARCHAR(16) DEFAULT NULL COMMENT '执行状态 RUNNING/SUCCESS/FAILED',
    `records_read` BIGINT DEFAULT NULL COMMENT '读取记录数',
    `records_written` BIGINT DEFAULT NULL COMMENT '写入记录数',
    `error_message` TEXT DEFAULT NULL COMMENT '错误信息',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_task_id` (`task_id`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='ETL任务执行日志表';

ALTER TABLE `etl_task`
    ADD COLUMN `source_datasource_id` BIGINT DEFAULT NULL COMMENT '源数据源ID' AFTER `description`,
    ADD COLUMN `target_datasource_id` BIGINT DEFAULT NULL COMMENT '目标数据源ID' AFTER `source_datasource_id`,
    ADD COLUMN `source_table` VARCHAR(256) DEFAULT NULL COMMENT '源表名',
    ADD COLUMN `target_table` VARCHAR(256) DEFAULT NULL COMMENT '目标表名',
    ADD COLUMN `transform_config` TEXT DEFAULT NULL COMMENT '转换配置JSON',
    ADD COLUMN `schedule_cron` VARCHAR(64) DEFAULT NULL COMMENT '调度Cron表达式';

USE `db_analysis`;

-- =============================================
-- D. 自助分析服务 (analysis-service) — analysis_report
-- =============================================

-- AnalysisReport extends TenantEntity：name/description/datasourceId/datasetId/config/isPublished + 继承字段
CREATE TABLE IF NOT EXISTS `analysis_report` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `tenant_id` BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID',
    `name` VARCHAR(128) NOT NULL COMMENT '报告名称',
    `description` VARCHAR(512) DEFAULT NULL COMMENT '报告描述',
    `datasource_id` BIGINT DEFAULT NULL COMMENT '数据源ID',
    `dataset_id` BIGINT DEFAULT NULL COMMENT '数据集ID',
    `config` TEXT DEFAULT NULL COMMENT '图表配置JSON',
    `is_published` TINYINT NOT NULL DEFAULT 0 COMMENT '是否已发布 0-未发布 1-已发布',
    `create_by` VARCHAR(64) DEFAULT NULL COMMENT '创建人(用户名)',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_by` VARCHAR(64) DEFAULT NULL COMMENT '更新人(用户名)',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除 0-未删除 1-已删除',
    PRIMARY KEY (`id`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_dataset_id` (`dataset_id`),
    KEY `idx_tenant_published` (`tenant_id`, `is_published`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分析报告表';

-- =============================================
-- E. 自助分析服务 (analysis-service) — query_history
--    QueryHistory 映射 datasource_id / sql / execution_time，既有表只有 dataset_id / sql_text / duration_ms
--    status 类型冲突（实体 String 'SUCCESS' vs 表 tinyint）：本次不加，见末尾 NOTE-4
-- =============================================

ALTER TABLE `query_history`
    ADD COLUMN `datasource_id` BIGINT DEFAULT NULL COMMENT '数据源ID(实体字段，历史数据为NULL)' AFTER `dataset_id`,
    ADD COLUMN `sql` TEXT DEFAULT NULL COMMENT '执行的SQL(实体映射列，与 sql_text 并存)' AFTER `sql_text`,
    ADD COLUMN `execution_time` BIGINT DEFAULT NULL COMMENT '执行耗时(ms，实体字段)' AFTER `duration_ms`;

USE `db_monitor`;

-- =============================================
-- F. 监控服务 (monitor-service)
--    monitor_alert / monitor_metric / service_instance / audit_log 均已存在且列齐全（含保留字 `condition`）
--    HealthController 无对应 entity：/api/monitor/health/{serviceName} 由 HealthCheckServiceImpl
--    实时聚合 service_instance + monitor_alert 得出，不落库；下表按 ServiceHealthVO 字段建，
--    供健康状态持久化使用（建表不改变现有查询逻辑），见末尾 NOTE-5
-- =============================================

CREATE TABLE IF NOT EXISTS `service_health` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `service_name` VARCHAR(128) NOT NULL COMMENT '服务名称',
    `instance_count` INT NOT NULL DEFAULT 0 COMMENT '实例总数',
    `healthy_count` INT NOT NULL DEFAULT 0 COMMENT '健康实例数(status=UP)',
    `overall_status` VARCHAR(16) NOT NULL DEFAULT 'UNKNOWN' COMMENT '整体状态 UP/DOWN/PARTIAL',
    `last_check_time` DATETIME DEFAULT NULL COMMENT '最后检查时间',
    `last_heartbeat` DATETIME DEFAULT NULL COMMENT '最近一次心跳时间',
    `active_alert_count` INT NOT NULL DEFAULT 0 COMMENT '活跃告警数(monitor_alert.status=ACTIVE)',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_service_name` (`service_name`),
    KEY `idx_overall_status` (`overall_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='服务健康状态表';

USE `db_schedule`;

-- =============================================
-- G1. 调度服务 (schedule-service) — 库存在但零表
--     entity 不继承 BaseEntity（无 deleted 字段），故不建 deleted 列
-- =============================================

CREATE TABLE IF NOT EXISTS `schedule_job` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `tenant_id` BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID',
    `job_name` VARCHAR(128) NOT NULL COMMENT '任务名称',
    `job_group` VARCHAR(64) DEFAULT 'DEFAULT' COMMENT '任务分组',
    `cron_expression` VARCHAR(64) DEFAULT NULL COMMENT 'Cron表达式',
    `job_class` VARCHAR(256) DEFAULT NULL COMMENT '任务执行类(全限定类名)',
    `job_params` TEXT DEFAULT NULL COMMENT '任务参数JSON',
    `status` VARCHAR(16) NOT NULL DEFAULT 'PAUSED' COMMENT '任务状态 RUNNING/PAUSED/ERROR',
    `description` VARCHAR(512) DEFAULT NULL COMMENT '任务描述',
    `misfire_policy` VARCHAR(32) DEFAULT 'IGNORE' COMMENT 'Misfire策略 IGNORE/FIRE_ONCE/EXECUTE_ALL',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_tenant_status` (`tenant_id`, `status`),
    KEY `idx_job_group` (`job_group`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='调度任务表';

CREATE TABLE IF NOT EXISTS `schedule_job_log` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `job_id` BIGINT NOT NULL COMMENT '调度任务ID(schedule_job.id)',
    `job_name` VARCHAR(128) DEFAULT NULL COMMENT '任务名称快照',
    `start_time` DATETIME DEFAULT NULL COMMENT '执行开始时间',
    `end_time` DATETIME DEFAULT NULL COMMENT '执行结束时间',
    `status` VARCHAR(16) DEFAULT NULL COMMENT '执行状态 SUCCESS/FAILED',
    `message` TEXT DEFAULT NULL COMMENT '执行消息/日志',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_job_id` (`job_id`),
    KEY `idx_create_time_status` (`create_time`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='调度任务执行日志表';

USE `db_openapi`;

-- =============================================
-- G2. 开放服务 (openapi-service) — 库存在但零表
--     ApiKeyController → OpenApiClient(openapi_client)；CallbackController 无状态不落库（无需 callback_config 表）
-- =============================================

CREATE TABLE IF NOT EXISTS `openapi_app` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `tenant_id` BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID',
    `app_name` VARCHAR(128) NOT NULL COMMENT '应用名称',
    `app_key` VARCHAR(64) NOT NULL COMMENT 'AppKey',
    `app_secret` VARCHAR(256) NOT NULL COMMENT 'AppSecret',
    `permissions` VARCHAR(1024) DEFAULT NULL COMMENT '授权范围，逗号分隔',
    `rate_limit` INT DEFAULT 60 COMMENT '每分钟请求上限',
    `ip_whitelist` VARCHAR(1024) DEFAULT NULL COMMENT 'IP白名单，逗号分隔，空表示不限制',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态 1-启用 0-禁用',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_app_key` (`app_key`),
    KEY `idx_tenant_status` (`tenant_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='开放平台应用表';

CREATE TABLE IF NOT EXISTS `openapi_client` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `tenant_id` BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID',
    `app_name` VARCHAR(128) DEFAULT NULL COMMENT '应用/客户端名称',
    `app_key` VARCHAR(64) NOT NULL COMMENT 'AppKey(即 API Key)',
    `app_secret` VARCHAR(256) NOT NULL COMMENT 'AppSecret',
    `status` VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态 ACTIVE/DISABLED',
    `rate_limit` INT DEFAULT 60 COMMENT '每分钟请求上限(Requests per minute)',
    `allowed_ips` VARCHAR(1024) DEFAULT NULL COMMENT '允许的IP，逗号分隔，空表示全部',
    `expire_time` DATETIME DEFAULT NULL COMMENT '过期时间，NULL 表示永不过期',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_client_app_key` (`app_key`),
    KEY `idx_tenant_status` (`tenant_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='开放平台客户端(API Key)表';

CREATE TABLE IF NOT EXISTS `openapi_log` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `client_id` BIGINT DEFAULT NULL COMMENT '客户端ID(openapi_client.id)',
    `app_name` VARCHAR(128) DEFAULT NULL COMMENT '应用名称',
    `api_path` VARCHAR(256) DEFAULT NULL COMMENT '被调用的接口路径',
    `method` VARCHAR(16) DEFAULT NULL COMMENT 'HTTP方法 GET/POST/PUT/DELETE',
    `request_params` TEXT DEFAULT NULL COMMENT '请求参数JSON',
    `response_code` INT DEFAULT NULL COMMENT 'HTTP响应码',
    `execution_time` BIGINT DEFAULT NULL COMMENT '耗时(ms)',
    `ip` VARCHAR(64) DEFAULT NULL COMMENT '调用方IP',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_client_time` (`client_id`, `create_time`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='开放平台调用日志表';

CREATE TABLE IF NOT EXISTS `openapi_webhook` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `tenant_id` BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID',
    `app_id` BIGINT DEFAULT NULL COMMENT '应用ID(openapi_app.id)',
    `event_type` VARCHAR(64) DEFAULT NULL COMMENT '事件类型',
    `url` VARCHAR(512) DEFAULT NULL COMMENT '回调URL',
    `secret` VARCHAR(256) DEFAULT NULL COMMENT '签名密钥',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态 1-启用 0-禁用',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_app_id` (`app_id`),
    KEY `idx_tenant_status` (`tenant_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='开放平台Webhook配置表';

USE `db_ai`;

-- =============================================
-- G3. AI 服务 (ai-service) — 库存在但零表
--     entity 不继承 BaseEntity → 无 deleted 列；Nl2SqlController / RecommendController 为占位实现，
--     不落库（故无需 nl2sql_query / ai_recommend 表），NL2SQL 生成的 SQL 存在 ai_message.sql_generated
-- =============================================

CREATE TABLE IF NOT EXISTS `ai_conversation` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `tenant_id` BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID',
    `user_id` BIGINT DEFAULT NULL COMMENT '用户ID',
    `title` VARCHAR(256) DEFAULT NULL COMMENT '会话标题',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_user_time` (`user_id`, `create_time`),
    KEY `idx_tenant_id` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI会话表';

CREATE TABLE IF NOT EXISTS `ai_message` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `conversation_id` BIGINT NOT NULL COMMENT '会话ID(ai_conversation.id)',
    `role` VARCHAR(16) NOT NULL COMMENT '消息角色 USER/ASSISTANT/SYSTEM',
    `content` LONGTEXT DEFAULT NULL COMMENT '消息内容',
    `sql_generated` TEXT DEFAULT NULL COMMENT 'NL2SQL生成的SQL(仅助手消息)',
    `token_count` INT DEFAULT NULL COMMENT 'Token消耗数',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_conversation_id` (`conversation_id`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI会话消息表';

CREATE TABLE IF NOT EXISTS `ai_insight` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `tenant_id` BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID',
    `datasource_id` BIGINT DEFAULT NULL COMMENT '数据源ID',
    `dataset_id` BIGINT DEFAULT NULL COMMENT '数据集ID',
    `insight_type` VARCHAR(32) DEFAULT NULL COMMENT '洞察类型 TREND/OUTLIER/CORRELATION/DISTRIBUTION',
    `content` TEXT DEFAULT NULL COMMENT '洞察内容',
    `config` TEXT DEFAULT NULL COMMENT '洞察生成配置JSON',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_tenant_type` (`tenant_id`, `insight_type`),
    KEY `idx_dataset_id` (`dataset_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI数据洞察表';

CREATE TABLE IF NOT EXISTS `few_shot_example` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `dataset_id` BIGINT DEFAULT NULL COMMENT '数据集ID',
    `question` VARCHAR(512) DEFAULT NULL COMMENT '自然语言问题',
    `sql` TEXT DEFAULT NULL COMMENT '问题对应的SQL(保留字，需反引号)',
    `category` VARCHAR(64) DEFAULT NULL COMMENT '示例分类',
    PRIMARY KEY (`id`),
    KEY `idx_dataset_id` (`dataset_id`),
    KEY `idx_category` (`category`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='NL2SQL少样本示例表';

-- =============================================
-- NOTE：类型冲突 / 无法用增量方式解决项（需 Java 或既有列改造，本补丁未做）
-- =============================================
-- NOTE-1 保留字：`sql`、`condition` 是 MySQL 8.0 保留字，建表/加列已用反引号，但 MyBatis-Plus 默认
--        不会给生成 SQL 的列名加反引号（本工程未配置 global-config.db-config.column-format），
--        故 QueryHistory.sql / FewShotExample.sql 仍会因 `INSERT INTO query_history (..., sql, ...)` 语法报错。
--        修法（Java 侧，二选一，本补丁不改代码）：字段上标注 @TableField("`sql`")，或开启 column-format 全局反引号。
-- NOTE-2 status 列不存在/冲突（本次未 ADD，也未改既有列）：
--        · db_analysis.query_history.status 为 TINYINT NOT NULL DEFAULT 1，实体 QueryHistory.status 为 String
--          （QueryHistoryServiceImpl 写入 "SUCCESS"）→ STRICT_TRANS_TABLES 下转换失败。既有表同时缺
--          dataset_id / query_json 的实体来源（这两列 NOT NULL 且实体无对应字段）→ 该表 INSERT 仍会失败，
--          需业务侧改为写 dataset_id/query_json 或对既有列放宽约束（非增量操作）。
--        · db_etl.etl_task.status 为 TINYINT NOT NULL DEFAULT 0，实体 EtlTask.status 为 String
--          （STOPPED/RUNNING/PAUSED/ERROR/COMPLETED）→ 冲突，未添加新列以免与既有 `status` 撞名。
--          建议改 entity 用 @TableField(exist=false) + 新增 status_str 列，或迁移既有列类型（均需人工决策）。
-- NOTE-3 db_etl.etl_task.dag_json 为 JSON NOT NULL 且 EtlTask 实体无 dagJson 字段 → 新建 ETL 任务时
--        INSERT 缺列失败；需为既有列补默认值或给实体补字段（均非本补丁范围）。
--        同理 db_datasource.datasource 的 host VARCHAR(256) NOT NULL / port INT NOT NULL 无默认值，
--        而 Datasource 实体只有 config JSON（host/port 在 config 内）→ 创建数据源时 INSERT 仍失败，
--        需放宽既有列约束（非增量）。etl_task_instance.trigger_type 为 TINYINT NOT NULL，
--        而 EtlTaskInstance.triggerType 是 String('manual'/'cron'/'api') → 同样冲突。
-- NOTE-4 db_model.model_dimension 的 dataset_id / dimension_type / data_type / column_name 中，
--        dataset_id 与 dimension_type 为 NOT NULL 但 ModelDimension 实体（及 DimensionCreateDTO）无对应字段；
--        model_metric.agg_function / data_type NOT NULL 而 ModelMetric 实体用 aggregation_type（新列）
--        → 新增维度/指标的 INSERT 仍需 Java 侧补字段或放宽既有 NOT NULL，本补丁只补齐实体已有字段的列。
-- NOTE-5 db_monitor.service_health 当前无 entity/Mapper 映射（HealthController 走内存聚合），
--        建表只为满足 schema 完整性与后续持久化，不影响现有接口。
-- NOTE-6 tenant_id：TenantEntity.tenantId / TenantLineInnerInterceptor 传的是 String 租户ID，
--        既有与本补丁的 tenant_id 统一保持 BIGINT（与 03 脚本一致），数字租户ID 可隐式转换；
--        若将来引入非数字租户编码，需要整体迁移为 VARCHAR。
-- NOTE-7 create_by/update_by 用 VARCHAR(64)（MetaObjectHandler 填用户名，BaseEntity 类型为 String）；
--        既有表 datasource / etl_task / dataset / dashboard / screen 的 create_by/update_by 是 BIGINT，
--        写入用户名时仍会失败——属既有列问题，本补丁不修改（不在增量范围内）。
-- NOTE-8 auth-service（H 项，核对结论，未建任何表）：db_auth 与 db_user 均已建齐 entity 对应的表
--        SysUser→sys_user、SysRole→sys_role、SysPermission→sys_permission、SysUserRole→sys_user_role、
--        SysRolePermission→sys_role_permission、SysLoginLog→sys_login_log —— 无“缺表”的实体。
--        反向差异：sys_dept、sys_oauth_client 有表无 entity；db_user 由 04-user-service-schema.sql 单独维护
--        （sys_user 列与 auth-service 的 SysUser 字段不完全一致，需按 user-service 的 entity 另议，不在本补丁范围）。
--        另：collab-service 的 collab_approval、screen-service 的 screen_component 也无表，超出本补丁清单范围。

-- =============================================
-- 校验：执行后逐条确认（应各自返回预期结果）
-- =============================================

-- 1) 新建的 14 张表都存在（期望 14 行）
SELECT TABLE_SCHEMA, TABLE_NAME
FROM information_schema.TABLES
WHERE (TABLE_SCHEMA, TABLE_NAME) IN (
    ('db_model', 'model_dataset'),
    ('db_analysis', 'analysis_report'),
    ('db_etl', 'etl_task_log'),
    ('db_monitor', 'service_health'),
    ('db_schedule', 'schedule_job'),
    ('db_schedule', 'schedule_job_log'),
    ('db_openapi', 'openapi_app'),
    ('db_openapi', 'openapi_client'),
    ('db_openapi', 'openapi_log'),
    ('db_openapi', 'openapi_webhook'),
    ('db_ai', 'ai_conversation'),
    ('db_ai', 'ai_message'),
    ('db_ai', 'ai_insight'),
    ('db_ai', 'few_shot_example')
)
ORDER BY TABLE_SCHEMA, TABLE_NAME;

-- 2) 新增列全部到位（期望 32 行；重复执行前也可用本查询判断哪些列已存在）
SELECT CONCAT(TABLE_SCHEMA, '.', TABLE_NAME, '.', COLUMN_NAME) AS added_column
FROM information_schema.COLUMNS
WHERE (TABLE_SCHEMA, TABLE_NAME, COLUMN_NAME) IN (
    ('db_datasource', 'datasource', 'config'),
    ('db_model', 'model_dataset', 'sql_query'), ('db_model', 'model_dataset', 'dimensions'),
    ('db_model', 'model_dataset', 'metrics'), ('db_model', 'model_dataset', 'filters'),
    ('db_model', 'model_dataset', 'deleted'),
    ('db_model', 'model_dimension', 'display_name'), ('db_model', 'model_dimension', 'datasource_id'),
    ('db_model', 'model_dimension', 'table_name'), ('db_model', 'model_dimension', 'description'),
    ('db_model', 'model_dimension', 'create_by'), ('db_model', 'model_dimension', 'update_by'),
    ('db_model', 'model_dimension', 'deleted'),
    ('db_model', 'model_metric', 'display_name'), ('db_model', 'model_metric', 'aggregation_type'),
    ('db_model', 'model_metric', 'datasource_id'), ('db_model', 'model_metric', 'table_name'),
    ('db_model', 'model_metric', 'deleted'),
    ('db_etl', 'etl_task', 'source_datasource_id'), ('db_etl', 'etl_task', 'target_datasource_id'),
    ('db_etl', 'etl_task', 'source_table'), ('db_etl', 'etl_task', 'target_table'),
    ('db_etl', 'etl_task', 'transform_config'), ('db_etl', 'etl_task', 'schedule_cron'),
    ('db_etl', 'etl_task_log', 'records_read'), ('db_etl', 'etl_task_log', 'records_written'),
    ('db_etl', 'etl_task_log', 'error_message'),
    ('db_analysis', 'query_history', 'datasource_id'), ('db_analysis', 'query_history', 'sql'),
    ('db_analysis', 'query_history', 'execution_time'),
    ('db_analysis', 'analysis_report', 'is_published'), ('db_analysis', 'analysis_report', 'config')
)
ORDER BY added_column;

-- 3) 冲突列保持原状（期望：query_history.status 与 etl_task.status 仍是 tinyint，未被本补丁改动）
SELECT TABLE_SCHEMA, TABLE_NAME, COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT
FROM information_schema.COLUMNS
WHERE (TABLE_SCHEMA, TABLE_NAME, COLUMN_NAME) IN (
    ('db_analysis', 'query_history', 'status'),
    ('db_etl', 'etl_task', 'status'),
    ('db_etl', 'etl_task', 'dag_json'),
    ('db_datasource', 'datasource', 'host'),
    ('db_model', 'model_dimension', 'dimension_type'),
    ('db_model', 'model_metric', 'agg_function')
);

-- 4) 各库表数量总览（期望 db_schedule=2, db_openapi=4, db_ai=4）
SELECT TABLE_SCHEMA, COUNT(*) AS table_count
FROM information_schema.TABLES
WHERE TABLE_SCHEMA IN ('db_datasource', 'db_model', 'db_etl', 'db_analysis', 'db_monitor',
                       'db_schedule', 'db_openapi', 'db_ai')
GROUP BY TABLE_SCHEMA
ORDER BY TABLE_SCHEMA;

-- 5) 保留字列已按反引号存储且可正常读写（期望返回 0 行，无报错即语法正确）
SELECT `id`, `sql`
FROM `db_analysis`.`query_history`
WHERE `sql` IS NULL
LIMIT 1;

SELECT `id`, `sql`, `category`
FROM `db_ai`.`few_shot_example`
LIMIT 1;
