-- ============================================================
-- RBAC 职责归位（B 方案）· 第 2 步：真正删除 db_auth 的重复表留观副本
-- 日期：2026-09-21   前置：2026-09-21-rbac-single-owner.sql 已执行
-- 执行时机：等 pc-web 登录 + 鉴权态矩阵全部通过、确认没有任何代码还在读这些表之后再跑。
--           本脚本不可逆（工作区非 git，且此前 mysqldump 备份被工具策略拦下）。
-- 回退窗口：在此之前，2026-09-21-rbac-rollback.sql 一条语句即可把表名改回去。
-- 执行：docker exec -i dataviz-mysql mysql -uroot -p<root口令> \
--         --default-character-set=utf8mb4 < deploy/sql/patch/2026-09-21-rbac-drop-dup-bak.sql
-- ============================================================

SET NAMES utf8mb4;

-- 删除前先精确计数（information_schema.table_rows 是估算值，别拿它当依据，见 D35）
SELECT 'before_drop' AS phase,
       (SELECT COUNT(*) FROM db_auth.zz_bak_20260921_sys_user)            u,
       (SELECT COUNT(*) FROM db_auth.zz_bak_20260921_sys_role)            r,
       (SELECT COUNT(*) FROM db_auth.zz_bak_20260921_sys_permission)      p,
       (SELECT COUNT(*) FROM db_auth.zz_bak_20260921_sys_role_permission) rp,
       (SELECT COUNT(*) FROM db_auth.zz_bak_20260921_sys_user_role)       ur,
       (SELECT COUNT(*) FROM db_auth.zz_bak_20260921_sys_dept)            d;

DROP TABLE IF EXISTS
  db_auth.zz_bak_20260921_sys_user,
  db_auth.zz_bak_20260921_sys_role,
  db_auth.zz_bak_20260921_sys_permission,
  db_auth.zz_bak_20260921_sys_role_permission,
  db_auth.zz_bak_20260921_sys_user_role,
  db_auth.zz_bak_20260921_sys_dept;

-- 校验：db_auth 应只剩 sys_login_log 与 sys_oauth_client 两张认证自有表
SELECT table_name
FROM information_schema.tables
WHERE table_schema = 'db_auth' ORDER BY table_name;
