SET NAMES utf8mb4;
SELECT '== A. db_analysis tables ==' AS s;
SELECT TABLE_NAME, TABLE_ROWS FROM information_schema.TABLES WHERE TABLE_SCHEMA='db_analysis' ORDER BY TABLE_NAME;
SELECT '== B. layer columns ==' AS s;
SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE, COLUMN_KEY FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA='db_analysis' AND TABLE_NAME IN ('ods_order_detail','dwd_sales_daily','dws_store_sales','dws_member_level','ads_store_performance','ads_member_value','ads_hour_heat','ads_realtime_feed')
 ORDER BY TABLE_NAME, ORDINAL_POSITION;
SELECT '== C. db_model tables ==' AS s;
SELECT TABLE_NAME, TABLE_ROWS FROM information_schema.TABLES WHERE TABLE_SCHEMA='db_model' ORDER BY TABLE_NAME;
SELECT '== D. demo_orders columns ==' AS s;
SELECT COLUMN_NAME, COLUMN_TYPE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='db_model' AND TABLE_NAME='demo_orders' ORDER BY ORDINAL_POSITION;
