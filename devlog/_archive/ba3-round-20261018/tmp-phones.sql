SET NAMES utf8mb4;
SELECT COUNT(*) AS with_phone FROM db_user.sys_user WHERE phone IS NOT NULL AND phone <> '';
SELECT id, username, phone, status, deleted FROM db_user.sys_user WHERE phone IS NOT NULL AND phone <> '' ORDER BY id;
