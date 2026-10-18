SET NAMES utf8mb4; USE db_screen;
SELECT CONCAT('@@OK L ', c.ord, ' ', c.cid, ' cfg=', JSON_CONTAINS_PATH(s.components_json,'one',CONCAT('$[',c.ord-1,'].chartConfig'))) FROM screen s, JSON_TABLE(s.components_json,'$[*]' COLUMNS (ord FOR ORDINALITY, cid VARCHAR(64) PATH '$.id')) c WHERE s.id=901;
