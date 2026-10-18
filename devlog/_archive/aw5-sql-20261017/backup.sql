SET NAMES utf8mb4;
CREATE TABLE IF NOT EXISTS `db_user`.`zz_bak_20261017_sys_permission` AS SELECT * FROM `db_user`.`sys_permission`;
CREATE TABLE IF NOT EXISTS `db_user`.`zz_bak_20261017_sys_role_permission` AS SELECT * FROM `db_user`.`sys_role_permission`;
SELECT 'bak_sys_permission' AS chk, COUNT(*) AS rows_copied FROM `db_user`.`zz_bak_20261017_sys_permission`
UNION ALL SELECT 'bak_sys_role_permission', COUNT(*) FROM `db_user`.`zz_bak_20261017_sys_role_permission`;
