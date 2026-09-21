-- 会话字符集：docker-entrypoint / mysql CLI 默认按 latin1 协商会话，中文会在入库时直接变成 `?`（不可逆）。
-- 命令行执行时另需传 --default-character-set=utf8mb4（两者都指向 utf8mb4 才不会丢字符）。
SET NAMES utf8mb4;

USE `db_auth`;

-- =============================================
-- 认证服务 (auth-service) 数据库初始化
-- =============================================

-- 用户表
CREATE TABLE IF NOT EXISTS `sys_user` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `tenant_id` BIGINT NOT NULL DEFAULT 1,
    `username` VARCHAR(64) NOT NULL,
    `password` VARCHAR(256) NOT NULL,
    `nickname` VARCHAR(64) DEFAULT '',
    `email` VARCHAR(128) DEFAULT '',
    `phone` VARCHAR(20) DEFAULT '',
    `avatar` VARCHAR(512) DEFAULT '',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '0-禁用 1-正常',
    `dept_id` BIGINT DEFAULT NULL COMMENT '部门ID(供 db_user 同步用；SysUser 实体无此字段)',
    `login_ip` VARCHAR(64) DEFAULT '',
    `login_time` DATETIME DEFAULT NULL,
    `last_login_time` DATETIME DEFAULT NULL,
    `create_by` VARCHAR(64) DEFAULT NULL COMMENT '创建人用户名(SysUser.createBy 为 String)',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_by` VARCHAR(64) DEFAULT NULL COMMENT '更新人用户名',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tenant_username` (`tenant_id`, `username`, `deleted`),
    KEY `idx_tenant_id` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 角色表
-- 列名与 auth-service 实体对齐：roleCode→role_code、description→description、sortOrder→sort_order
-- （历史版本用 role_key / remark / sort，与实体不一致，需靠 2026-09-21-schema-conflict-fix.sql 改名才能读写）
CREATE TABLE IF NOT EXISTS `sys_role` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `tenant_id` BIGINT NOT NULL DEFAULT 1,
    `role_name` VARCHAR(64) NOT NULL,
    `role_code` VARCHAR(64) NOT NULL,
    `sort_order` INT NOT NULL DEFAULT 0,
    `status` TINYINT NOT NULL DEFAULT 1,
    `description` VARCHAR(256) DEFAULT '',
    `create_by` VARCHAR(64) DEFAULT NULL COMMENT '创建人用户名(SysRole.createBy 为 String)',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_by` VARCHAR(64) DEFAULT NULL COMMENT '更新人用户名',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tenant_role_code` (`tenant_id`, `role_code`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色表';

-- 权限表
CREATE TABLE IF NOT EXISTS `sys_permission` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `parent_id` BIGINT NOT NULL DEFAULT 0,
    `permission_name` VARCHAR(64) NOT NULL,
    `permission_code` VARCHAR(128) NOT NULL,
    `type` TINYINT NOT NULL COMMENT '1-目录 2-菜单 3-按钮',
    `path` VARCHAR(256) DEFAULT '',
    `component` VARCHAR(256) DEFAULT '',
    `icon` VARCHAR(64) DEFAULT '',
    `sort_order` INT NOT NULL DEFAULT 0,
    `visible` TINYINT NOT NULL DEFAULT 1,
    `status` TINYINT NOT NULL DEFAULT 1,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_permission_code` (`permission_code`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='权限表';

-- 用户角色关联表
CREATE TABLE IF NOT EXISTS `sys_user_role` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `role_id` BIGINT NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_role` (`user_id`, `role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户角色关联表';

-- 角色权限关联表
CREATE TABLE IF NOT EXISTS `sys_role_permission` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `role_id` BIGINT NOT NULL,
    `permission_id` BIGINT NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_permission` (`role_id`, `permission_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色权限关联表';

-- 登录日志表
CREATE TABLE IF NOT EXISTS `sys_login_log` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `tenant_id` BIGINT NOT NULL DEFAULT 1,
    `user_id` BIGINT DEFAULT NULL,
    `username` VARCHAR(64) NOT NULL,
    `login_type` TINYINT NOT NULL COMMENT '1-密码 2-短信 3-SSO',
    `status` TINYINT NOT NULL COMMENT '0-失败 1-成功',
    `ip` VARCHAR(64) DEFAULT '',
    `user_agent` VARCHAR(512) DEFAULT '',
    `message` VARCHAR(256) DEFAULT '',
    `login_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_tenant_user` (`tenant_id`, `user_id`),
    KEY `idx_login_time` (`login_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='登录日志表';

-- OAuth 客户端表
CREATE TABLE IF NOT EXISTS `sys_oauth_client` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `client_id` VARCHAR(128) NOT NULL,
    `client_secret` VARCHAR(256) NOT NULL,
    `client_name` VARCHAR(64) NOT NULL,
    `grant_types` VARCHAR(256) NOT NULL,
    `redirect_uris` VARCHAR(1024) DEFAULT '',
    `scopes` VARCHAR(512) DEFAULT '',
    `access_token_ttl` INT NOT NULL DEFAULT 7200,
    `refresh_token_ttl` INT NOT NULL DEFAULT 604800,
    `status` TINYINT NOT NULL DEFAULT 1,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_client_id` (`client_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OAuth客户端表';

-- 部门表
CREATE TABLE IF NOT EXISTS `sys_dept` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `tenant_id` BIGINT NOT NULL DEFAULT 1,
    `parent_id` BIGINT NOT NULL DEFAULT 0,
    `dept_name` VARCHAR(64) NOT NULL,
    `sort_order` INT NOT NULL DEFAULT 0,
    `leader` VARCHAR(64) DEFAULT '',
    `phone` VARCHAR(20) DEFAULT '',
    `status` TINYINT NOT NULL DEFAULT 1,
    `create_by` VARCHAR(64) DEFAULT NULL COMMENT '创建人用户名',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_by` VARCHAR(64) DEFAULT NULL COMMENT '更新人用户名',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_tenant_parent` (`tenant_id`, `parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='部门表';

-- =============================================
-- 初始数据
-- =============================================

-- 默认租户管理员 (密码: admin123, BCrypt加密)
INSERT INTO `sys_user` (`id`, `tenant_id`, `username`, `password`, `nickname`, `status`)
VALUES (1, 1, 'admin', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '系统管理员', 1);

-- 默认角色（列名与 SysRole 实体一致：role_code / sort_order / description）
INSERT INTO `sys_role` (`id`, `tenant_id`, `role_name`, `role_code`, `sort_order`, `status`) VALUES
(1, 1, '超级管理员', 'super_admin', 1, 1),
(2, 1, '系统管理员', 'admin', 2, 1),
(3, 1, '普通用户', 'user', 3, 1);

-- 管理员角色关联
INSERT INTO `sys_user_role` (`user_id`, `role_id`) VALUES (1, 1);

-- 默认权限
INSERT INTO `sys_permission` (`id`, `parent_id`, `permission_name`, `permission_code`, `type`, `path`, `icon`, `sort_order`) VALUES
(1, 0, '系统管理', 'system', 1, '/system', 'Setting', 100),
(2, 1, '用户管理', 'system:user:list', 2, '/system/user', 'User', 1),
(3, 1, '角色管理', 'system:role:list', 2, '/system/role', 'UserFilled', 2),
(4, 1, '菜单管理', 'system:menu:list', 2, '/system/menu', 'Menu', 3),
(5, 1, '租户管理', 'system:tenant:list', 2, '/system/tenant', 'OfficeBuilding', 4),
(6, 0, '数据源管理', 'datasource', 1, '/datasource', 'Connection', 50),
(7, 0, 'ETL 管理', 'etl', 1, '/etl', 'Share', 40),
(8, 0, '数据建模', 'model', 1, '/model', 'Grid', 35),
(9, 0, '自助分析', 'analysis', 1, '/analysis', 'DataAnalysis', 30),
(10, 0, '仪表板', 'dashboard', 1, '/dashboard', 'Monitor', 20),
(11, 0, '大屏管理', 'screen', 1, '/screen', 'FullScreen', 15),
(12, 0, '告警中心', 'alert', 1, '/alert', 'Bell', 60),
(13, 0, '系统监控', 'monitor', 1, '/monitor', 'View', 90),
(14, 2, '用户新增', 'system:user:add', 3, '', '', 1),
(15, 2, '用户编辑', 'system:user:edit', 3, '', '', 2),
(16, 2, '用户删除', 'system:user:delete', 3, '', '', 3),
(17, 2, '用户查询', 'system:user:query', 3, '', '', 4);

-- 超级管理员拥有所有权限
INSERT INTO `sys_role_permission` (`role_id`, `permission_id`)
SELECT 1, id FROM `sys_permission`;

-- 默认部门
INSERT INTO `sys_dept` (`id`, `tenant_id`, `parent_id`, `dept_name`, `sort_order`) VALUES
(1, 1, 0, '总公司', 1),
(2, 1, 1, '技术部', 1),
(3, 1, 1, '产品部', 2),
(4, 1, 1, '运营部', 3);
