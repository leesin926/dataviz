SET NAMES utf8mb4; USE db_screen;
SELECT CONCAT('@@OK E ', COUNT(*)) FROM screen s JOIN JSON_TABLE(s.config_json, '$.layers[*]' COLUMNS (id VARCHAR(64) PATH '$.id', NESTED PATH '$.componentIds[*]' COLUMNS (cid VARCHAR(64) PATH '$'))) l WHERE s.id=1;
