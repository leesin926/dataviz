-- 会话字符集：docker-entrypoint / mysql CLI 默认按 latin1 协商会话，中文会在入库时直接变成 `?`（不可逆）。
-- 命令行执行时另需传 --default-character-set=utf8mb4（两者都指向 utf8mb4 才不会丢字符）。
SET NAMES utf8mb4;

-- =============================================
-- 文件服务 (file-service) 表结构
-- 背景：db_file 在初始脚本里只建库不建表，file_info 缺失导致上传链路不可用
-- 日期：2026-09-21
-- =============================================

USE `db_file`;

CREATE TABLE IF NOT EXISTS `file_info` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `tenant_id` BIGINT DEFAULT NULL COMMENT '租户ID',
    `file_name` VARCHAR(255) NOT NULL COMMENT '原始文件名',
    `file_path` VARCHAR(512) NOT NULL COMMENT '存储对象名（相对 base-path 的路径，如 2026/09/21/uuid.png）',
    `file_size` BIGINT DEFAULT NULL COMMENT '文件字节数',
    `file_type` VARCHAR(128) DEFAULT NULL COMMENT 'MIME 类型，image/* 可走免登 /api/file/view/{id}',
    `bucket` VARCHAR(64) DEFAULT NULL COMMENT '存储目录/桶名',
    `create_by` BIGINT DEFAULT NULL COMMENT '上传人',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文件信息表';
