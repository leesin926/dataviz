-- 会话字符集：docker-entrypoint / mysql CLI 默认按 latin1 协商会话，中文会在入库时直接变成 `?`（不可逆）。
-- 命令行执行时另需传 --default-character-set=utf8mb4（两者都指向 utf8mb4 才不会丢字符）。
SET NAMES utf8mb4;

-- =============================================
-- 2026-09-21 存量库对齐补丁（针对已初始化的 dev MySQL 数据卷）
-- 背景：init/01~03 建库建表后，admin-service 与 user-service 的实体字段与库表列名不一致，
--      导致管理端「租户 / 系统配置 / 审计日志」以及设计端「用户 / 角色 / 菜单」接口 500。
-- 用法：整体执行一次；其中 CREATE TABLE 部分是幂等的，ALTER 部分重复执行会报 duplicate column，
--      可忽略（说明已应用过）。
-- 新库（清空数据卷重装）无需本文件：init/03 已按对齐后的结构建表，init/04 负责 db_user。
-- =============================================

-- ---------- A. db_admin：对齐 admin-service 实体 ----------

-- A1. sys_tenant：tenant_name/tenant_code → name/code，status TINYINT → VARCHAR(ACTIVE/DISABLED)，补 config 列
ALTER TABLE `db_admin`.`sys_tenant`
    CHANGE COLUMN `tenant_name` `name` VARCHAR(128) NOT NULL,
    CHANGE COLUMN `tenant_code` `code` VARCHAR(64) NOT NULL,
    ADD COLUMN `config` TEXT DEFAULT NULL COMMENT '扩展配置 JSON' AFTER `max_storage_mb`,
    MODIFY COLUMN `status` VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/DISABLED';

UPDATE `db_admin`.`sys_tenant` SET `status` = 'ACTIVE'   WHERE `status` = '1';
UPDATE `db_admin`.`sys_tenant` SET `status` = 'DISABLED' WHERE `status` = '0';

-- A2. sys_config：补 tenant_id，config_type 语义改为 SYSTEM/CUSTOM
ALTER TABLE `db_admin`.`sys_config`
    ADD COLUMN `tenant_id` BIGINT DEFAULT NULL AFTER `remark`,
    MODIFY COLUMN `config_type` VARCHAR(32) NOT NULL DEFAULT 'CUSTOM' COMMENT 'SYSTEM-内置 CUSTOM-自定义';

UPDATE `db_admin`.`sys_config`
SET `config_type` = 'CUSTOM'
WHERE `config_type` NOT IN ('SYSTEM', 'CUSTOM');

-- A3. 接口操作审计表（admin-service AuditLog 实体；此前仅 db_monitor 有同名不同构的表）
CREATE TABLE IF NOT EXISTS `db_admin`.`audit_log` (
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

-- ---------- B. db_user：建表并从 db_auth 同步初始数据 ----------
-- 直接执行 init 脚本即可（脚本首行是 USE `db_user`，全部语句幂等）：
--   mysql -uroot -p < deploy/sql/init/04-user-service-schema.sql
