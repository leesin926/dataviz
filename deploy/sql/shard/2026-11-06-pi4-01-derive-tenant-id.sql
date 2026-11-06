-- PI4-01 · 给两张"没有租户列"的增长表补 tenant_id 派生列并回填
-- 日期：2026-11-06　状态：**未执行**（本轮 Docker Desktop 停止，3306/3307 都连不上；脚本先落盘待起库后跑）
-- 为什么要这一列：分库的桶位函数是 MOD(MD5(tenant_id) 前 4 字节, buckets)，
--   openapi_log 与 alert_notify_log 都没有租户列 ⇒ 没有它就只能"按时间分表、按实例不分库"，
--   用户要的"两个 MySQL 实例"这一半就落不了地。派生而非猜：值从各自的主人那行抄过来。
-- ⚠️ 顺序敏感：月度物理表是 CREATE TABLE ... LIKE 建的，LIKE 复制列 —— 所以这一步必须在 02 之前跑，
--   否则月度表少一列，回填 INSERT 直接列数不匹配（那种错只在写进去的时候才暴露）。
SET NAMES utf8mb4;

USE `db_openapi`;

-- ① 补列（幂等：MySQL 5.7/8.0 都没有 ADD COLUMN IF NOT EXISTS，用 information_schema 判存再走动态 SQL）
SET @col := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = 'db_openapi' AND TABLE_NAME = 'openapi_log' AND COLUMN_NAME = 'tenant_id');
SET @ddl := IF(@col = 0,
  'ALTER TABLE `openapi_log` ADD COLUMN `tenant_id` BIGINT NULL COMMENT ''调用方所属租户，由 client_id/app_key 派生'' AFTER `client_id`, ADD KEY `idx_tenant_time` (`tenant_id`, `create_time`)',
  'SELECT ''openapi_log.tenant_id already present, skip ALTER'' AS note');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

-- ② 回填：先按 openapi_client.id 对（日志读端就是按 client 的可见 id 收口的，主口径必须是它）
UPDATE `openapi_log` l
  JOIN `db_openapi`.`openapi_client` c ON c.id = l.client_id
   AND l.tenant_id IS NULL
   SET l.tenant_id = c.tenant_id;

-- ③ 再按 openapi_app.id 对（回调验签读的是 openapi_app，写入的 client_id 其实是 app.id —— 见 API-162）
--    两条 UPDATE 的顺序不能反：主口径优先，app 只补 client 侧对不上的那些。
UPDATE `openapi_log` l
  JOIN `db_openapi`.`openapi_app` a ON a.id = l.client_id
   AND l.tenant_id IS NULL
   SET l.tenant_id = a.tenant_id;

-- ④ alert_notify_log：租户在事件那一侧，顺着 event_id 抄
USE `db_alert`;
SET @col2 := (SELECT COUNT(*) FROM information_schema.COLUMNS
              WHERE TABLE_SCHEMA = 'db_alert' AND TABLE_NAME = 'alert_notify_log' AND COLUMN_NAME = 'tenant_id');
SET @ddl2 := IF(@col2 = 0,
  'ALTER TABLE `alert_notify_log` ADD COLUMN `tenant_id` BIGINT NULL COMMENT ''所属租户，由 event_id 对应的事件派生'' AFTER `event_id`, ADD KEY `idx_tenant_time` (`tenant_id`, `create_time`)',
  'SELECT ''alert_notify_log.tenant_id already present, skip ALTER'' AS note');
PREPARE s2 FROM @ddl2; EXECUTE s2; DEALLOCATE PREPARE s2;

UPDATE `alert_notify_log` n
  JOIN `db_alert`.`alert_event` e ON e.id = n.event_id
   AND n.tenant_id IS NULL
   SET n.tenant_id = e.tenant_id;

-- ============ 回读自检（跑完把三段输出都贴回来） ============
-- 期望①：两张表都有 tenant_id 列且可空（NULL 是"派生不出主人"的行，见期望②，不是漏加）
SELECT CONCAT('@@COLUMN ', TABLE_SCHEMA, '.', TABLE_NAME, '.tenant_id = ', COLUMN_TYPE) FROM information_schema.COLUMNS
WHERE COLUMN_NAME = 'tenant_id' AND ((TABLE_SCHEMA = 'db_openapi' AND TABLE_NAME = 'openapi_log')
   OR (TABLE_SCHEMA = 'db_alert' AND TABLE_NAME = 'alert_notify_log'));

-- 期望②：孤儿行数为 0。不为 0 就是"主人已被删/从未存在"的行 —— 这些行分不了库，
-- 必须先决定处置（归到租户 1？还是留在逻辑表里不参与分片？），不许静默给个默认值。
SELECT CONCAT('@@ORPHAN openapi_log = ', COUNT(*)) FROM `db_openapi`.`openapi_log` WHERE tenant_id IS NULL;
SELECT CONCAT('@@ORPHAN alert_notify_log = ', COUNT(*)) FROM `db_alert`.`alert_notify_log` WHERE tenant_id IS NULL;

-- 期望③：总量对得上（回填只允许"多分出几行有租户"，绝不允许少行）
SELECT CONCAT('@@COUNT openapi_log total = ', COUNT(*), ', with-tenant = ', COUNT(tenant_id)) FROM `db_openapi`.`openapi_log`;
SELECT CONCAT('@@COUNT alert_notify_log total = ', COUNT(*), ', with-tenant = ', COUNT(tenant_id)) FROM `db_alert`.`alert_notify_log`;

-- ============ 回滚 ============
-- 派生列是新加的、没人写也没人读，回滚就是删列；但一旦 02/03 跑过，月度表也带着这一列，
-- 回滚必须三步一起：DROP COLUMN → DROP 月度表 → 代码侧 shard.enabled 关掉。别只删一半。
--   ALTER TABLE `db_openapi`.`openapi_log` DROP COLUMN `tenant_id`;
--   ALTER TABLE `db_alert`.`alert_notify_log` DROP COLUMN `tenant_id`;
