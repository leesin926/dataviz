SET NAMES utf8mb4;
SELECT '@@MD' AS tag, id, tenant_id, name, datasource_id, table_name, LEFT(sql_query,60) AS sqlq, LEFT(dimensions,80) AS dims, LEFT(metrics,80) AS mets, deleted FROM db_model.model_dataset ORDER BY id;
SELECT '@@DIM' AS tag, id, dataset_id, name, display_name, column_name, datasource_id, table_name, data_type, dimension_type, sort, deleted FROM db_model.model_dimension ORDER BY id;
SELECT '@@MET' AS tag, id, dataset_id, name, display_name, column_name, expression, aggregation_type, agg_function, data_type, format, deleted FROM db_model.model_metric ORDER BY id;
SELECT '@@RPT' AS tag, id, tenant_id, name, dataset_id, datasource_id, is_published, CHAR_LENGTH(config) AS cfg_len, deleted FROM db_analysis.analysis_report ORDER BY id;
SELECT '@@DS' AS tag, id, name, datasource_id, table_name, type, deleted FROM db_model.dataset ORDER BY id;
SELECT '@@QH' AS tag, id, tenant_id, user_id, datasource_id, LEFT(sql,60) AS sql_, execution_time, duration_ms, row_count, status, create_time FROM db_analysis.query_history ORDER BY id DESC LIMIT 12;
