SET NAMES utf8mb4;
SELECT CONCAT('@@C', ' ~|~ ', TABLE_NAME, ' ~|~ ', COLUMN_NAME, ' ~|~ ', COLUMN_TYPE, ' ~|~ ', IS_NULLABLE, ' ~|~ ', IFNULL(COLUMN_DEFAULT,'~N~')) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='db_screen' ORDER BY TABLE_NAME, ORDINAL_POSITION;
SELECT CONCAT('@@S', ' ~|~ ', id, ' ~|~ ', name, ' ~|~ ', status, ' ~|~ ', CHAR_LENGTH(config_json), ' ~|~ ', IFNULL(share_token,'NULL'), ' ~|~ ', tenant_id, ' ~|~ ', deleted) FROM db_screen.screen;
