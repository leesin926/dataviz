SET NAMES utf8mb4;
SELECT id, name, status, IFNULL(share_token,'NULL') AS tok,
       CHAR_LENGTH(config_json) AS cfg_len, CHAR_LENGTH(components_json) AS comp_len,
       CHAR_LENGTH(IFNULL(variants_json,'')) AS var_len
FROM db_screen.screen WHERE id IN (901,911) ORDER BY id;
