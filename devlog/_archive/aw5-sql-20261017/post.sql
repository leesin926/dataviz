SET NAMES utf8mb4;
SELECT 'perm_total_alive' AS chk, COUNT(*) AS v FROM db_user.sys_permission WHERE deleted=0
UNION ALL SELECT 'role1_grants_alive', COUNT(*) FROM db_user.sys_role_permission rp JOIN db_user.sys_permission p ON p.id=rp.permission_id AND p.deleted=0 WHERE rp.role_id=1
UNION ALL SELECT 'role2_grants_alive', COUNT(*) FROM db_user.sys_role_permission rp JOIN db_user.sys_permission p ON p.id=rp.permission_id AND p.deleted=0 WHERE rp.role_id=2
UNION ALL SELECT 'role3_grants_alive', COUNT(*) FROM db_user.sys_role_permission rp JOIN db_user.sys_permission p ON p.id=rp.permission_id AND p.deleted=0 WHERE rp.role_id=3
UNION ALL SELECT 'orphan_children(no live parent)', COUNT(*) FROM db_user.sys_permission c LEFT JOIN db_user.sys_permission par ON par.id=c.parent_id AND par.deleted=0 WHERE c.deleted=0 AND c.parent_id<>0 AND par.id IS NULL;

SELECT permission_code, permission_name, HEX(permission_name) AS name_hex,
       (permission_name LIKE '%\?%') AS has_question
FROM db_user.sys_permission WHERE deleted=0 AND id>43 ORDER BY id;

SELECT TABLE_SCHEMA, TABLE_NAME, TABLE_COMMENT, HEX(TABLE_COMMENT) AS comment_hex FROM information_schema.TABLES
WHERE (TABLE_SCHEMA='db_admin' AND TABLE_NAME='sys_license') OR (TABLE_SCHEMA='db_analysis' AND TABLE_NAME='data_perm_rule');

SELECT 'sys_license_cols' AS chk, COUNT(*) AS v FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='db_admin' AND TABLE_NAME='sys_license'
UNION ALL SELECT 'sys_license_rows', COUNT(*) FROM db_admin.sys_license
UNION ALL SELECT 'sys_license_active', COALESCE(SUM(status=1),0) FROM db_admin.sys_license
UNION ALL SELECT 'data_perm_rule_cols', COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='db_analysis' AND TABLE_NAME='data_perm_rule'
UNION ALL SELECT 'data_perm_rule_has_tenant_id', COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='db_analysis' AND TABLE_NAME='data_perm_rule' AND COLUMN_NAME='tenant_id'
UNION ALL SELECT 'data_perm_rule_rows', COUNT(*) FROM db_analysis.data_perm_rule;

SELECT COLUMN_NAME, COLUMN_COMMENT, HEX(COLUMN_COMMENT) AS hex_ok FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA='db_admin' AND TABLE_NAME='sys_license' AND COLUMN_NAME IN ('key_fingerprint','edition','status') ORDER BY ORDINAL_POSITION;
