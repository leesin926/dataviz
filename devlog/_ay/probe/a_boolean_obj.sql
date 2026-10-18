SET NAMES utf8mb4; USE db_screen;
SELECT CONCAT('@@OK A ', COUNT(*)) FROM screen s, JSON_TABLE(s.components_json, '$[*]' COLUMNS (cid VARCHAR(64) PATH '$.id', has_cfg BOOLEAN PATH '$.chartConfig')) c WHERE s.id = 901;
