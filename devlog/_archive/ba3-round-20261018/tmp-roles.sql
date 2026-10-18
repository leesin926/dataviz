SET NAMES utf8mb4;
SELECT u.id, u.username, r.id AS role_id, r.role_code, r.role_name
  FROM db_user.sys_user u
  LEFT JOIN db_user.sys_user_role ur ON ur.user_id = u.id
  LEFT JOIN db_user.sys_role r ON r.id = ur.role_id
 WHERE u.id IN (1,901,902,903,905) ORDER BY u.id;
