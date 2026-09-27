-- ============================================================
-- 回滚：2026-09-23-api30-wildcard-and-role2-unlock.sql 的全部三处改动
-- 日期：2026-09-23   关联：docs/12 §19.9
-- 为什么是 DELETE 而不是软删（deleted=1）：`sys_permission` 的唯一键是 `uk_permission_code(permission_code, deleted)`
--   ⇒ 软删后如果以后再种同一条码，deleted 仍是 1 会撞唯一键；探针行本就不是产品数据，物理删干净最省事。
-- 跑完这一份之后：**必须再重新登录一次**才算把探针权限从会话里清掉（Redis 快照与 localStorage 都不被改库驱逐）。
-- ============================================================

SET NAMES utf8mb4;
USE `db_user`;

-- 1. 撤 role 3 的通配授权（按码反查，不写死 id）
DELETE rp FROM `sys_role_permission` rp
INNER JOIN `sys_permission` p ON p.`id` = rp.`permission_id`
WHERE p.`permission_code` = 'system:*';

-- 2. 删探针权限行
DELETE FROM `sys_permission` WHERE `permission_code` = 'system:*';

-- 3. 撤 designer01 的临时 role 2
DELETE FROM `sys_user_role` WHERE `id` = 949 AND `user_id` = 903 AND `role_id` = 2;

-- ============================================================
-- 校验：跑完应回到执行前状态
-- ============================================================
-- 期望：permission_total = 39
SELECT COUNT(*) AS permission_total FROM `sys_permission` WHERE `deleted` = 0;

-- 期望：0 行（探针码不存在，也没有软删残留）
SELECT COUNT(*) AS probe_left FROM `sys_permission` WHERE `permission_code` = 'system:*';

-- 期望：super_admin 39 / admin 19 / user 10
SELECT r.`role_code`, COUNT(rp.`permission_id`) AS granted
FROM `sys_role` r
LEFT JOIN `sys_role_permission` rp ON rp.`role_id` = r.`id`
WHERE r.`deleted` = 0
GROUP BY r.`role_code`;

-- 期望：designer01 只剩 role 3 一行
SELECT u.`username`, ur.`role_id`
FROM `sys_user` u
INNER JOIN `sys_user_role` ur ON ur.`user_id` = u.`id`
WHERE u.`id` = 903;
