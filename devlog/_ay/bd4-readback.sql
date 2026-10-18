SET NAMES utf8mb4;
USE db_model;
SELECT CONCAT('@@MD ~|~ ', id, ' ~|~ ', datasource_id, ' ~|~ ', COALESCE(table_name, 'NULL'), ' ~|~ ', COALESCE(sql_query, 'NULL'), ' ~|~ ', COALESCE(dimensions, 'NULL'), ' ~|~ ', COALESCE(metrics, 'NULL'), ' ~|~ ', COALESCE(filters, 'NULL')) FROM model_dataset WHERE id BETWEEN 911 AND 921 ORDER BY id;
SELECT CONCAT('@@DIM ~|~ ', id, ' ~|~ ', dataset_id, ' ~|~ ', column_name, ' ~|~ ', dimension_type, ' ~|~ ', COALESCE(table_name, 'NULL'), ' ~|~ ', data_type, ' ~|~ ', sort) FROM model_dimension WHERE id BETWEEN 911 AND 999 ORDER BY id;
SELECT CONCAT('@@MET ~|~ ', id, ' ~|~ ', dataset_id, ' ~|~ ', column_name, ' ~|~ ', aggregation_type, ' ~|~ ', COALESCE(table_name, 'NULL'), ' ~|~ ', data_type, ' ~|~ ', sort) FROM model_metric WHERE id BETWEEN 911 AND 999 ORDER BY id;
USE db_analysis;
SELECT CONCAT('@@RPT ~|~ ', id, ' ~|~ ', dataset_id, ' ~|~ ', is_published, ' ~|~ ', COALESCE(config, 'NULL')) FROM analysis_report WHERE id BETWEEN 911 AND 917 ORDER BY id;
SELECT CONCAT('@@RULE ~|~ ', id, ' ~|~ ', dataset_id, ' ~|~ ', scope_type, ' ~|~ ', scope_value, ' ~|~ ', COALESCE(conditions_json, 'NULL'), ' ~|~ ', COALESCE(restricted_columns, 'NULL'), ' ~|~ ', enabled) FROM data_perm_rule WHERE id BETWEEN 911 AND 912 ORDER BY id;
SELECT CONCAT('@@DS ~|~ ', id, ' ~|~ ', name, ' ~|~ ', status) FROM db_datasource.datasource WHERE id IN (901, 902, 903, 904) ORDER BY id;
