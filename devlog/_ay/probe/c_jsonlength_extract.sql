SET NAMES utf8mb4; USE db_screen;
SELECT CONCAT('@@OK C ', COUNT(*)) FROM screen s WHERE s.id=901 AND JSON_LENGTH(JSON_EXTRACT(s.components_json,'$[*]'))>0;
