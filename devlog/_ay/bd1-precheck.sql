SET NAMES utf8mb4;

SELECT '--- 1. schemas ---' AS section;
SELECT SCHEMA_NAME FROM information_schema.SCHEMATA ORDER BY SCHEMA_NAME;

SELECT '--- 2. demo chain row counts ---' AS section;
SELECT 'datasource' AS tbl, COUNT(*) AS rows_cnt FROM db_datasource.datasource
UNION ALL SELECT 'etl_task', COUNT(*) FROM db_etl.etl_task
UNION ALL SELECT 'model_dataset', COUNT(*) FROM db_model.model_dataset
UNION ALL SELECT 'model_dimension', COUNT(*) FROM db_model.model_dimension
UNION ALL SELECT 'model_metric', COUNT(*) FROM db_model.model_metric
UNION ALL SELECT 'analysis_report', COUNT(*) FROM db_analysis.analysis_report
UNION ALL SELECT 'query_history', COUNT(*) FROM db_analysis.query_history
UNION ALL SELECT 'dashboard', COUNT(*) FROM db_dashboard.dashboard
UNION ALL SELECT 'dashboard_widget', COUNT(*) FROM db_dashboard.dashboard_widget
UNION ALL SELECT 'screen', COUNT(*) FROM db_screen.screen
UNION ALL SELECT 'screen_component', COUNT(*) FROM db_screen.screen_component;

SELECT '--- 3. demo_* tables/views by schema ---' AS section;
SELECT TABLE_SCHEMA, TABLE_NAME, TABLE_TYPE, TABLE_ROWS
FROM information_schema.TABLES
WHERE TABLE_NAME LIKE 'demo\_%' ORDER BY TABLE_SCHEMA, TABLE_NAME;

SELECT '--- 4. datasource rows (id,name,type,status,config head) ---' AS section;
SELECT id, name, type, status, deleted, LEFT(config, 120) AS config_head
FROM db_datasource.datasource ORDER BY id;

SELECT '--- 5. screens (id,name,status,adapt_mode,share,canvas) ---' AS section;
SELECT id, name, status, adapt_mode, view_count,
       JSON_UNQUOTE(JSON_EXTRACT(config_json, '$.width'))  AS w,
       JSON_UNQUOTE(JSON_EXTRACT(config_json, '$.height')) AS h,
       JSON_UNQUOTE(JSON_EXTRACT(config_json, '$.adaptationMode')) AS amode,
       JSON_LENGTH(components_json) AS comp_cnt,
       deleted
FROM db_screen.screen ORDER BY id;

SELECT '--- 6. chartConfig presence per screen (autowild NULL => at least one component lacks it) ---' AS section;
SELECT id AS screen_id, name, JSON_LENGTH(components_json) AS comps,
       JSON_LENGTH(components_json, '$[*].chartConfig') AS chartcfg_len,
       JSON_CONTAINS_PATH(components_json, 'all', '$[*].chartConfig') AS all_have_cc
FROM db_screen.screen ORDER BY id;

SELECT '--- 7. screen columns live ---' AS section;
SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT
FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='db_screen' AND TABLE_NAME='screen' ORDER BY ORDINAL_POSITION;

SELECT '--- 8. model_dataset rows ---' AS section;
SELECT id, name, datasource_id, table_name, LEFT(sql_query,60) AS sqlq, deleted FROM db_model.model_dataset ORDER BY id;

SELECT '--- 9. etl_task rows ---' AS section;
SELECT id, name, source_datasource_id AS src, target_datasource_id AS tgt, source_table, target_table, status, deleted
FROM db_etl.etl_task ORDER BY id;

SELECT '--- 10. analysis rows ---' AS section;
SELECT id, name, datasource_id, dataset_id, is_published, deleted FROM db_analysis.analysis_report ORDER BY id;
SELECT id, user_id, datasource_id, dataset_id, status FROM db_analysis.query_history ORDER BY id LIMIT 10;

SELECT '--- 11. dashboards ---' AS section;
SELECT id, name, status, deleted FROM db_dashboard.dashboard ORDER BY id;

SELECT '--- 12. sys_user admin + tenant ---' AS section;
SELECT id, username, real_name, tenant_id, status, deleted FROM db_user.sys_user ORDER BY id LIMIT 12;
