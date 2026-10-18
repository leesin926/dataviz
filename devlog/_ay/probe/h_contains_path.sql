SET NAMES utf8mb4; USE db_screen;
SELECT CONCAT('@@OK H ', COUNT(*)) FROM screen s, JSON_TABLE(s.components_json, '$[*]' COLUMNS (ord FOR ORDINALITY, cid VARCHAR(64) PATH '$.id', typ VARCHAR(32) PATH '$.type')) c WHERE s.id=901 AND JSON_CONTAINS_PATH(s.components_json, 'one', CONCAT('$[', c.ord, '].chartConfig')) = 0;
