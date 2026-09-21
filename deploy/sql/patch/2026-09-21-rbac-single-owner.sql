-- ============================================================
-- RBAC 职责归位（B 方案）· 第 1 步：口令补齐 + 重复表改名留观
-- 日期：2026-09-21   关联决策：D37（RBAC 单一归属）/ D39（B 方案落地）
-- 背景：db_auth 与 db_user 各有一套 6 张 RBAC 表且已分叉。认证改为经
--       user-service 内部接口读 db_user，db_auth 不再持有用户/角色/权限表。
-- 取证：两边 sys_role(3)/sys_permission(17)/sys_role_permission(17)/sys_dept(4)
--       内容逐行等价，admin 的 bcrypt 串完全相同 ⇒ 切换不改变现有登录与权限码。
-- 说明：本脚本只改名不删除，保留一条随时可回退的路径；确认登录链路通过后再执行
--       2026-09-21-rbac-drop-dup-bak.sql 真正删除。
-- 执行：docker exec -i dataviz-mysql mysql -uroot -p<root口令> \
--         --default-character-set=utf8mb4 < deploy/sql/patch/2026-09-21-rbac-single-owner.sql
--       （容器会话默认 latin1，第一句必须是 SET NAMES utf8mb4，见 D31）
-- ============================================================

SET NAMES utf8mb4;

-- 1. db_user 的 5 个演示账号当初灌的是空口令，切换后它们就是唯一用户表，
--    空口令账号既登不进、也验证不了"禁用生效"。统一补成与 admin 相同的哈希（口令 admin123）。
--    只补空哈希，绝不覆盖已有口令。
UPDATE db_user.sys_user
SET password = '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2',
    update_time = NOW()
WHERE (password IS NULL OR password = '')
  AND deleted = 0;

-- 校验 1：应返回 6 行，pwlen 全部 60；ops01 的 status=0 用来验证"封号真的生效"
SELECT id, username, CHAR_LENGTH(password) AS pwlen, status, tenant_id, dept_id
FROM db_user.sys_user WHERE deleted = 0 ORDER BY id;

-- 校验 2：admin 经 db_user 应拿到 17 个权限码（与切换前 db_auth 口径一致）
SELECT COUNT(*) AS admin_perm_codes
FROM db_user.sys_permission p
JOIN db_user.sys_role_permission rp ON rp.permission_id = p.id
JOIN db_user.sys_user_role ur ON ur.role_id = rp.role_id
WHERE ur.user_id = 1 AND p.status = 1 AND p.deleted = 0;

-- 2. db_auth 的 6 张重复表改名留观（auth-service 已无任何代码引用它们）。
--    刻意留在 db_auth 里：不污染 user-service 的库，且改名是原地操作，秒级可逆。
RENAME TABLE
  db_auth.sys_user            TO db_auth.zz_bak_20260921_sys_user,
  db_auth.sys_role            TO db_auth.zz_bak_20260921_sys_role,
  db_auth.sys_permission      TO db_auth.zz_bak_20260921_sys_permission,
  db_auth.sys_role_permission TO db_auth.zz_bak_20260921_sys_role_permission,
  db_auth.sys_user_role       TO db_auth.zz_bak_20260921_sys_user_role,
  db_auth.sys_dept            TO db_auth.zz_bak_20260921_sys_dept;

-- 校验 3：db_auth 只剩认证自己的表 + 6 张 zz_bak_ 留观表
SELECT table_name, table_rows AS estimated_rows
FROM information_schema.tables
WHERE table_schema = 'db_auth' ORDER BY table_name;
