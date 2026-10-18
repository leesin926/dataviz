SET NAMES utf8mb4; USE db_screen;
SELECT COUNT(*) FROM screen s JOIN JSON_TABLE(s.components_json,'$[*]' COLUMNS (cid VARCHAR(64) PATH '$.id', x INT PATH '$.x', w INT PATH '$.w')) c WHERE s.id IN (1,6) AND (c.x + c.w > JSON_EXTRACT(s.config_json,'$.width'));
