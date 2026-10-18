SELECT 'sys_license_exists' AS chk, COUNT(*) AS v FROM information_schema.TABLES WHERE TABLE_SCHEMA='db_admin' AND TABLE_NAME='sys_license'
UNION ALL SELECT 'data_perm_rule_exists', COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='db_analysis' AND TABLE_NAME='data_perm_rule'
UNION ALL SELECT 'perm_total_alive', COUNT(*) FROM db_user.sys_permission WHERE deleted=0
UNION ALL SELECT 'role1_grants_total', COUNT(*) FROM db_user.sys_role_permission rp JOIN db_user.sys_permission p ON p.id=rp.permission_id AND p.deleted=0 WHERE rp.role_id=1
UNION ALL SELECT 'dir_monitor_alive', COUNT(*) FROM db_user.sys_permission WHERE permission_code='monitor' AND deleted=0
UNION ALL SELECT 'dir_schedule_alive', COUNT(*) FROM db_user.sys_permission WHERE permission_code='schedule' AND deleted=0
UNION ALL SELECT 'dir_ai_alive', COUNT(*) FROM db_user.sys_permission WHERE permission_code='ai' AND deleted=0
UNION ALL SELECT 'dir_file_alive', COUNT(*) FROM db_user.sys_permission WHERE permission_code='file' AND deleted=0
UNION ALL SELECT 'dir_collab_alive', COUNT(*) FROM db_user.sys_permission WHERE permission_code='collab' AND deleted=0
UNION ALL SELECT 'dir_openapi_alive', COUNT(*) FROM db_user.sys_permission WHERE permission_code='openapi' AND deleted=0;

SELECT permission_code, id, type, parent_id, deleted, HEX(permission_name) AS name_hex FROM db_user.sys_permission
WHERE permission_code IN ('schedule','schedule:read','schedule:write','ai','ai:read','ai:write','file','file:read','file:write',
 'collab','collab:read','collab:write','openapi','openapi:read','openapi:write','monitor','monitor:read','monitor:write')
ORDER BY permission_code;

SELECT INDEX_NAME, GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) AS cols FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA='db_user' AND TABLE_NAME='sys_permission' GROUP BY INDEX_NAME;
SELECT INDEX_NAME, GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) AS cols FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA='db_user' AND TABLE_NAME='sys_role_permission' GROUP BY INDEX_NAME;
