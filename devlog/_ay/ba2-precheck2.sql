SET NAMES utf8mb4;
SELECT COLUMN_NAME, COLUMN_TYPE, CHARACTER_MAXIMUM_LENGTH
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA='db_analysis' AND TABLE_NAME='query_history'
  AND COLUMN_NAME IN ('status','dataset_id','query_json','user_id');
