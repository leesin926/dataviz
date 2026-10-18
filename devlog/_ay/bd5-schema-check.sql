SET NAMES utf8mb4;
USE db_model;
SELECT CONCAT('@@DS ~|~ ', id, ' ~|~ ', name, ' ~|~ ', IFNULL(source_type,'NULL'), ' ~|~ ', IFNULL(table_name,'NULL')) FROM dataset WHERE id IN (911,912,913,914,915,916,917,918,919) ORDER BY id;
SELECT CONCAT('@@COLS ~|~ ', table_schema, '.', table_name, ' ~|~ ', GROUP_CONCAT(column_name ORDER BY ordinal_position)) FROM information_schema.columns WHERE table_schema='db_analysis' AND table_name IN ('ads_store_performance','ads_hour_heat','ads_member_value','ads_realtime_feed') GROUP BY table_schema, table_name;
USE db_analysis;
SELECT CONCAT('@@RULE ~|~ ', id, ' ~|~ ', dataset_id, ' ~|~ ', IFNULL(rule_name,'NULL'), ' ~|~ ', scope_mode, ' ~|~ ', enabled, ' ~|~ ', IFNULL(restricted_columns,'NULL'), ' ~|~ ', IFNULL(scope_dept_ids,'NULL')) FROM data_perm_rule WHERE dataset_id IN (911,912,913,914,915,916,917,918,919) AND deleted=0 ORDER BY id;
