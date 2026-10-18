SET NAMES utf8mb4; USE db_screen;
SELECT CONCAT('@@OK B ', COUNT(*)) FROM screen s, JSON_TABLE(s.components_json, '$[*]' COLUMNS (cid VARCHAR(64) PATH '$.id', nrows INT PATH '$.request.staticData.rows.length()')) c WHERE s.id = 901;
