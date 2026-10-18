SET NAMES utf8mb4;
SELECT CONCAT('@@C ~|~ ', TABLE_NAME, ' ~|~ ', COLUMN_NAME, ' ~|~ ', DATA_TYPE)
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA='db_analysis' AND TABLE_NAME IN ('dwd_sales_daily','dws_store_sales','ads_store_performance','ads_hour_heat','ads_member_value','ads_realtime_feed','dws_member_level')
ORDER BY TABLE_NAME, ORDINAL_POSITION;
SELECT CONCAT('@@V ~|~ ', VERSION());
