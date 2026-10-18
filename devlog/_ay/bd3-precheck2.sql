SET NAMES utf8mb4;
SELECT '== A. etl_task rows ==' AS s;
SELECT id, tenant_id, name, status, source_datasource_id, target_datasource_id,
       source_table, target_table, cron_expression, schedule_cron, last_run_status, deleted,
       CHAR_LENGTH(dag_json) AS dag_len, CHAR_LENGTH(transform_config) AS tc_len
  FROM db_etl.etl_task ORDER BY id;
SELECT '== B. transform_config full ==' AS s;
SELECT id, name, transform_config FROM db_etl.etl_task ORDER BY id;
SELECT '== C. dag_json full ==' AS s;
SELECT id, name, dag_json FROM db_etl.etl_task ORDER BY id;
SELECT '== D. etl_task_instance cols ==' AS s;
SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='db_etl' AND TABLE_NAME='etl_task_instance' ORDER BY ORDINAL_POSITION;
SELECT '== E. etl_task_log cols ==' AS s;
SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='db_etl' AND TABLE_NAME='etl_task_log' ORDER BY ORDINAL_POSITION;
SELECT '== F. instances/logs ==' AS s;
SELECT * FROM db_etl.etl_task_instance ORDER BY id;
SELECT '== G. logs ==' AS s;
SELECT id, task_id, level, LEFT(message,80) AS msg, create_time FROM db_etl.etl_task_log ORDER BY id;
