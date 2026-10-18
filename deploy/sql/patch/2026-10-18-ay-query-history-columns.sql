-- =============================================================================
-- 2026-10-18 · 阶段 AY · AZ-draft：db_analysis.query_history 补齐 BaseEntity 列
-- 状态：**只落盘，未执行**。执行权在用户（见 D72 口径：执行权已交助手，决策权未交）。
--
-- 起因（运行时实证，两条判据都是 500，不是推断）：
--   GET /api/analysis/query/history  -> 500 {"message":"系统内部错误"}
--   GET /api/analysis/history        -> 500 {"message":"系统内部错误"}
--   analysis-service 日志根因：
--     java.sql.SQLSyntaxErrorException: Unknown column 'deleted' in 'where clause'
--       at QueryHistoryServiceImpl.getHistory(QueryHistoryServiceImpl.java:53)
--       at QueryServiceImpl.getHistory(QueryServiceImpl.java:139)
--
-- 根因：QueryHistory extends TenantEntity → BaseEntity 带 `deleted` 字段，
--   而每个服务的 application.yml 都配了
--     mybatis-plus.global-config.db-config.logic-delete-field: deleted
--   ⇒ MyBatis-Plus 给**所有**继承来的查询注入 `AND deleted = 0`。
--   03-all-services-schema.sql 建 query_history 时没有 deleted / update_time / create_by / update_by，
--   2026-09-21-schema-completion.sql 只补了 datasource_id / `sql` / execution_time，没补这四列。
--
-- 静态对账（devlog/_ay/ay11-entity-ddl-audit.cjs，本轮跑的）：
--   继承 TenantEntity/BaseEntity 的实体 8 个，其映射表**只有 query_history 一张**缺 `deleted`。
--   所以本补丁只动这一张表；其余 7 张（analysis_report / data_perm_rule / datasource /
--   etl_task / model_dataset / model_dimension / model_metric）列齐全，不要顺手改。
--
-- 与 2026-09-21 补丁 NOTE-2 的关系：那条注记说本表 INSERT 也会失败
--   （dataset_id / query_json 是 NOT NULL 而实体没有对应字段；status 是 TINYINT 而实体写 'SUCCESS'）。
--   本补丁把这三项一并收口，因为只补 `deleted` 的话，历史查询从 500 变成"永远空列表"——
--   写入仍然进不来，等于把故障换了个形状。
-- =============================================================================

SET NAMES utf8mb4;

USE `db_analysis`;

-- -----------------------------------------------------------------------------
-- 1) BaseEntity / TenantEntity 需要的审计列（缺一个就会在 INSERT 的列清单里报错）
--    全部带默认值 ⇒ 存量行不用回填，NULL 也不会被逻辑删除条件误杀
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `_ay_query_history_bak` LIKE `query_history`;
INSERT IGNORE INTO `_ay_query_history_bak` SELECT * FROM `query_history`;

SET @sql = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `query_history` ADD COLUMN `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT ''逻辑删除(0=未删,1=已删)''',
    'SELECT ''deleted 已存在，跳过'' AS note')
  FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = 'db_analysis' AND TABLE_NAME = 'query_history' AND COLUMN_NAME = 'deleted');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @sql = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `query_history` ADD COLUMN `create_by` VARCHAR(64) DEFAULT NULL COMMENT ''创建者''',
    'SELECT ''create_by 已存在，跳过'' AS note')
  FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = 'db_analysis' AND TABLE_NAME = 'query_history' AND COLUMN_NAME = 'create_by');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @sql = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `query_history` ADD COLUMN `update_by` VARCHAR(64) DEFAULT NULL COMMENT ''更新者''',
    'SELECT ''update_by 已存在，跳过'' AS note')
  FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = 'db_analysis' AND TABLE_NAME = 'query_history' AND COLUMN_NAME = 'update_by');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @sql = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `query_history` ADD COLUMN `update_time` DATETIME DEFAULT NULL COMMENT ''更新时间''',
    'SELECT ''update_time 已存在，跳过'' AS note')
  FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = 'db_analysis' AND TABLE_NAME = 'query_history' AND COLUMN_NAME = 'update_time');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- -----------------------------------------------------------------------------
-- 2) 让 INSERT 走得通：实体没有来源的两列放宽为可空
--    dataset_id / query_json 原为 NOT NULL 且无默认值，而 QueryHistory 实体不含这两个字段
--    ⇒ 改表不改代码；这两列是 03 号建表时的"自助分析"设想遗留，现在由 datasource_id + `sql` 承担
-- -----------------------------------------------------------------------------
ALTER TABLE `query_history`
    MODIFY COLUMN `dataset_id` BIGINT DEFAULT NULL COMMENT '数据集ID(旧模型遗留，实体不再写入)',
    MODIFY COLUMN `query_json` JSON DEFAULT NULL COMMENT '查询DSL(旧模型遗留，实体不再写入)';

-- -----------------------------------------------------------------------------
-- 3) status 类型对齐：实体写 'SUCCESS'/'FAILED'，列却是 TINYINT NOT NULL DEFAULT 1
--    STRICT_TRANS_TABLES 下这个转换直接报错，等于写不进历史。
--    ⚠️ 这是**类型变更**，不是增量加列：存量 1 会变成字符串 '1'（不会被读回来，
--       因为实体按 String 比较 'SUCCESS'），本表只有 901~9xx 几行演示数据。
--    如果不想动既有列，替代方案是加 `status_str` 并在实体上 @TableField(exist=false) 屏蔽 status —— 那条要改 Java，本补丁不选。
-- -----------------------------------------------------------------------------
ALTER TABLE `query_history`
    MODIFY COLUMN `status` VARCHAR(32) NOT NULL DEFAULT 'SUCCESS' COMMENT '执行状态(SUCCESS/FAILED)';

-- -----------------------------------------------------------------------------
-- 4) 校验（期望：4a=6 列齐全 / 4b 两条 history 接口 200 / 4c 备份行数与主表一致）
-- -----------------------------------------------------------------------------
SELECT COLUMN_NAME, DATA_TYPE, IS_NULLABLE, COLUMN_DEFAULT
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = 'db_analysis' AND TABLE_NAME = 'query_history'
  AND COLUMN_NAME IN ('deleted', 'create_by', 'update_by', 'update_time', 'status', 'dataset_id', 'query_json')
ORDER BY ORDINAL_POSITION;

SELECT COUNT(*) AS total_rows, SUM(deleted = 0) AS visible_rows FROM `query_history`;
SELECT (SELECT COUNT(*) FROM `query_history`) AS main_rows, (SELECT COUNT(*) FROM `_ay_query_history_bak`) AS backup_rows;

-- -----------------------------------------------------------------------------
-- 回滚（只回滚第 2、3 步的语义变更；第 1 步加的列留着不影响，MP 只会多查一个常量条件）
-- -----------------------------------------------------------------------------
-- ALTER TABLE `query_history`
--     MODIFY COLUMN `dataset_id` BIGINT NOT NULL,
--     MODIFY COLUMN `query_json` JSON NOT NULL,
--     MODIFY COLUMN `status` TINYINT NOT NULL DEFAULT 1;
-- DROP TABLE IF EXISTS `_ay_query_history_bak`;
