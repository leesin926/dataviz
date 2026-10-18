SET NAMES utf8mb4;
SELECT '== demo_sales_daily columns (both schemas) ==' AS s;
SELECT TABLE_SCHEMA, COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT FROM information_schema.COLUMNS
 WHERE TABLE_NAME='demo_sales_daily' AND TABLE_SCHEMA IN ('db_model','db_analysis') ORDER BY TABLE_SCHEMA, ORDINAL_POSITION;
SELECT '== demo_user_behavior columns (both schemas) ==' AS s;
SELECT TABLE_SCHEMA, COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT FROM information_schema.COLUMNS
 WHERE TABLE_NAME='demo_user_behavior' AND TABLE_SCHEMA IN ('db_model','db_analysis') ORDER BY TABLE_SCHEMA, ORDINAL_POSITION;
SELECT '== demo_stores columns ==' AS s;
SELECT COLUMN_NAME, COLUMN_TYPE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='db_model' AND TABLE_NAME='demo_stores' ORDER BY ORDINAL_POSITION;
SELECT '== counts ==' AS s;
SELECT (SELECT COUNT(*) FROM db_model.demo_user_behavior) AS behavior_model,
       (SELECT COUNT(*) FROM db_analysis.demo_user_behavior) AS behavior_analysis,
       (SELECT COUNT(*) FROM db_model.demo_sales_daily) AS sales_model,
       (SELECT COUNT(*) FROM db_analysis.demo_sales_daily) AS sales_analysis,
       (SELECT COUNT(*) FROM db_model.demo_stores) AS stores;
SELECT '== heartbeat distribution ==' AS s;
SELECT event_type, COUNT(*) c FROM db_model.demo_user_behavior GROUP BY event_type ORDER BY c DESC;
