SET NAMES utf8mb4;
SELECT CONCAT(TABLE_NAME, '.', COLUMN_NAME, ' ', COLUMN_TYPE, ' ', IS_NULLABLE) c
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA='db_analysis'
  AND TABLE_NAME IN ('ods_order_detail','dwd_sales_daily','dws_store_sales','dws_member_level','ads_store_performance','ads_hour_heat','ads_member_value','ads_realtime_feed')
ORDER BY FIELD(TABLE_NAME,'ods_order_detail','dwd_sales_daily','dws_store_sales','dws_member_level','ads_store_performance','ads_hour_heat','ads_member_value','ads_realtime_feed'), ORDINAL_POSITION;
