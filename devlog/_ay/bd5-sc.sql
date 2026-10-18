SET NAMES utf8mb4;
SELECT CONCAT('@@SC ~|~ ', id, ' ~|~ ', screen_id, ' ~|~ ', component_type) FROM db_screen.screen_component ORDER BY screen_id, id LIMIT 60;
SELECT CONCAT('@@CNT ~|~ ', COUNT(*)) FROM db_screen.screen_component;
