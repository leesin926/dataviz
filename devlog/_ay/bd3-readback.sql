SET NAMES utf8mb4;
SELECT '@@TASK' AS tag, id, transform_config FROM db_etl.etl_task WHERE id BETWEEN 911 AND 918 ORDER BY id;
SELECT '@@COLS' AS tag, CONCAT(t.table_name, '.', c.column_name) AS col, c.is_nullable,
       IFNULL(c.column_default, '~NULL~') AS dflt
  FROM information_schema.COLUMNS c JOIN information_schema.TABLES t
    ON t.table_schema=c.table_schema AND t.table_name=c.table_name
 WHERE c.table_schema='db_analysis'
   AND t.table_name IN ('ods_order_detail','dwd_sales_daily','dws_store_sales','dws_member_level',
                        'ads_store_performance','ads_member_value','ads_hour_heat','ads_realtime_feed')
 ORDER BY t.table_name, c.ordinal_position;
