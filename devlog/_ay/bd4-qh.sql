SET NAMES utf8mb4;
SELECT id, tenant_id, user_id, datasource_id, LEFT(`sql`,70) AS sql_head, execution_time, duration_ms, row_count, status, create_time, deleted
  FROM db_analysis.query_history ORDER BY id DESC LIMIT 12;
SELECT '列形状' AS s, CHAR_LENGTH(`sql`) IS NULL AS sql_null, COUNT(*) c FROM db_analysis.query_history GROUP BY 1;
