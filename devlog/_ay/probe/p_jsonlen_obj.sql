SET NAMES utf8mb4; USE db_screen;
SELECT CONCAT('@@P ', c.cid, ' ', IFNULL(JSON_LENGTH(c.cfg),-1)) FROM screen s, JSON_TABLE(s.components_json,'$[*]' COLUMNS (cid VARCHAR(64) PATH '$.id', cfg JSON PATH '$.chartConfig')) c WHERE s.id=901;
