SELECT 'bak_perm_alive' AS chk, COUNT(*) AS v FROM db_user.zz_bak_20261017_sys_permission WHERE deleted=0
UNION ALL SELECT 'live_perm_alive', COUNT(*) FROM db_user.sys_permission WHERE deleted=0
UNION ALL SELECT 'bak_role1_alive', COUNT(*) FROM db_user.zz_bak_20261017_sys_role_permission rp JOIN db_user.zz_bak_20261017_sys_permission p ON p.id=rp.permission_id AND p.deleted=0 WHERE rp.role_id=1
UNION ALL SELECT 'bak_role2_alive', COUNT(*) FROM db_user.zz_bak_20261017_sys_role_permission rp JOIN db_user.zz_bak_20261017_sys_permission p ON p.id=rp.permission_id AND p.deleted=0 WHERE rp.role_id=2
UNION ALL SELECT 'live_role2_alive', COUNT(*) FROM db_user.sys_role_permission rp JOIN db_user.sys_permission p ON p.id=rp.permission_id AND p.deleted=0 WHERE rp.role_id=3
UNION ALL SELECT 'bak_role3_alive', COUNT(*) FROM db_user.zz_bak_20261017_sys_role_permission rp JOIN db_user.zz_bak_20261017_sys_permission p ON p.id=rp.permission_id AND p.deleted=0 WHERE rp.role_id=3;
SELECT COUNT(*) AS rows_changed_in_old_43 FROM db_user.sys_permission p
JOIN db_user.zz_bak_20261017_sys_permission b ON b.id=p.id
WHERE p.id<=43 AND (p.permission_code<>b.permission_code OR p.permission_name<>b.permission_name OR p.parent_id<>b.parent_id OR p.deleted<>b.deleted);
