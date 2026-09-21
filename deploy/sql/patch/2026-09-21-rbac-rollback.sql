-- ============================================================
-- RBAC 职责归位（B 方案）· 回退：把 db_auth 的 6 张重复表名改回来
-- 仅在 2026-09-21-rbac-drop-dup-bak.sql **尚未执行**时有效（改名是原地操作，数据仍在）。
-- 执行：docker exec -i dataviz-mysql mysql -uroot -p<root口令> \
--         --default-character-set=utf8mb4 < deploy/sql/patch/2026-09-21-rbac-rollback.sql
-- 注意：光改表名不足以回退登录链路，还要把 auth-service / user-service / gateway
--       一起重启回改动前的构建（IDEA 里对这三个模块 Rebuild 后再启）。
-- ============================================================

SET NAMES utf8mb4;

RENAME TABLE
  db_auth.zz_bak_20260921_sys_user            TO db_auth.sys_user,
  db_auth.zz_bak_20260921_sys_role            TO db_auth.sys_role,
  db_auth.zz_bak_20260921_sys_permission      TO db_auth.sys_permission,
  db_auth.zz_bak_20260921_sys_role_permission TO db_auth.sys_role_permission,
  db_auth.zz_bak_20260921_sys_user_role       TO db_auth.sys_user_role,
  db_auth.zz_bak_20260921_sys_dept            TO db_auth.sys_dept;

SELECT table_name
FROM information_schema.tables
WHERE table_schema = 'db_auth' ORDER BY table_name;
