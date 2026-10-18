SET NAMES utf8mb4;
SELECT CONCAT('@@CFG ', id, ' :: ', config_json) FROM db_screen.screen WHERE id IN (1,6,901,902,903);
SELECT CONCAT('@@VAR ', id, ' :: ', IFNULL(variants_json,'NULL')) FROM db_screen.screen WHERE id IN (1,6,901,902,903);
SELECT CONCAT('@@CMP ', id, ' :: ', components_json) FROM db_screen.screen WHERE id IN (1,6,901,902,903);
