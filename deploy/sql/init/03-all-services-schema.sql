-- 会话字符集：docker-entrypoint / mysql CLI 默认按 latin1 协商会话，中文会在入库时直接变成 `?`（不可逆）。
-- 命令行执行时另需传 --default-character-set=utf8mb4（两者都指向 utf8mb4 才不会丢字符）。
SET NAMES utf8mb4;

USE `db_datasource`;

-- =============================================
-- 数据源服务 (datasource-service) 数据库初始化
-- =============================================

CREATE TABLE IF NOT EXISTS `datasource` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `tenant_id` BIGINT NOT NULL DEFAULT 1,
    `name` VARCHAR(128) NOT NULL,
    `type` VARCHAR(32) NOT NULL COMMENT 'MYSQL/POSTGRESQL/ORACLE/SQLSERVER/CLICKHOUSE/ES/HIVE',
    `host` VARCHAR(256) NOT NULL,
    `port` INT NOT NULL,
    `database_name` VARCHAR(128) DEFAULT '',
    `username` VARCHAR(128) DEFAULT '',
    `password` VARCHAR(512) DEFAULT '',
    `properties` JSON DEFAULT NULL COMMENT '额外连接参数',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '0-不可用 1-可用',
    `last_check_time` DATETIME DEFAULT NULL,
    `description` VARCHAR(512) DEFAULT '',
    `create_by` BIGINT DEFAULT NULL,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_by` BIGINT DEFAULT NULL,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_tenant_id` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据源表';

CREATE TABLE IF NOT EXISTS `datasource_metadata` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `datasource_id` BIGINT NOT NULL,
    `table_name` VARCHAR(256) NOT NULL,
    `table_comment` VARCHAR(512) DEFAULT '',
    `columns_json` JSON DEFAULT NULL COMMENT '列信息JSON',
    `sync_time` DATETIME DEFAULT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_datasource_id` (`datasource_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据源元数据表';

USE `db_etl`;

-- =============================================
-- ETL 服务 (etl-service)
-- =============================================

CREATE TABLE IF NOT EXISTS `etl_task` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `tenant_id` BIGINT NOT NULL DEFAULT 1,
    `name` VARCHAR(128) NOT NULL,
    `description` VARCHAR(512) DEFAULT '',
    `dag_json` JSON NOT NULL COMMENT 'DAG定义JSON',
    `status` TINYINT NOT NULL DEFAULT 0 COMMENT '0-草稿 1-启用 2-禁用',
    `cron_expression` VARCHAR(64) DEFAULT NULL,
    `last_run_time` DATETIME DEFAULT NULL,
    `last_run_status` VARCHAR(16) DEFAULT NULL,
    `create_by` BIGINT DEFAULT NULL,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_by` BIGINT DEFAULT NULL,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_tenant_id` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='ETL任务表';

CREATE TABLE IF NOT EXISTS `etl_task_instance` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `task_id` BIGINT NOT NULL,
    `tenant_id` BIGINT NOT NULL DEFAULT 1,
    `trigger_type` TINYINT NOT NULL COMMENT '1-手动 2-定时 3-API',
    `status` VARCHAR(16) NOT NULL COMMENT 'RUNNING/SUCCESS/FAILED/CANCELLED',
    `start_time` DATETIME DEFAULT NULL,
    `end_time` DATETIME DEFAULT NULL,
    `duration_ms` BIGINT DEFAULT NULL,
    `log` LONGTEXT DEFAULT NULL,
    `error_msg` TEXT DEFAULT NULL,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_task_id` (`task_id`),
    KEY `idx_tenant_status` (`tenant_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='ETL任务实例表';

USE `db_model`;

-- =============================================
-- 数据建模服务 (model-service)
-- =============================================

CREATE TABLE IF NOT EXISTS `dataset` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `tenant_id` BIGINT NOT NULL DEFAULT 1,
    `name` VARCHAR(128) NOT NULL,
    `datasource_id` BIGINT NOT NULL,
    `table_name` VARCHAR(256) NOT NULL,
    `type` TINYINT NOT NULL COMMENT '1-物理表 2-自定义SQL 3-多表关联',
    `sql_content` TEXT DEFAULT NULL,
    `join_config` JSON DEFAULT NULL,
    `columns_json` JSON DEFAULT NULL,
    `description` VARCHAR(512) DEFAULT '',
    `create_by` BIGINT DEFAULT NULL,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_by` BIGINT DEFAULT NULL,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_tenant_ds` (`tenant_id`, `datasource_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据集表';

CREATE TABLE IF NOT EXISTS `model_dimension` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `tenant_id` BIGINT NOT NULL DEFAULT 1,
    `dataset_id` BIGINT NOT NULL,
    `name` VARCHAR(128) NOT NULL,
    `column_name` VARCHAR(256) NOT NULL,
    `data_type` VARCHAR(32) NOT NULL,
    `dimension_type` VARCHAR(32) NOT NULL COMMENT 'TIME/GEO/CATEGORY/NUMERIC',
    `format` VARCHAR(64) DEFAULT '',
    `hierarchy_json` JSON DEFAULT NULL,
    `sort` INT NOT NULL DEFAULT 0,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_dataset_id` (`dataset_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='维度表';

CREATE TABLE IF NOT EXISTS `model_metric` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `tenant_id` BIGINT NOT NULL DEFAULT 1,
    `dataset_id` BIGINT NOT NULL,
    `name` VARCHAR(128) NOT NULL,
    `column_name` VARCHAR(256) DEFAULT NULL,
    `expression` TEXT DEFAULT NULL COMMENT '计算表达式',
    `agg_function` VARCHAR(32) NOT NULL COMMENT 'SUM/AVG/COUNT/MAX/MIN/CUSTOM',
    `data_type` VARCHAR(32) NOT NULL,
    `format` VARCHAR(64) DEFAULT '',
    `description` VARCHAR(512) DEFAULT '',
    `sort` INT NOT NULL DEFAULT 0,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_dataset_id` (`dataset_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='指标表';

USE `db_analysis`;

-- =============================================
-- 自助分析服务 (analysis-service)
-- =============================================

CREATE TABLE IF NOT EXISTS `query_history` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `tenant_id` BIGINT NOT NULL DEFAULT 1,
    `user_id` BIGINT NOT NULL,
    `dataset_id` BIGINT NOT NULL,
    `query_json` JSON NOT NULL,
    `sql_text` TEXT DEFAULT NULL,
    `duration_ms` INT DEFAULT NULL,
    `row_count` INT DEFAULT NULL,
    `status` TINYINT NOT NULL DEFAULT 1,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_user_time` (`user_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='查询历史表';

USE `db_dashboard`;

-- =============================================
-- 仪表板服务 (dashboard-service)
-- =============================================

CREATE TABLE IF NOT EXISTS `dashboard` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `tenant_id` BIGINT NOT NULL DEFAULT 1,
    `name` VARCHAR(128) NOT NULL,
    `description` VARCHAR(512) DEFAULT '',
    `config_json` JSON DEFAULT NULL,
    `layout_json` JSON DEFAULT NULL,
    `cover_url` VARCHAR(512) DEFAULT '',
    `status` TINYINT NOT NULL DEFAULT 0 COMMENT '0-草稿 1-已发布',
    `view_count` INT NOT NULL DEFAULT 0,
    `like_count` INT NOT NULL DEFAULT 0,
    `is_template` TINYINT NOT NULL DEFAULT 0,
    `create_by` BIGINT DEFAULT NULL,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_by` BIGINT DEFAULT NULL,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_create_by` (`create_by`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='仪表板表';

CREATE TABLE IF NOT EXISTS `dashboard_widget` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `dashboard_id` BIGINT NOT NULL,
    `widget_type` VARCHAR(32) NOT NULL COMMENT 'CHART/TABLE/TEXT/IMAGE/FILTER',
    `title` VARCHAR(128) DEFAULT '',
    `config_json` JSON NOT NULL,
    `data_config_json` JSON NOT NULL,
    `position_json` JSON NOT NULL,
    `sort` INT NOT NULL DEFAULT 0,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_dashboard_id` (`dashboard_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='仪表板组件表';

USE `db_screen`;

-- =============================================
-- 大屏服务 (screen-service)
-- =============================================

CREATE TABLE IF NOT EXISTS `screen` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `tenant_id` BIGINT NOT NULL DEFAULT 1,
    `name` VARCHAR(128) NOT NULL,
    `description` VARCHAR(512) DEFAULT '',
    `config_json` JSON NOT NULL,
    `components_json` JSON NOT NULL,
    `variants_json` JSON DEFAULT NULL COMMENT '三端配置变体 {"pc":{...},"mobile":{...},"tablet":{...}}，缺省端回退顶层配置',
    `cover_url` VARCHAR(512) DEFAULT '',
    `status` TINYINT NOT NULL DEFAULT 0 COMMENT '0-草稿 1-已发布',
    `adapt_mode` VARCHAR(16) NOT NULL DEFAULT 'SCALE',
    `view_count` INT NOT NULL DEFAULT 0,
    `share_token` VARCHAR(64) DEFAULT NULL,
    `share_expire_time` DATETIME DEFAULT NULL,
    `create_by` BIGINT DEFAULT NULL,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_by` BIGINT DEFAULT NULL,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_tenant_id` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='大屏表';

USE `db_collab`;

-- =============================================
-- 协作服务 (collab-service)
-- =============================================

CREATE TABLE IF NOT EXISTS `collab_comment` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `tenant_id` BIGINT NOT NULL DEFAULT 1,
    `target_type` VARCHAR(32) NOT NULL,
    `target_id` BIGINT NOT NULL,
    `content` TEXT NOT NULL,
    `parent_id` BIGINT DEFAULT 0,
    `mentions_json` JSON DEFAULT NULL,
    `create_by` BIGINT DEFAULT NULL,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `deleted` TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_target` (`target_type`, `target_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评论表';

CREATE TABLE IF NOT EXISTS `collab_subscription` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `tenant_id` BIGINT NOT NULL DEFAULT 1,
    `user_id` BIGINT NOT NULL,
    `target_type` VARCHAR(32) NOT NULL,
    `target_id` BIGINT NOT NULL,
    `notify_types` VARCHAR(256) NOT NULL DEFAULT 'ALL',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_target` (`user_id`, `target_type`, `target_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订阅表';

USE `db_alert`;

-- =============================================
-- 告警服务 (alert-service)
-- =============================================

CREATE TABLE IF NOT EXISTS `alert_rule` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `name` VARCHAR(128) NOT NULL,
    `description` VARCHAR(512) DEFAULT NULL,
    `type` VARCHAR(32) NOT NULL DEFAULT 'THRESHOLD',
    `datasource_id` BIGINT DEFAULT NULL,
    `metric_expression` VARCHAR(512) DEFAULT NULL,
    `condition` VARCHAR(32) DEFAULT NULL,
    `threshold` DECIMAL(20,4) DEFAULT NULL,
    `duration` INT DEFAULT NULL,
    `severity` VARCHAR(16) NOT NULL DEFAULT 'WARNING',
    `notify_channels` VARCHAR(512) DEFAULT NULL,
    `enabled` TINYINT NOT NULL DEFAULT 1,
    `tenant_id` BIGINT DEFAULT NULL,
    `create_by` VARCHAR(64) DEFAULT NULL,
    `update_by` VARCHAR(64) DEFAULT NULL,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` INT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_tenant_enabled` (`tenant_id`, `enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='告警规则表';

CREATE TABLE IF NOT EXISTS `alert_event` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `rule_id` BIGINT NOT NULL,
    `rule_name` VARCHAR(256) DEFAULT NULL,
    `trigger_value` DECIMAL(20,4) DEFAULT NULL,
    `severity` VARCHAR(16) NOT NULL,
    `status` VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    `message` TEXT DEFAULT NULL,
    `notified_at` DATETIME DEFAULT NULL,
    `resolved_at` DATETIME DEFAULT NULL,
    `tenant_id` BIGINT DEFAULT NULL,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_rule_id` (`rule_id`),
    KEY `idx_tenant_status` (`tenant_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='告警事件表';

CREATE TABLE IF NOT EXISTS `alert_notify_log` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `event_id` BIGINT NOT NULL,
    `channel` VARCHAR(32) NOT NULL,
    `recipient` VARCHAR(256) DEFAULT NULL,
    `status` VARCHAR(16) NOT NULL DEFAULT 'SUCCESS',
    `error_message` TEXT DEFAULT NULL,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_event_id` (`event_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='告警通知日志';

CREATE TABLE IF NOT EXISTS `notify_channel` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `name` VARCHAR(128) NOT NULL,
    `type` VARCHAR(32) NOT NULL,
    `config` TEXT DEFAULT NULL,
    `enabled` TINYINT NOT NULL DEFAULT 1,
    `tenant_id` BIGINT DEFAULT NULL,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_tenant_type` (`tenant_id`, `type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通知渠道配置';

USE `db_admin`;

-- =============================================
-- 平台管理服务 (admin-service)
-- =============================================

-- 列名/类型严格对齐 admin-service entity（SysTenant / SysConfig / AuditLog）
CREATE TABLE IF NOT EXISTS `sys_tenant` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `name` VARCHAR(128) NOT NULL,
    `code` VARCHAR(64) NOT NULL,
    `contact_name` VARCHAR(64) DEFAULT '',
    `contact_phone` VARCHAR(20) DEFAULT '',
    `contact_email` VARCHAR(128) DEFAULT '',
    `max_users` INT NOT NULL DEFAULT 100,
    `max_datasources` INT NOT NULL DEFAULT 10,
    `max_storage_mb` INT NOT NULL DEFAULT 10240,
    `config` TEXT DEFAULT NULL COMMENT '扩展配置 JSON',
    `status` VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/DISABLED',
    `expire_time` DATETIME DEFAULT NULL,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tenant_code` (`code`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='租户表';

CREATE TABLE IF NOT EXISTS `sys_config` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `config_key` VARCHAR(128) NOT NULL,
    `config_value` TEXT NOT NULL,
    `config_type` VARCHAR(32) NOT NULL DEFAULT 'CUSTOM' COMMENT 'SYSTEM/CUSTOM',
    `remark` VARCHAR(256) DEFAULT '',
    `tenant_id` BIGINT DEFAULT NULL,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_config_key` (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统配置表';

-- admin-service 写入的操作审计（与 db_monitor.audit_log 不同：这里记录接口调用明细）
CREATE TABLE IF NOT EXISTS `audit_log` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT DEFAULT NULL,
    `username` VARCHAR(64) DEFAULT '',
    `module` VARCHAR(64) DEFAULT '',
    `action` VARCHAR(64) DEFAULT '',
    `method` VARCHAR(16) DEFAULT '',
    `request_url` VARCHAR(256) DEFAULT '',
    `request_params` TEXT DEFAULT NULL,
    `response_code` INT DEFAULT NULL,
    `ip` VARCHAR(64) DEFAULT '',
    `user_agent` VARCHAR(512) DEFAULT '',
    `execution_time` BIGINT DEFAULT NULL COMMENT '耗时(ms)',
    `tenant_id` BIGINT DEFAULT NULL,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_create_time` (`create_time`),
    KEY `idx_username` (`username`),
    KEY `idx_module_action` (`module`, `action`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='接口操作审计';

-- 默认租户
INSERT INTO `sys_tenant` (`id`, `name`, `code`, `max_users`, `max_datasources`)
VALUES (1, '默认租户', 'default', 1000, 100);

USE `db_monitor`;

-- =============================================
-- 监控服务 (monitor-service)
-- =============================================

CREATE TABLE IF NOT EXISTS `audit_log` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `tenant_id` BIGINT NOT NULL DEFAULT 1,
    `user_id` BIGINT DEFAULT NULL,
    `username` VARCHAR(64) DEFAULT '',
    `module` VARCHAR(64) NOT NULL,
    `action` VARCHAR(64) NOT NULL,
    `target_type` VARCHAR(64) DEFAULT '',
    `target_id` VARCHAR(64) DEFAULT '',
    `detail` TEXT DEFAULT NULL,
    `ip` VARCHAR(64) DEFAULT '',
    `user_agent` VARCHAR(512) DEFAULT '',
    `duration` INT DEFAULT NULL,
    `status` TINYINT NOT NULL DEFAULT 1,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_tenant_time` (`tenant_id`, `create_time`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审计日志表';

CREATE TABLE IF NOT EXISTS `service_instance` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `service_name` VARCHAR(128) NOT NULL,
    `instance_id` VARCHAR(256) DEFAULT NULL,
    `host` VARCHAR(128) DEFAULT NULL,
    `port` INT DEFAULT NULL,
    `status` VARCHAR(32) NOT NULL DEFAULT 'UP',
    `metadata` TEXT DEFAULT NULL,
    `last_heartbeat` DATETIME DEFAULT NULL,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_service_name` (`service_name`),
    KEY `idx_status_heartbeat` (`status`, `last_heartbeat`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='服务实例表';

CREATE TABLE IF NOT EXISTS `monitor_metric` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `service_name` VARCHAR(128) NOT NULL,
    `instance_id` VARCHAR(256) DEFAULT NULL,
    `metric_name` VARCHAR(128) NOT NULL,
    `metric_value` DECIMAL(20,6) DEFAULT NULL,
    `metric_type` VARCHAR(32) DEFAULT 'GAUGE',
    `tags` TEXT DEFAULT NULL,
    `timestamp` DATETIME DEFAULT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_service_metric` (`service_name`, `metric_name`),
    KEY `idx_timestamp` (`timestamp`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='监控指标表';

CREATE TABLE IF NOT EXISTS `monitor_alert` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `service_name` VARCHAR(128) NOT NULL,
    `metric_name` VARCHAR(128) NOT NULL,
    `condition` VARCHAR(32) NOT NULL,
    `threshold` DECIMAL(20,6) DEFAULT NULL,
    `message` VARCHAR(512) DEFAULT NULL,
    `status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `resolved_time` DATETIME DEFAULT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_service_name` (`service_name`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='监控告警表';

USE `db_file`;

-- =============================================
-- 文件服务 (file-service)
-- =============================================

CREATE TABLE IF NOT EXISTS `file_info` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `tenant_id` BIGINT DEFAULT NULL COMMENT '租户ID',
    `file_name` VARCHAR(255) NOT NULL COMMENT '原始文件名',
    `file_path` VARCHAR(512) NOT NULL COMMENT '存储对象名（相对 base-path，如 2026/09/21/uuid.png）',
    `file_size` BIGINT DEFAULT NULL COMMENT '文件字节数',
    `file_type` VARCHAR(128) DEFAULT NULL COMMENT 'MIME 类型，image/* 可走免登 /api/file/view/{id}',
    `bucket` VARCHAR(64) DEFAULT NULL COMMENT '存储目录/桶名',
    `create_by` BIGINT DEFAULT NULL COMMENT '上传人',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文件信息表';
