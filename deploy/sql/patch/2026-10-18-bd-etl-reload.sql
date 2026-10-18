-- BD3 配套：把演示 ETL 链清空，好在界面上真跑一遍 911~918 重建分层数据。
--
-- 为什么要单独一份：引擎目前只实现 append（overwrite/upsert 会明确报错），而 8 张分层表都带主键，
-- 目标表非空时重跑是「主键冲突」而不是「覆盖」。所以重跑的前置动作是清空，这一步 destructive，
-- 刻意不放进任何自动补丁里 —— 需要时由人手工执行。
--
-- 跑完之后在 ETL 设计页按顺序点执行：911 → (912/913/914/916) → (915/917/918)。
-- 每跑一条，etl_task_log / etl_task_instance 会多出引擎自己记的真实读数。
SET NAMES utf8mb4;

-- 先看一眼现状（谁非空），再决定要不要清
SELECT 'ods_order_detail' AS tbl, COUNT(*) AS rows_now FROM db_analysis.ods_order_detail
UNION ALL SELECT 'dwd_sales_daily', COUNT(*) FROM db_analysis.dwd_sales_daily
UNION ALL SELECT 'dws_store_sales', COUNT(*) FROM db_analysis.dws_store_sales
UNION ALL SELECT 'dws_member_level', COUNT(*) FROM db_analysis.dws_member_level
UNION ALL SELECT 'ads_store_performance', COUNT(*) FROM db_analysis.ads_store_performance
UNION ALL SELECT 'ads_member_value', COUNT(*) FROM db_analysis.ads_member_value
UNION ALL SELECT 'ads_hour_heat', COUNT(*) FROM db_analysis.ads_hour_heat
UNION ALL SELECT 'ads_realtime_feed', COUNT(*) FROM db_analysis.ads_realtime_feed;

-- 业务库三张源表（demo_orders/demo_stores/demo_members）不在清理范围：
-- 它们是 ETL 的输入，清空等于把演示数据的根拔掉。

TRUNCATE TABLE db_analysis.ads_realtime_feed;
TRUNCATE TABLE db_analysis.ads_member_value;
TRUNCATE TABLE db_analysis.ads_store_performance;
TRUNCATE TABLE db_analysis.ads_hour_heat;
TRUNCATE TABLE db_analysis.dws_member_level;
TRUNCATE TABLE db_analysis.dws_store_sales;
TRUNCATE TABLE db_analysis.dwd_sales_daily;
TRUNCATE TABLE db_analysis.ods_order_detail;

-- 清掉旧的执行历史，免得「这次到底跑没跑」和上一轮的记录混在一起
DELETE FROM db_etl.etl_task_log WHERE task_id BETWEEN 911 AND 918;
DELETE FROM db_etl.etl_task_instance WHERE task_id BETWEEN 911 AND 918;
UPDATE db_etl.etl_task SET last_run_time = NULL, last_run_status = NULL WHERE id BETWEEN 911 AND 918;

SELECT '清理后分层表总行数(必须 0)' AS check_name,
       (SELECT COUNT(*) FROM db_analysis.ods_order_detail) +
       (SELECT COUNT(*) FROM db_analysis.dwd_sales_daily) +
       (SELECT COUNT(*) FROM db_analysis.dws_store_sales) +
       (SELECT COUNT(*) FROM db_analysis.dws_member_level) +
       (SELECT COUNT(*) FROM db_analysis.ads_store_performance) +
       (SELECT COUNT(*) FROM db_analysis.ads_member_value) +
       (SELECT COUNT(*) FROM db_analysis.ads_hour_heat) +
       (SELECT COUNT(*) FROM db_analysis.ads_realtime_feed) AS value;
