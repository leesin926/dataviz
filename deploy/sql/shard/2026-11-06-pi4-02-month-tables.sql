-- PI4 · 分表物理表：每个实例都建同一套月度表（数据按租户哈希分到实例，表结构两边一致）
-- ⚠️ 本文件必须在 **两个实例各跑一遍**：3306（ds0）与 3307（ds1）。只跑一边 = 一半的写没有落点。
--    跑法：docker exec -i dataviz-mysql   sh -c '...mysql...' < 本文件
--          docker exec -i dataviz-mysql-2 sh -c '...mysql...' < 本文件
-- 前置：先跑 2026-11-06-pi4-01-derive-tenant-id.sql（LIKE 要复制 tenant_id 派生列）。
-- 生成物，别手改：改窗口请改 devlog/_pi/gen-shard-ddl.cjs 后重新生成。
SET NAMES utf8mb4;

-- db_alert.alert_event（时间列 create_time）
CREATE TABLE IF NOT EXISTS `db_alert`.`alert_event_202608` LIKE `db_alert`.`alert_event`;
CREATE TABLE IF NOT EXISTS `db_alert`.`alert_event_202609` LIKE `db_alert`.`alert_event`;
CREATE TABLE IF NOT EXISTS `db_alert`.`alert_event_202610` LIKE `db_alert`.`alert_event`;
CREATE TABLE IF NOT EXISTS `db_alert`.`alert_event_202611` LIKE `db_alert`.`alert_event`;
CREATE TABLE IF NOT EXISTS `db_alert`.`alert_event_202612` LIKE `db_alert`.`alert_event`;
CREATE TABLE IF NOT EXISTS `db_alert`.`alert_event_202701` LIKE `db_alert`.`alert_event`;
CREATE TABLE IF NOT EXISTS `db_alert`.`alert_event_202702` LIKE `db_alert`.`alert_event`;

-- db_alert.alert_notify_log（时间列 create_time）
CREATE TABLE IF NOT EXISTS `db_alert`.`alert_notify_log_202608` LIKE `db_alert`.`alert_notify_log`;
CREATE TABLE IF NOT EXISTS `db_alert`.`alert_notify_log_202609` LIKE `db_alert`.`alert_notify_log`;
CREATE TABLE IF NOT EXISTS `db_alert`.`alert_notify_log_202610` LIKE `db_alert`.`alert_notify_log`;
CREATE TABLE IF NOT EXISTS `db_alert`.`alert_notify_log_202611` LIKE `db_alert`.`alert_notify_log`;
CREATE TABLE IF NOT EXISTS `db_alert`.`alert_notify_log_202612` LIKE `db_alert`.`alert_notify_log`;
CREATE TABLE IF NOT EXISTS `db_alert`.`alert_notify_log_202701` LIKE `db_alert`.`alert_notify_log`;
CREATE TABLE IF NOT EXISTS `db_alert`.`alert_notify_log_202702` LIKE `db_alert`.`alert_notify_log`;

-- db_openapi.openapi_log（时间列 create_time）
CREATE TABLE IF NOT EXISTS `db_openapi`.`openapi_log_202608` LIKE `db_openapi`.`openapi_log`;
CREATE TABLE IF NOT EXISTS `db_openapi`.`openapi_log_202609` LIKE `db_openapi`.`openapi_log`;
CREATE TABLE IF NOT EXISTS `db_openapi`.`openapi_log_202610` LIKE `db_openapi`.`openapi_log`;
CREATE TABLE IF NOT EXISTS `db_openapi`.`openapi_log_202611` LIKE `db_openapi`.`openapi_log`;
CREATE TABLE IF NOT EXISTS `db_openapi`.`openapi_log_202612` LIKE `db_openapi`.`openapi_log`;
CREATE TABLE IF NOT EXISTS `db_openapi`.`openapi_log_202701` LIKE `db_openapi`.`openapi_log`;
CREATE TABLE IF NOT EXISTS `db_openapi`.`openapi_log_202702` LIKE `db_openapi`.`openapi_log`;

-- db_auth.sys_login_log（时间列 login_time）
CREATE TABLE IF NOT EXISTS `db_auth`.`sys_login_log_202608` LIKE `db_auth`.`sys_login_log`;
CREATE TABLE IF NOT EXISTS `db_auth`.`sys_login_log_202609` LIKE `db_auth`.`sys_login_log`;
CREATE TABLE IF NOT EXISTS `db_auth`.`sys_login_log_202610` LIKE `db_auth`.`sys_login_log`;
CREATE TABLE IF NOT EXISTS `db_auth`.`sys_login_log_202611` LIKE `db_auth`.`sys_login_log`;
CREATE TABLE IF NOT EXISTS `db_auth`.`sys_login_log_202612` LIKE `db_auth`.`sys_login_log`;
CREATE TABLE IF NOT EXISTS `db_auth`.`sys_login_log_202701` LIKE `db_auth`.`sys_login_log`;
CREATE TABLE IF NOT EXISTS `db_auth`.`sys_login_log_202702` LIKE `db_auth`.`sys_login_log`;

-- db_analysis.query_history（时间列 create_time）
CREATE TABLE IF NOT EXISTS `db_analysis`.`query_history_202608` LIKE `db_analysis`.`query_history`;
CREATE TABLE IF NOT EXISTS `db_analysis`.`query_history_202609` LIKE `db_analysis`.`query_history`;
CREATE TABLE IF NOT EXISTS `db_analysis`.`query_history_202610` LIKE `db_analysis`.`query_history`;
CREATE TABLE IF NOT EXISTS `db_analysis`.`query_history_202611` LIKE `db_analysis`.`query_history`;
CREATE TABLE IF NOT EXISTS `db_analysis`.`query_history_202612` LIKE `db_analysis`.`query_history`;
CREATE TABLE IF NOT EXISTS `db_analysis`.`query_history_202701` LIKE `db_analysis`.`query_history`;
CREATE TABLE IF NOT EXISTS `db_analysis`.`query_history_202702` LIKE `db_analysis`.`query_history`;

-- db_admin.audit_log（时间列 create_time）
CREATE TABLE IF NOT EXISTS `db_admin`.`audit_log_202608` LIKE `db_admin`.`audit_log`;
CREATE TABLE IF NOT EXISTS `db_admin`.`audit_log_202609` LIKE `db_admin`.`audit_log`;
CREATE TABLE IF NOT EXISTS `db_admin`.`audit_log_202610` LIKE `db_admin`.`audit_log`;
CREATE TABLE IF NOT EXISTS `db_admin`.`audit_log_202611` LIKE `db_admin`.`audit_log`;
CREATE TABLE IF NOT EXISTS `db_admin`.`audit_log_202612` LIKE `db_admin`.`audit_log`;
CREATE TABLE IF NOT EXISTS `db_admin`.`audit_log_202701` LIKE `db_admin`.`audit_log`;
CREATE TABLE IF NOT EXISTS `db_admin`.`audit_log_202702` LIKE `db_admin`.`audit_log`;

-- db_monitor.audit_log（时间列 create_time）
CREATE TABLE IF NOT EXISTS `db_monitor`.`audit_log_202608` LIKE `db_monitor`.`audit_log`;
CREATE TABLE IF NOT EXISTS `db_monitor`.`audit_log_202609` LIKE `db_monitor`.`audit_log`;
CREATE TABLE IF NOT EXISTS `db_monitor`.`audit_log_202610` LIKE `db_monitor`.`audit_log`;
CREATE TABLE IF NOT EXISTS `db_monitor`.`audit_log_202611` LIKE `db_monitor`.`audit_log`;
CREATE TABLE IF NOT EXISTS `db_monitor`.`audit_log_202612` LIKE `db_monitor`.`audit_log`;
CREATE TABLE IF NOT EXISTS `db_monitor`.`audit_log_202701` LIKE `db_monitor`.`audit_log`;
CREATE TABLE IF NOT EXISTS `db_monitor`.`audit_log_202702` LIKE `db_monitor`.`audit_log`;

-- 回读自检（两实例各跑一次，期望每张逻辑表 7 张月度表）
-- 用派生表而不是直接 GROUP BY 表达式：MySQL 8 认不出 CONCAT 里的 SUBSTRING_INDEX 与分组列的函数依赖，
-- 直写会撞 only_full_group_by 报 1055（DDL 已经成功，只有这行自检失败，很容易误判成"建表没成"）
SELECT CONCAT('@@MONTH-TABLES ', s, '.', b, ' = ', c) AS check_line
FROM (SELECT table_schema AS s, SUBSTRING_INDEX(table_name, '_20', 1) AS b, COUNT(*) AS c
      FROM information_schema.TABLES
      WHERE table_name REGEXP '_(202608|202609|202610|202611|202612|202701|202702)$'
        AND table_schema IN ('db_alert', 'db_openapi', 'db_auth', 'db_analysis', 'db_admin', 'db_monitor')
      GROUP BY 1, 2) x
ORDER BY 1;
