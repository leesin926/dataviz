-- ============================================================================
-- 阶段 AG：短信登录 —— 给可登录账号补齐手机号 + 加查询索引
-- 决策依据：用户拍板"不统一：每个账号一个号"，手机号即短信登录定位账号的唯一身份。
-- 执行方式：由用户在 MySQL 客户端整段执行（本仓约定，见进度表 D31/D35）。
--   docker exec -i dataviz-mysql mysql --default-character-set=utf8mb4 -uroot -p < 本文件
-- ============================================================================
SET NAMES utf8mb4;

USE `db_user`;

-- ----------------------------------------------------------------------------
-- 1. 手机号：admin 用演示主号，901~905 各保留自己那一条
--    按 username 定位而不是按 id，避免"某台机器上 id 不一样"时误伤。
-- ----------------------------------------------------------------------------
UPDATE `sys_user` SET `phone` = '13912345678', `update_time` = NOW() WHERE `username` = 'admin';
UPDATE `sys_user` SET `phone` = '13900000901', `update_time` = NOW() WHERE `username` = 'analyst01';
UPDATE `sys_user` SET `phone` = '13900000902', `update_time` = NOW() WHERE `username` = 'analyst02';
UPDATE `sys_user` SET `phone` = '13900000903', `update_time` = NOW() WHERE `username` = 'designer01';
UPDATE `sys_user` SET `phone` = '13900000904', `update_time` = NOW() WHERE `username` = 'ops01';
UPDATE `sys_user` SET `phone` = '13900000905', `update_time` = NOW() WHERE `username` = 'viewer01';

-- ----------------------------------------------------------------------------
-- 2. 索引：短信登录是免登面上的全表扫描入口，必须走索引
--    刻意只做普通索引，不做唯一索引 —— phone 列默认值是 ''，
--    多个"没填手机号"的账号会一起撞在 '' 上，唯一约束根本建不起来。
--    重复执行时靠 information_schema 兜住，不再报 1061。
-- ----------------------------------------------------------------------------
SET @idx_exists := (
    SELECT COUNT(*) FROM `information_schema`.`STATISTICS`
    WHERE `TABLE_SCHEMA` = 'db_user' AND `TABLE_NAME` = 'sys_user' AND `INDEX_NAME` = 'idx_phone'
);
SET @ddl := IF(@idx_exists = 0,
    'ALTER TABLE `sys_user` ADD INDEX `idx_phone` (`phone`)',
    'SELECT ''idx_phone 已存在，跳过'' AS note');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ----------------------------------------------------------------------------
-- 3. 校验（把这三段输出回报）
-- ----------------------------------------------------------------------------
-- 3a. 期望 0 行：一个手机号对多个可登录账号，意味着短信登录会随机登进其中一个
SELECT `phone`, COUNT(*) AS `cnt`
FROM `sys_user`
WHERE `deleted` = 0 AND `phone` <> ''
GROUP BY `phone` HAVING COUNT(*) > 1;

-- 3b. 期望 6 行，admin = 13912345678，且六个号互不相同
SELECT `id`, `username`, `phone`, `status`, `deleted`
FROM `sys_user`
WHERE `username` IN ('admin', 'analyst01', 'analyst02', 'designer01', 'ops01', 'viewer01')
ORDER BY `id`;

-- 3c. 列出仍没有手机号、因此走不了短信登录的账号（演示账号里期望为 0 行；
--     若这里还有一批业务演示用户，属正常 —— 它们只是不能用短信这条路）
SELECT `id`, `username`
FROM `sys_user`
WHERE `deleted` = 0 AND (`phone` IS NULL OR `phone` = '');
