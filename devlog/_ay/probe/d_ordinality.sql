SET NAMES utf8mb4; USE db_screen;
SELECT CONCAT('@@OK D ', COUNT(*)) FROM screen s, JSON_TABLE(s.components_json, '$[*]' COLUMNS (ord FOR ORDINALITY, cid VARCHAR(64) PATH '$.id')) c WHERE s.id=901 AND JSON_EXTRACT(s.components_json, CONCAT('$[', c.ord-1, '.props')) IS NOT NULL;
