SET NAMES utf8mb4;

-- =============================================
-- BE3 · file-service 存储策略可选：为 file_info 补 `storage_type` 列
-- 日期：2026-11-06
-- 背景：写侧策略此前被 `@Qualifier("localStorageStrategy")` 写死，每条上传都记 `storageType="local"`，
--       MinioStorageStrategy 是死代码。改成 `file.storage.type` 配置驱动后，
--       每条文件要记录**真正处理它的那个策略**，否则日后 local→minio 一切换，
--       老行会被当前配置误导（明明在本地盘上却按 minio 去读），且读侧无法按行路由。
--       init/03-all-services-schema.sql 与 patch/2026-09-21-file-info-table.sql 建的 file_info
--       都没有这一列（已核对），因此这里补列并回填存量行。
-- ✅ 已于 2026-11-06 在 dataviz-mysql（容器起来后）执行，回读：@@BAD=0、@@DISTINCT=[local]、列定义 varchar(32) NOT NULL DEFAULT local。
--    （落笔当时数据库停机，脚本只落盘不执行；那一版警示已过期，此处按执行结果更正。）
-- =============================================

USE `db_file`;

-- ① 补列：幂等写法。MySQL 5.7 没有 `ADD COLUMN IF NOT EXISTS`，用 information_schema 判存再走动态 SQL。
SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'db_file' AND TABLE_NAME = 'file_info' AND COLUMN_NAME = 'storage_type'
);
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE `file_info` ADD COLUMN `storage_type` VARCHAR(32) NOT NULL DEFAULT ''local'' COMMENT ''实际处理该文件的存储策略 code：local/minio/...'' AFTER `bucket`',
    'SELECT ''storage_type column already present, skip ALTER'' AS note');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ② 回填存量行：这些行**只可能**是 local 处理的（minio 策略此前是死代码，从未被选过），
--    所以把 NULL/空一律定为 'local' 是说实话，不是猜。DEFAULT 'local' 已覆盖新行，
--    这一步兜住"列刚补上、旧行仍是 NULL"的窗口。
UPDATE `file_info` SET `storage_type` = 'local' WHERE `storage_type` IS NULL OR `storage_type` = '';

-- ============ 回读自检（跑完把输出贴回来） ============
-- 三段缺一不可："@@BAD 缺席" 与 "@@COUNT/@@DISTINCT 符合预期" 同时成立才算通过。
-- 期望：storage_type 列存在，且没有任何一行是 NULL/空。
SELECT CONCAT('@@BAD null-storage-type rows = ', COUNT(*)) FROM `file_info`
WHERE `storage_type` IS NULL OR `storage_type` = '';

-- 期望：回填后取值集合只有 [local]（本轮只补列，未产生 minio 行；出现别的值说明已被应用写入，需人工确认）。
SELECT CONCAT('@@DISTINCT storage_type = [', GROUP_CONCAT(DISTINCT `storage_type` ORDER BY `storage_type` SEPARATOR ','), ']')
FROM `file_info`;

-- 期望：@@COUNT column = 1，确认列确实加上了（类型 varchar、非空、默认 local）。
SELECT CONCAT('@@COUNT storage_type column def = ', COLUMN_TYPE, ' ~|~ ', IS_NULLABLE, ' ~|~ ', IFNULL(COLUMN_DEFAULT, '<none>'))
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = 'db_file' AND TABLE_NAME = 'file_info' AND COLUMN_NAME = 'storage_type';

-- ============ 回滚 ============
-- 回填只是把"其实一直是 local"的老行写明，无数据损失；如需彻底回退：
--   ALTER TABLE `db_file`.`file_info` DROP COLUMN `storage_type`;
-- 但应用侧 StorageStrategyRouter 依赖该列做读侧路由，回滚 DDL 前需同步回退代码，勿单独执行。
