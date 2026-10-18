SET NAMES utf8mb4;
SELECT CONCAT('@@ROLE ', id, ' ', role_code, ' ', role_name) r FROM db_user.sys_role ORDER BY id;
