SET NAMES utf8mb4;
USE db_screen;

-- 逐个试探自检里用到的 JSON 构造，能跑通就打印 @@OK，失败会中断（所以一条一个文件段）
SELECT CONCAT('@@A has_cfg-as-boolean: ', COUNT(*)) FROM screen s,
JSON_TABLE(s.components_json, '$[*]' COLUMNS (
  cid VARCHAR(64) PATH '$.id', type VARCHAR(32) PATH '$.type',
  has_cfg BOOLEAN PATH '$.chartConfig'
)) c WHERE s.id = 901;
