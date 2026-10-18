SET NAMES utf8mb4; USE db_screen;
SELECT CONCAT('@@OK K ', c.cid, ' ', IFNULL(JSON_LENGTH(c.rj),'-')) FROM screen s, JSON_TABLE(s.components_json, '$[*]' COLUMNS (cid VARCHAR(64) PATH '$.id', rj FORMAT JSON PATH '$.request.staticData.rows')) c WHERE s.id=901;
