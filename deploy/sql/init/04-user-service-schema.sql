-- 会话字符集：docker-entrypoint / mysql CLI 默认按 latin1 协商会话，中文会在入库时直接变成 `?`（不可逆）。
-- 命令行执行时另需传 --default-character-set=utf8mb4（两者都指向 utf8mb4 才不会丢字符）。
SET NAMES utf8mb4;

USE `db_user`;

-- =============================================
-- user-service 表结构（01-create-databases.sql 只建库不建表，此前 db_user 为空库，
-- 导致 /user/page、/role/list、/permission/tree、/dept/tree 全部报错）
-- 列名严格对齐 com.dataviz.user.entity 下的 MyBatis-Plus 实体：
--   SysUser / SysRole(role_code, sort_order) / SysPermission(permission_code, sort_order)
--   SysDept(dept_name, sort_order) / SysUserRole / SysRolePermission
-- 登录链路仍由 auth-service 读写 db_auth，本库表数据从 db_auth 同步初始化。
-- =============================================

CREATE TABLE IF NOT EXISTS `sys_user` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `tenant_id` BIGINT NOT NULL DEFAULT 1,
    `username` VARCHAR(64) NOT NULL,
    `password` VARCHAR(128) NOT NULL,
    `nickname` VARCHAR(64) DEFAULT '',
    `email` VARCHAR(128) DEFAULT '',
    `phone` VARCHAR(20) DEFAULT '',
    `avatar` VARCHAR(256) DEFAULT '',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '0-禁用 1-启用',
    `dept_id` BIGINT DEFAULT NULL,
    `login_ip` VARCHAR(64) DEFAULT '',
    `login_time` DATETIME DEFAULT NULL,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `create_by` VARCHAR(64) DEFAULT '',
    `update_by` VARCHAR(64) DEFAULT '',
    `deleted` TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tenant_username` (`tenant_id`, `username`, `deleted`),
    KEY `idx_dept_id` (`dept_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表（管理端读写）';

CREATE TABLE IF NOT EXISTS `sys_role` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `tenant_id` BIGINT NOT NULL DEFAULT 1,
    `role_code` VARCHAR(64) NOT NULL,
    `role_name` VARCHAR(64) NOT NULL,
    `description` VARCHAR(256) DEFAULT '',
    `sort_order` INT NOT NULL DEFAULT 0,
    `status` TINYINT NOT NULL DEFAULT 1,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `create_by` VARCHAR(64) DEFAULT '',
    `update_by` VARCHAR(64) DEFAULT '',
    `deleted` TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tenant_role_code` (`tenant_id`, `role_code`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色表（管理端读写）';

CREATE TABLE IF NOT EXISTS `sys_permission` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `parent_id` BIGINT NOT NULL DEFAULT 0,
    `permission_code` VARCHAR(128) NOT NULL,
    `permission_name` VARCHAR(64) NOT NULL,
    `type` TINYINT NOT NULL COMMENT '1-目录 2-菜单 3-按钮',
    `path` VARCHAR(256) DEFAULT '',
    `icon` VARCHAR(64) DEFAULT '',
    `sort_order` INT NOT NULL DEFAULT 0,
    `status` TINYINT NOT NULL DEFAULT 1,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_permission_code` (`permission_code`, `deleted`),
    KEY `idx_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='权限表（管理端读写）';

CREATE TABLE IF NOT EXISTS `sys_dept` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `tenant_id` BIGINT NOT NULL DEFAULT 1,
    `parent_id` BIGINT NOT NULL DEFAULT 0,
    `dept_name` VARCHAR(64) NOT NULL,
    `sort_order` INT NOT NULL DEFAULT 0,
    `leader` VARCHAR(64) DEFAULT '',
    `phone` VARCHAR(20) DEFAULT '',
    `status` TINYINT NOT NULL DEFAULT 1,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `create_by` VARCHAR(64) DEFAULT '',
    `update_by` VARCHAR(64) DEFAULT '',
    `deleted` TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_tenant_parent` (`tenant_id`, `parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='部门表（管理端读写）';

CREATE TABLE IF NOT EXISTS `sys_user_role` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `role_id` BIGINT NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_role` (`user_id`, `role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户角色关联';

CREATE TABLE IF NOT EXISTS `sys_role_permission` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `role_id` BIGINT NOT NULL,
    `permission_id` BIGINT NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_permission` (`role_id`, `permission_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色权限关联';

-- =============================================
-- 从 db_auth 同步初始数据（幂等：重复执行不会产生重复行）
-- =============================================

-- 以下 SELECT 的列名与 02-auth-schema.sql 的 db_auth 表保持一致
-- （历史上 db_auth 用 role_key/permission_key/sort/remark，与 SysRole/SysPermission 实体不符，
--   已由 02 与 2026-09-21-schema-conflict-fix.sql 统一为 role_code/permission_code/sort_order/description）
INSERT IGNORE INTO `sys_user`
    (`id`, `tenant_id`, `username`, `password`, `nickname`, `email`, `phone`, `avatar`, `status`, `dept_id`, `deleted`)
SELECT `id`, `tenant_id`, `username`, `password`, `nickname`,
       COALESCE(`email`, ''), COALESCE(`phone`, ''), COALESCE(`avatar`, ''), `status`, `dept_id`, `deleted`
FROM `db_auth`.`sys_user`;

INSERT IGNORE INTO `sys_role` (`id`, `tenant_id`, `role_code`, `role_name`, `description`, `sort_order`, `status`, `deleted`)
SELECT `id`, `tenant_id`, `role_code`, `role_name`, COALESCE(`description`, ''), `sort_order`, `status`, `deleted`
FROM `db_auth`.`sys_role`;

INSERT IGNORE INTO `sys_permission`
    (`id`, `parent_id`, `permission_code`, `permission_name`, `type`, `path`, `icon`, `sort_order`, `status`, `deleted`)
SELECT `id`, `parent_id`, `permission_code`, `permission_name`, `type`,
       COALESCE(`path`, ''), COALESCE(`icon`, ''), `sort_order`, `status`, `deleted`
FROM `db_auth`.`sys_permission`;

INSERT IGNORE INTO `sys_dept` (`id`, `tenant_id`, `parent_id`, `dept_name`, `sort_order`, `leader`, `phone`, `status`, `deleted`)
SELECT `id`, `tenant_id`, `parent_id`, `dept_name`, `sort_order`, COALESCE(`leader`, ''), COALESCE(`phone`, ''), `status`, `deleted`
FROM `db_auth`.`sys_dept`;

INSERT IGNORE INTO `sys_user_role` (`user_id`, `role_id`)
SELECT `user_id`, `role_id` FROM `db_auth`.`sys_user_role`;

INSERT IGNORE INTO `sys_role_permission` (`role_id`, `permission_id`)
SELECT `role_id`, `permission_id` FROM `db_auth`.`sys_role_permission`;
