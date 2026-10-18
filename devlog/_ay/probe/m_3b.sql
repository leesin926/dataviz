SET NAMES utf8mb4; USE db_screen;
SELECT CONCAT('@@BAD orphan ', s.id, ' ~|~ ', c.cid) FROM screen s JOIN JSON_TABLE(s.components_json,'$[*]' COLUMNS (cid VARCHAR(64) PATH '$.id')) c WHERE s.id IN (1,6) AND s.deleted=0 AND NOT EXISTS (SELECT 1 FROM JSON_TABLE(s.config_json,'$.layers[*]' COLUMNS (NESTED PATH '$.componentIds[*]' COLUMNS (cid VARCHAR(64) PATH '$'))) t WHERE t.cid = c.cid) LIMIT 3;
