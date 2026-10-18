SET NAMES utf8mb4; USE db_screen;
SELECT CONCAT('@@OK D2 ', COUNT(*)) FROM screen WHERE id=901 AND JSON_EXTRACT(components_json, CONCAT('$[0', '.', 'props', ']')) IS NOT NULL;
