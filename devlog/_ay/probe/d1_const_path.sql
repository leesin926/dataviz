SET NAMES utf8mb4; USE db_screen;
SELECT CONCAT('@@OK D1 ', JSON_TYPE(JSON_EXTRACT(components_json,'$[0].props'))) FROM screen WHERE id=901;
