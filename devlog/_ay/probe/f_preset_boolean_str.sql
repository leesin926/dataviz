SET NAMES utf8mb4; USE db_screen;
SELECT CONCAT('@@OK F ', COUNT(*)) FROM screen s, JSON_TABLE(s.components_json, '$[*]' COLUMNS (cid VARCHAR(64) PATH '$.id', has_preset BOOLEAN PATH '$.chartPreset')) c WHERE c.has_preset = 1;
