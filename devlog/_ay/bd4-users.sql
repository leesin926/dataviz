SET NAMES utf8mb4;
SELECT CONCAT(u.id,'|',u.username,'|',IFNULL(GROUP_CONCAT(r.role_code),'(none)')) r
FROM db_user.sys_user u
LEFT JOIN db_user.sys_user_role ur ON ur.user_id=u.id
LEFT JOIN db_user.sys_role r ON r.id=ur.role_id
WHERE u.deleted=0
GROUP BY u.id, u.username ORDER BY u.id LIMIT 40;
