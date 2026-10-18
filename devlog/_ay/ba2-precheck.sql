SET NAMES utf8mb4;
SELECT COLUMN_NAME, DATA_TYPE, IS_NULLABLE, COLUMN_DEFAULT
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA='db_analysis' AND TABLE_NAME='query_history' ORDER BY ORDINAL_POSITION;
SELECT COUNT(*) AS rows_total FROM db_analysis.query_history;
SELECT COUNT(*) AS bak_exists FROM information_schema.TABLES WHERE TABLE_SCHEMA='db_analysis' AND TABLE_NAME='_ay_query_history_bak';
